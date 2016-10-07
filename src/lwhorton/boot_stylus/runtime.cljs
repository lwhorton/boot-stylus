(ns lwhorton.boot-stylus.runtime
  (:require [goog.object :as g]))

(def oset g/set)
(def oget g/getValueByKeys)

(defonce style-tags (atom {}))

(defn- make-style-tag []
  (let [ele (.createElement js/document "style")
        _ (oset ele "type" "text/css")
        head (.querySelector js/document "head")]
    (.appendChild head ele)
    ele))

(defn- update-style-tag! [ele contents]
  (let [css (.createTextNode js/document contents)
        existing (oget ele "firstChild")]
    (when existing (.removeChild ele existing))
    (.appendChild ele css)))

(defn- insert-style-tag! [id css]
  (let [ele (make-style-tag)]
    (update-style-tag! ele css)
    (swap! style-tags assoc id ele)))

(defn update-stylesheet! [{:keys [source name]}]
  (if-let [existing (@style-tags name)]
    (update-style-tag! existing source)
    (insert-style-tag! name source)))
