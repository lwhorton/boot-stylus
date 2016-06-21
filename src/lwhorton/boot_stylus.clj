(ns lwhorton.boot-stylus
  {:boot/export-tasks true}
  (:require
    [boot.core :as c :refer [deftask]]
    [clojure.java.io :as io]
    [clojure.java.shell :as sh]
    [boot.util :as u]
    [me.raynes.conch :as conch]
    [me.raynes.conch.low-level :as co]
    [lwhorton.runtime :as runtime]
    )
  )

(defn write-style!
  "Use the Stylus api to compile in-file.styl to out-file.css."
  [in-file out-file]
  (conch/with-programs [stylus]
    (doto out-file io/make-parents (spit ""))
    (apply stylus ["--include-css" "-o" (.getPath out-file) in-file])
    (spit out-file (slurp out-file))))

(deftask add-styl
  "Add all .styl files into the fileset."
  []
  (let [tmp (c/tmp-dir!)
        prev (atom nil)]
    (c/with-pre-wrap fileset
      (let [diff (c/fileset-diff @prev fileset)
            in-files (c/input-files diff)
            styl-files (c/by-ext [".styl"] in-files)]
        (reset! prev fileset)
        (when (seq styl-files)
          (u/info "Detecting change to %d .styl files.\n" (count styl-files)))
          (doseq [in styl-files]
            (let [in-file (c/tmp-file in)
                  in-path (c/tmp-path in)
                  out-file (io/file tmp in-path)]
              (println (str "Adding .styl file " in-path " to fileset."))
              (doto out-file io/make-parents (spit (slurp in-file)))))
        (-> fileset
            (c/add-resource tmp)
            c/commit!)))))

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
  "Compile all .css files into css-modules with corresponding json source-map.
  Cache classname hashes (e.g. (def foo-class \"_foo-class_{hash}\")) in
  between runs so reloading files persists the proper string."
  []
  (let [tmp (c/tmp-dir!)
        hash-cache (c/cache-dir! ::css-modules)
        prev (atom nil)]
    (c/with-pre-wrap fileset
      (let [diff (c/fileset-diff @prev fileset)
            in-files (c/input-files diff)
            css (c/by-ext [".css"] in-files)
            filtered (c/not-by-name ["reset.css"] css)
            postcss (first (c/by-name ["run_postcss.js"] (c/input-files fileset)))
            postcss-runnable (c/tmp-file postcss)]
        (reset! prev fileset)
        (when (seq filtered)
          (u/info "Converting %d css-modules.\n" (count filtered))
          (doseq [in filtered]
            (let [in-file (c/tmp-file in)
                  in-path (c/tmp-path in)
                  out-path (str (.replaceAll in-path "\\.css$" ".cljs") )
                  out-file (io/file tmp out-path)
                  namespace (get-namespace out-path)
                  hash (get-hash hash-cache in-path namespace)]
              (u/info (str "Generated namespace: \"" namespace "\"\n"))
              (u/info (str "Converting " in-path " to " out-path "..."))
              (let [stdout (c/tmp-dir!)]
                (conch/with-programs [node]
                  (node (.getPath in-file) (.getPath out-file) hash
                        :in postcss-runnable
                        :out stdout
                        :env {"NODE_PATH" (generate-node-path)}))
                (doto out-file io/make-parents (spit (runtime/create-module namespace stdout)))))))
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
    (add-styl)
    (compile-stylus)
    (compile-css-modules)
    identity))
