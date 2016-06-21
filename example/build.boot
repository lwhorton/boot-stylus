(set-env!
  :source-paths #{"src/cljs" "../src"}
  :resource-paths #{"resources"}
  :checkouts '[[lwhorton/boot-stylus "0.0.1"]]
  :dependencies '[
                  [org.clojure/clojure "1.8.0"]
                  [org.clojure/clojurescript "1.8.40"]
                  [lwhorton/boot-stylus "0.0.1"]
                  [adzerk/boot-cljs "1.7.228-1" :scope "test"] ;; compile cljs -> js
                  [pandeiro/boot-http "0.7.3" :scope "test"] ;; http server
                  ]
  )

(require
  '[boot.core :refer [deftask]]
  '[pandeiro.boot-http :refer [serve]]
  '[lwhorton.boot-stylus :refer [stylus]]
  '[adzerk.boot-cljs :refer [cljs]]
  )

(deftask test
  "Compile, test stylus, and serve"
  []
  (comp
    (serve :dir "target")
    (stylus)
    (cljs)
    (target :dir #{"target"})))


