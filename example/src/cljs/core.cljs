(ns example.core
  (:require
    [example.styles :as s]))

(def document js/document)

(defn render []
  (let [header (.add (.-classList (.createElement document "header")) s/head)
        footer (.add (.-classList (.createElement document "footer")) s/foot)]
    (-> (.-body document)
        (.append-child header)
        (.append-child footer))))

(render)

