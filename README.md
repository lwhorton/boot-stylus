# boot-stylus

### Compile .styl files into clojurescript modules.

Everything about CSS is miserable- the syntax, the specificity rules, the cascading, etc.
Preprocessors make *some* things much more tolerable, but they don't solve the most pressing problem- modularity. The new-ish [css-modules](http://glenmaddern.com/articles/css-modules)
is an attempt to make css more modular. Since I hate writing
css so much, I wanted to go one step further and utilize [stylus](http://stylus-lang.com/). Thus was born boot-stylus, a boot task to convert `.styl` files inside your `:source-paths` into cljs modules that can be brought in via the clojure's standard `(:require)`.

## How to use
List boot-stylus as a depencency:
```clojure
(set-env!
  :dependencies '[lwhorton/boot-stylus "0.0.2-SNAPSHOT" :scope "test"])
```

Add the stylus task somewhere in your build task:
```clojure
(require
  '[lwhorton.boot-stylus.core :refer [stylus]])

(deftask dev []
  (comp
    (stylus)
    (cljs)
    (target :dir #{"target"})))
```

Now any file inside `:source-paths` that ends in `.styl` will be compiled into a `.cljs` module. Namespaces are auto-generated following the convention `(ns {path}.{to_file}.{filename}})`.

For example, `src/my_project/my_feature/styles.styl` will be compiled into `{out}/my_project/my_feature/styles.cljs` (assumping `src` is on the classpath).

## Example

Given the stylus:
```stylus
.foo
  color blue

.bar
  color green
```

The generated module will look something like this:
```clojure
(ns my-project.my-feature.styles)

(def .__foo__[hash] ".foo { color: blue; }")
(def .__bar__[hash] ".bar { color: green; }")
```

Which you can require from inside your clojurescript:
```clojure
(ns my-project.my-feature.core
  (:requires '[[my-project.my-feature.styles :as s]]))

;; hiccup
(defn render []
  [:div
    [:p {:class s/bar} "Goodbye css"]
    [:p {:class s/foo} "Hello world"]])
```

## ¡IMPORTANT!

boot-stylus currently depends on two node modules: [postcss](https://github.com/postcss/postcss) and [stylus cli](http://stylus-lang.com/docs/executable.html). They must be globally available, i.e. running `user$ stylus ./foo.styl > ./bar.css` from the terminal should work. You can install them via [npm](https://www.npmjs.com/).
