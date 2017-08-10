(set-env!
  :source-paths #{"src/cljs"}
  :resource-paths #{"resources"}
  :checkouts '[[lwhorton/boot-stylus "0.0.3-SNAPSHOT"]]
  :dependencies '[
                  [org.clojure/clojure "1.8.0"]
                  [org.clojure/clojurescript "1.9.854"]
                  [lwhorton/boot-stylus "0.0.3-SNAPSHOT"]
                  [cljsjs/react-table "6.0.1-SNAPSHOT"]
                  [cljsjs/react "15.6.1-1"]
                  [adzerk/boot-cljs "2.1.2-SNAPSHOT" :scope "test"] ;; compile cljs -> js
                  [pandeiro/boot-http "0.7.3" :scope "test"] ;; http server
                  [adzerk/boot-reload "0.4.8" :scope "test"] ;; reload on change
                  ]
  )

(require
  '[boot.core :refer [deftask] :as c]
  '[pandeiro.boot-http :refer [serve]]
  '[lwhorton.boot-stylus.core :refer [stylus]]
  '[adzerk.boot-cljs :refer [cljs]]
  '[adzerk.boot-reload :refer [reload]]
  )

(deftask from-jars
  "Import files from a jar (e.g. CLJSJS) and move them to the given location in the fileset."
  [i imports IMPORT #{[sym regex str]} "Tuples describing import: [jar-sym regex-path-in-jar target-path]"]
  (let [add-jar-args (into {} (for [[sym p] imports] [sym p]))
        move-args (into {} (for [[_ p t] imports] [p t]))]
    (sift :add-jar add-jar-args :move move-args)))

(deftask dev
  "Test boot-stylus compilation, serving the output at localhost:3000"
  []
  (comp
    (serve :dir "target")
    (watch)
    (reload)
    (from-jars :imports #{['cljsjs/react-table
                           #"^cljsjs/react-table/common/react-table\.inc\.css$"
                           "assets/cljsjs/react-table/common/react-table.inc.css"]})
    (stylus)
    (cljs :ids #{"js/main"})
    (from-jars :imports #{['cljsjs/react-table
                           #"^cljsjs/react-table/common/react-table\.inc\.css$"
                           "assets/cljsjs/react-table/common/react-table.inc.css"]})
    (sift :to-asset #{#"^js/.*"})
    (show :fileset true)
    (target :dir #{"target"})))
