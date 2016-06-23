(ns lwhorton.boot-stylus.runtime)

(defonce style-tags (atom {}))

(defn- make-style-tag []
  (let [ele (.createElement js/document "style")
        head (.querySelector js/document "head")]
    (.appendChild head ele)
    ele))

(defn- update-style-tag! [ele contents]
  (set! (.-innerHTML ele) contents))

(defn- insert-style-tag! [id css]
  (let [ele (make-style-tag)]
    (update-style-tag! ele css)
    (swap! style-tags assoc id ele)))

(defn update-stylesheet! [{:keys [source name]}]
  (if-let [existing (@style-tags name)]
    (update-style-tag! existing source)
    (insert-style-tag! name source)))
