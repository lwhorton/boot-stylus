(set-env!
  :source-paths #{"src/cljs"}
  :resource-paths #{"resources"}
  :checkouts '[[lwhorton/boot-stylus "0.0.3-SNAPSHOT"]]
  :dependencies '[
                  [org.clojure/clojure "1.8.0"]
                  [org.clojure/clojurescript "1.8.40"]
                  [lwhorton/boot-stylus "0.0.1"]
                  [adzerk/boot-cljs "1.7.228-1" :scope "test"] ;; compile cljs -> js
                  [pandeiro/boot-http "0.7.3" :scope "test"] ;; http server
                  [adzerk/boot-reload "0.4.8" :scope "test"] ;; reload on change
                  ]
  )

(require
  '[boot.core :refer [deftask]]
  '[pandeiro.boot-http :refer [serve]]
  '[lwhorton.boot-stylus.core :refer [stylus]]
  '[adzerk.boot-cljs :refer [cljs]]
  '[adzerk.boot-reload :refer [reload]]
  )

(deftask test
  "Compile, test stylus, and serve"
  []
  (comp
    (watch)
    (serve :dir "target")
    (reload)
    (stylus)
    (cljs)
    (target :dir #{"target"})))


