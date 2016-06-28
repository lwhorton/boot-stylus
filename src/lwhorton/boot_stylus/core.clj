(ns lwhorton.boot-stylus.core
  {:boot/export-tasks true}
  (:require
    [boot.core :as c :refer [deftask]]
    [clojure.java.io :as io]
    [clojure.java.shell :as sh]
    [boot.util :as u]
    [me.raynes.conch :as conch]
    [me.raynes.conch.low-level :as co]
    [lwhorton.boot-stylus.generator :as generator]
    )
  )

(defn- write-style!
  "Use the Stylus api to compile in-file (styl) to out-file (css)."
  [in-file out-file]
  (conch/with-programs [stylus]
    ;; for stylus to not choke we need the file to exist
    (doto out-file io/make-parents c/touch)
    (apply stylus ["-o" (.getPath out-file) in-file])))

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
  (let [out-path (str in-path "-" id ".key")
        src (io/file cache out-path)
        hash (if (.exists src) (slurp src) (uuid))]
    (doto src io/make-parents (spit hash))
    hash)
  )

(defn- generate-node-path []
  ;; @TODO allow options to add more paths to NODE_PATH
  (str (System/getenv "NODE_PATH")))

(deftask compile-css-modules
  "Compile all .css files into css-modules with a json manifest.
  Cache classname hashes (e.g. (def foo-class \"_foo-class_{hash}\")) in
  between runs so reloading files persists the proper string, and updates
  to the browser are applied to the correct class."
  []
  (let [tmp (c/tmp-dir!)
        hash-cache (c/cache-dir! ::css-modules)
        prev (atom nil)]
    (c/with-pre-wrap fileset
      (let [diff (c/fileset-diff @prev fileset)
            in-files (c/input-files diff)
            css (c/by-ext [".css"] in-files)]
        (reset! prev fileset)
        (when (seq css)
          (u/info "Converting %d css-modules.\n" (count css))
          (doseq [in css]
            (let [in-file (c/tmp-file in)
                  in-path (c/tmp-path in)
                  out-path (str (.replaceAll in-path "\\.css$" ".cljs") )
                  out-file (io/file tmp out-path)
                  namespace (get-namespace out-path)
                  hash (get-hash hash-cache in-path namespace)]
              (u/info (str "Generated namespace for \"" in-path "\" is \"" namespace "\"\n"))
              (u/info (str "Converting " in-path " to " out-path "...\n"))
              (let [stdout (co/stream-to-string
                             (co/proc "postcss"
                                      (.getPath in-file)
                                      (.getPath out-file)
                                      hash)
                             :out)]
                (when (or (empty? stdout) (nil? stdout))
                  (u/fail "\"" in-path "\" did not return any css..."))
                ;; we can have two types of "errors" here -- an error emitted by the postcss parsing
                ;; and an error that results from a null file pointer, or empty file input, etc.
                (if-let [module (generator/create-module namespace stdout)]
                  (if (:err module)
                    (u/fail (:err module))
                    (doto out-file io/make-parents (spit module)))
                  (u/fail "Something went wrong compiling: " in-path))))))
        (-> fileset
            (c/add-resource hash-cache)
            (c/add-resource tmp)
            c/commit!)))))

(deftask compile-stylus
  "Compile all .styl files in the fileset into .css files"
  []
  (let [tmp (c/tmp-dir!)
        prev (atom nil)]
    (c/with-pre-wrap fileset
      (let [diff (c/fileset-diff @prev fileset)
            in-files (c/input-files diff)
            styl-files (c/by-ext [".styl"] in-files)]
        (reset! prev fileset)
        (when (seq styl-files)
          (u/info "Compiling %d .styl files.\n" (count styl-files))
          (doseq [in styl-files]
            (let [in-file (c/tmp-file in)
                  in-path (c/tmp-path in)
                  out-path (.replaceAll in-path "\\.styl$" ".css")
                  out-file (io/file tmp out-path)]
              (println (str "Compiling " in-path " to " out-path "..."))
              (write-style! in-file out-file))))
        (-> fileset
            (c/add-resource tmp)
            c/commit!)))))

(deftask stylus
  "Compile all .styl files into clojure modules following css-modules syntax."
  []
  (comp
    (compile-stylus)
    (compile-css-modules)
    identity))
