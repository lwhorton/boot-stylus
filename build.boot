(set-env!
  :resource-paths #{"src"}
  :dependencies '[
                  [org.clojure/clojure "1.8.0" :scope "provided"]
                  [me.raynes/conch "0.8.0"]
                  ]
  :repositories #(conj % ["clojars" {:url "https://clojars.org/repo/"
                                     :username (System/getenv "CLOJARS_USER")
                                     :password (System/getenv "CLOJARS_PASS")}])
  )

(require '[boot.task.built-in :refer [push]])

(def +version+ "0.0.3-SNAPSHOT")

(deftask build
  "Build the jar in prep for deploy."
  []
  (comp
    (pom)
    (jar)
    identity))

(deftask dev
  "Build a jar and install to local maven repo."
  []
  (comp
    (watch)
    (build)
    (install)
    identity))

(deftask deploy
  []
  (comp
    (build)
    (push :repo "clojars")))

(task-options!
  pom {:project 'lwhorton/boot-stylus
       :version +version+
       :description "Boot task to compile stylus files to css-module clojure namespaces."
       :url "https://github.com/lwhorton/boot-stylus"
       :scm {:url "https://github.com/lwhorton/boot-stylus"}
       }
  )

