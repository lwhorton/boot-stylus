# boot-stylus

### Compile .styl files into clojurescript modules.

Everything about CSS is miserable- the syntax, the specificity rules, the cascading, etc.
Preprocessors make *some* things much more tolerable, but they don't solve the most pressing problem- modularity. The new-ish [css-modules](http://glenmaddern.com/articles/css-modules)
is an attempt to make css more modular. Since I hate writing
css so much, I wanted to go one step further and utilize [stylus](http://stylus-lang.com/). Thus was born boot-stylus, a boot task to convert `.styl` files inside your `:source-paths` into cljs modules that can be brought in via clojure's standard `(:require [module])`.

## How to use

> ¡IMPORTANT! boot-stylus currently depends on node in order to run. As soon as someone decides to port a clojure css-modules generator, that dependency will go away.


List boot-stylus as a depencency:
```clojure
(set-env!
  :dependencies '[[lwhorton/boot-stylus "0.0.2-SNAPSHOT" :scope "test"]])
```

Add stylus to your build task in the `build.boot`` file:
```clojure
(require
  '[lwhorton.boot-stylus.core :refer [stylus]])

(deftask dev []
  (comp
    (stylus)
    (cljs)
    (target :dir #{"target"})))
```

Now any file inside `:source-paths` that ends in `.styl` will be compiled into a `.cljs` module. Namespaces are auto-generated following the convention `/{path}/{to}/{file}.styl -> (ns {path}.{to_file}.{file})`.

For example, `my_project/my_feature/styles.styl` will be compiled into `{out}/my_project/my_feature/styles.cljs`.

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
  (:require [my-project.my-feature.styles :as s]))

;; hiccup
(defn render []
  [:div
    [:p {:class s/foo} "Goodbye css"]
    [:p {:class s/bar} "Hello world"]])
```

That will ultimately render the html:
```
<div>
    <p class="__foo__[hash]">Goodbye css</p>
    <p class="__bar__[hash]">Hello world</p>

</div>
```

## Special Thanks

A super-special thanks to [@micha](https://github.com/micha) and [@martinklepsch](https://github.com/martinklepsch)
 who were instrumental in explaining the intricacies of boot ( a really great idea once you get to know it ).
Check out their work, they're both really talented and offer some great ideas for the clojure community.
