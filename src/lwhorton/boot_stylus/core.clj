(ns lwhorton.boot-stylus.core
  {:boot/export-tasks true}
  (:require
    [boot.core :as c :refer [deftask]]
    [boot.task.built-in :refer [sift]]
    [clojure.java.io :as io]
    [clojure.java.shell :as sh]
    [boot.util :as u]
    [me.raynes.conch :as conch]
    [degree9.boot-npm :as npm]
    [degree9.boot-exec :as exec]
    [lwhorton.boot-stylus.generator :as generator]
    [clojure.java.io :refer [reader writer]]
    )
  )

(def verbose false)
(def starttime (atom nil))

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
  (let [tmp (c/tmp-dir!)
        prev (atom nil)]
    (c/with-pre-wrap [fileset]
      (let [diff (c/fileset-diff @prev fileset :hash)
            ;; find the stylus executable in our fileset, then run our stylus
            ;; files through the executable from the parent dir so that paths
            ;; to ./node_modules and any require('../relative-path') works
            stylus-mods (c/by-re [#"^node_modules/stylus"] (c/input-files fileset))
            stylus-exec (first (c/by-name ["stylus"] stylus-mods))
            in-files (c/input-files diff)
            styl-files (c/by-ext [".styl"] (c/by-re [#"^node_modules"] in-files true))]
        (reset! prev fileset)

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
                (binding [u/*sh-dir* (.getPath (c/tmp-dir stylus-exec))]
                  (apply u/dosh (concat
                                  ["./node_modules/stylus/bin/stylus"]
                                  ["-o"
                                   (.getPath out-file)
                                   (.getPath in-file)])))))))

        (-> fileset
            (c/add-resource tmp)
            c/commit!)))))

(deftask compile-css-modules
  "Compile all .css files into css-modules with a json manifest.
  Cache classname hashes (e.g. (def foo-class \"_foo-class_{hash}\")) in
  between runs so reloading files persists the proper string, and updates
  to the browser are applied to the correct class."
  []
  (let [tmp (c/tmp-dir!)
        hash-cache (c/cache-dir! ::css-modules)
        prev (atom nil)]
    (c/with-pre-wrap [fileset]
      (let [diff (c/fileset-diff @prev fileset :hash)
            post-css (first (c/by-name ["run_postcss.js"] (c/input-files fileset)))
            node-modules (first (c/by-re [#"^node_modules"] (c/input-files fileset)))
            in-files (c/input-files diff)
            ;; compile all css, but exclude node_modules and cljsjs paths
            css (c/by-ext [".css"] (c/by-re [#"cljsjs" #"^node_modules"] in-files true))]
        (println "compiling" (apply str (map #(str (.getPath (c/tmp-file %)) "\t") css)))
        (reset! prev fileset)

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

                ;; we cannot ./node run_postcss.js args without the script being at a
                ;; sibling level to our installed node_modules
                (spit (io/file (str (.getPath (c/tmp-dir node-modules)) "/run_postcss.js"))
                      (slurp (c/tmp-file post-css)))
                (conch/with-programs [node]
                  (let [stdout (node "run_postcss.js"
                                     (.getPath in-file)
                                     (.getPath out-file)
                                     hash
                                     {:dir (.getPath (c/tmp-dir node-modules))})]
                    (when (or (empty? stdout) (nil? stdout))
                      (u/fail "\"" in-path "\" did not return any css..."))
                    ;; we can have two types of "errors" here -- an error emitted by the postcss parsing
                    ;; and an error that results from a null file pointer, or empty file input, etc.
                    (if-let [module (generator/create-module namespace stdout)]
                      (if (:err module)
                        (u/fail (:err module))
                        (doto out-file io/make-parents (spit module)))
                      (u/fail "Something went wrong compiling: " in-path))))))))

        (when (seq css)
          (u/info (str "Finished compiling stylus, elapsed time: " (- (System/currentTimeMillis) @starttime) "ms\n")))

        (-> fileset
            (c/add-resource hash-cache)
            (c/add-resource tmp)
            c/commit!)
        ))))

(deftask remove-leftovers
  "Remove all files generated in the stylus task because all we really care
  about as output is the cljs modules, and everything else was intermediary.
  Also this will slow things down if we dont remove big dirs such as node_modules."
  []
  (c/with-pre-wrap [fs]
    (let [in-files (c/input-files fs)
          out-files (c/output-files fs)
          css-files (c/by-ext [".css"] in-files)
          hash-files (c/by-ext [".stylushash"] out-files)
          node-modules (c/by-re [#"^node_modules/.*"] (c/input-files fs))
          other-files (c/by-path ["run_postcss.js"
                                  "package-lock.json"
                                  "package.json"] in-files)]
      (-> fs
          (c/rm css-files)
          (c/rm hash-files)
          (c/rm node-modules)
          (c/rm other-files)
          c/commit!))))

(deftask install-deps
  []
  (comp
    (let [tmp (c/tmp-dir!)]
      (c/with-pre-wrap [fs]
        (spit (str (.getPath tmp) "/run_postcss.js")
              (slurp (io/resource "lwhorton/boot_stylus/run_postcss.js")))
        (-> fs
            (c/add-source tmp)
            (c/commit!))))
    (npm/npm :install {:postcss "5.0.21"
                       :postcss-modules "0.5.0"
                       :stylus "0.54.5"}
             :cache-key ::cache)))

(deftask stylus
  "Compile all .styl files into clojure modules following css-modules syntax."
  []
  (comp
    (install-deps)
    (compile-stylus)
    (compile-css-modules)
    (remove-leftovers)
    ))
