(ns lwhorton.boot-stylus.core
  {:boot/export-tasks true}
  (:require
    [boot.core :as c :refer [deftask]]
    [boot.task.built-in :refer [sift]]
    [clojure.java.io :as io]
    [clojure.java.shell :as sh]
    [boot.util :as u]
    [me.raynes.conch :as conch]
    [me.raynes.conch.low-level :as co]
    [lwhorton.boot-stylus.generator :as generator]
    [clojure.java.io :refer [reader writer]]
    )
  )

(def verbose false)
(defonce starttime (atom (System/currentTimeMillis)))

(defn- get-namespace
  "Convert an out-path filepath like factors_context/factor_analyze/style.css
  into \"factors-context.factor-analyze.style\" for use as a file namespace."
  [out-path]
  (let [no-extension (subs out-path 0 (.lastIndexOf out-path "."))
        hyphenated (.replaceAll no-extension "_" "-")]
    (.replaceAll hyphenated "/" ".")))

(defn- uuid []
  (subs (str (java.util.UUID/randomUUID)) 0 5))

(defn- get-hash [cache in-path id]
  (let [out-path (str in-path "-" id ".stylushash")
        src (io/file cache out-path)
        hash (if (.exists src) (slurp src) (uuid))]
    (doto src io/make-parents (spit hash))
    hash))

(deftask compile-stylus
  "Compile all .styl files in the fileset into .css files"
  []
  (let [installed? (atom false)
        tmp (c/tmp-dir!)
        prev (atom nil)]
    (c/with-pre-wrap [fileset]
      (let [diff (c/fileset-diff @prev fileset :hash)
            in-files (c/input-files diff)
            styl-files (c/by-ext [".styl"] in-files)]
        (reset! prev fileset)
        (when-not @installed?
          (u/info "Installing necessary node deps...\n")
          (let [pkg (io/resource "lwhorton/boot_stylus/package.json")
                dest (io/file tmp "package.json")]
            (with-open [in (io/input-stream pkg)]
              (io/copy in dest)))
          (let [compiler (io/resource "lwhorton/boot_stylus/run_postcss.js")
                dest (io/file tmp "run_postcss.js")]
            (with-open [in (io/input-stream compiler)]
              (io/copy in dest)))
          (binding [u/*sh-dir* (.getPath tmp)]
            (u/dosh "npm" "install" "--depth" "-1"))
          (reset! installed? true)
          (u/info "Installed node deps.\n"))

        ;; compile stylus into css
        (when (seq styl-files)
          (do
            (u/info "Compiling stylus...\n")
            (reset! starttime (System/currentTimeMillis))
            (when verbose (u/info "Compiling %d .styl files.\n" (count styl-files)))
            (doseq [in styl-files]
              (let [in-file (c/tmp-file in)
                    in-path (c/tmp-path in)
                    out-path (.replaceAll in-path "\\.styl$" ".css")
                    out-file (io/file tmp out-path)]
                ;; in order for stylus to not throw, the file needs to exist
                (doto out-file io/make-parents c/touch)
                (when verbose (println (str "Compiling " in-path " to " out-path "...")))
                (binding [u/*sh-dir* (.getPath tmp)]
                  (u/dosh "./node_modules/.bin/stylus" "-o" (.getPath out-file) (.getPath in-file)))))))

        (if (seq styl-files)
          (-> fileset
              (c/add-resource tmp)
              c/commit!)
          fileset)))))

(deftask compile-css-modules
  "Compile all .css files into css-modules with a json manifest.
  Cache classname hashes (e.g. (def foo-class \"_foo-class_{hash}\")) in
  between runs so reloading files persists the proper string, and updates
  to the browser are applied to the correct class."
  []
  (let [tmp (c/tmp-dir!)
        hash-cache (c/cache-dir! ::css-modules)
        prev (atom nil)
        installed? (atom false)]
    (c/with-pre-wrap [fileset]
      (let [diff (c/fileset-diff @prev fileset :hash)
            in-files (c/input-files diff)
            css (c/by-ext [".css"] in-files)]
        (reset! prev fileset)

        (when-not @installed?
          (u/info "Installing necessary node deps...\n")
          (let [pkg (io/resource "lwhorton/boot_stylus/package.json")
                dest (io/file tmp "package.json")]
            (with-open [in (io/input-stream pkg)]
              (io/copy in dest)))
          (let [compiler (io/resource "lwhorton/boot_stylus/run_postcss.js")
                dest (io/file tmp "run_postcss.js")]
            (with-open [in (io/input-stream compiler)]
              (io/copy in dest)))
          (binding [u/*sh-dir* (.getPath tmp)]
            (u/dosh "npm" "install" "--depth" "-1"))
          (reset! installed? true)
          (u/info "Installed node deps.\n"))

        (when (seq css)
          (do
            (u/info "Converting %d css-modules...\n" (count css))
            (doseq [in css]
              (let [in-file (c/tmp-file in)
                    in-path (c/tmp-path in)
                    out-path (str (.replaceAll in-path "\\.css$" ".cljs"))
                    out-file (io/file tmp out-path)
                    namespace (get-namespace out-path)
                    hash (get-hash hash-cache in-path namespace)]
                (when verbose
                  (do (u/info (str "Generated namespace for \"" in-path "\" is \"" namespace "\"\n"))
                      (u/info (str "Converting " in-path " to " out-path "...\n"))))
                (conch/with-programs [node]
                  (let [stdout (node "./run_postcss.js"
                                     (.getPath in-file)
                                     (.getPath out-file)
                                     hash
                                     {:dir (.getPath tmp)})]
                    (when (or (empty? stdout) (nil? stdout))
                      (u/fail "\"" in-path "\" did not return any css..."))
                    ;; we can have two types of "errors" here -- an error emitted by the postcss parsing
                    ;; and an error that results from a null file pointer, or empty file input, etc.
                    (if-let [module (generator/create-module namespace stdout)]
                      (if (:err module)
                        (u/fail (:err module))
                        (doto out-file io/make-parents (spit module)))
                      (u/fail "Something went wrong compiling: " in-path))))))))

        (if (seq css)
         (do
           (u/info (str "Finished compiling stylus, elapsed time: " (- (System/currentTimeMillis) @starttime) "ms\n"))
           (-> fileset
            (c/add-resource hash-cache)
            (c/add-resource tmp)
            c/commit!))
          fileset)))))

(deftask remove-leftovers
  "Remove all files generated in the stylus task because all we really care
  about as output is the cljs modules, and everything else was intermediary.
  Also this will slow things down if we dont remove big dirs such as node_modules."
  []
  (c/with-pre-wrap [fs]
    (let [in-files (c/input-files fs)
          out-files (c/output-files fs)
          css-files (c/by-ext [".css"] in-files)
          hash-files (c/by-ext [".stylushash"] in-files)
          other-files (concat (c/by-path ["package.json" "run_postcss.js"] in-files)
                              (c/by-re [#"^node_modules*"] out-files))]
      (-> fs
          (c/rm css-files)
          ;(c/rm hash-files)
          (c/rm other-files)
          c/commit!))))

(deftask stylus
  "Compile all .styl files into clojure modules following css-modules syntax."
  []
  (comp
    (compile-stylus)
    (compile-css-modules)
    (remove-leftovers)
    ))
