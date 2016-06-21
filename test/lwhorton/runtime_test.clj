(ns lwhorton.runtime-test
  (:require [clojure.test :as t :refer [deftest testing is are]]
            [lwhorton.runtime :as sut]
            [clojure.java.io :as io]))

(deftest create-class-test
  (testing "writing a class definition"
    (testing "should define the class as the (def {class-name} {css-module class-string})"
      (is (= "(def foo \"._foo_mangle_1\")" (#'sut/create-class "foo" "._foo_mangle_1")))
      (is (= "(def bar-baz \"._bar_baz_mangle_1\")" (#'sut/create-class "bar-baz" "._bar_baz_mangle_1")))
      (is (= "(def foo-three-deep \"._foo_three_deep_mangle_1\")" (#'sut/create-class "foo-three-deep" "._foo_three_deep_mangle_1")))
      )))

(deftest create-module-contents-test
  (testing "writing a cljs module from css-module css contents"
    (let [contents (#'sut/create-module-contents "foo-bar.styles"
                                               {:foo_class "_foo_class_mangled_1"
                                                :bar "_bar_mangled_2"}
                                               ".some-css {}")
          split (clojure.string/split contents #"\n")]
      (testing "should write the given ns appending .styles"
        (is (= (first split) "(ns foo-styles.styles)")))
      (testing "should write a def block for each class entry"
        (is (= (nth split 1) "(def foo-class \"_foo_class_mangled_1\")"))
        (is (= (nth split 2) "(def bar \"_bar_mangled_2\")")))

      (testing "should write an expr invoking update-stylesheet! with {:source :name}"
        (let [src ".some-css {}"
              sheet (str "{:name \"pod/foo-styles\", :source \"" src "\"}")]
          (is (= (str "(do (css-modules.runtime/update-stylesheet! " sheet "))")
                 (nth split 3)))))
      )))

(deftest parse-stdout-test
  (testing "parsing stdout in css-modules' special format"
    (let [stdout "~json~{\"foo\": true}~json~~css~.class{display:block;}~css~"
          [edn css] (#'sut/parse-stdout stdout)]
    (testing "should return edn from the ~json~"
      (is (= {:foo true} edn)))
    (testing "hsould return a string from the ~css~"
      (is (= ".class{display:block;}" css))))))


