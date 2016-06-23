(set-env!
  :source-paths #{"src/lwhorton"}
  :dependencies '[
                  [org.clojure/clojure "1.8.0" :scope "provided"]
                  [me.raynes/conch "0.8.0"]
                  ]
  )

(def +version+ "0.0.1")

(deftask build-jar
  "Build a jar and install to local maven repo."
  []
  (comp
    (watch)
    (pom)
    (jar)
    (install)
    identity))

(deftask dev
  "Rebuilt POM on change for fast iteration."
  []
  (comp
    (watch)
    (build-jar)
    identity))

(task-options!
  pom {:project 'lwhorton/boot-stylus
       :version +version+
       :description "Boot task to compile stylus files to css-module clojure namespaces."})

