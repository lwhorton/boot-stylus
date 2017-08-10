(ns example.core
  (:require
    [example.styles :as s]
    [example.base]
    [react-table :as react-table]
    [clojure.string :as str]
    )
  )

(def document js/document)

(defn render []
  (.log js/console s/head)
  (.log js/console s/foot)
  (let [header (.createElement document "header")
        footer (.createElement document "footer")]
    (set! (.-classList header) s/head)
    (set! (.-classList footer) s/foot)
    (doto (.-body document)
        (.appendChild header)
        (.appendChild footer))))

(render)

