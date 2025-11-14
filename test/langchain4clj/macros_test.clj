(ns langchain4clj.macros-test
  (:require [clojure.test :refer [deftest is testing]]
            [langchain4clj.macros :as macros]))

;; ============================================================================
;; Helper Java Classes for Testing
;; ============================================================================

(deftype TestBuilder [^:volatile-mutable name
                      ^:volatile-mutable age
                      ^:volatile-mutable email]
  Object
  (toString [_] (str "TestBuilder[name=" name ", age=" age ", email=" email "]")))

(defn test-builder []
  (->TestBuilder nil nil nil))

(defn set-name [^TestBuilder builder n]
  (set! (.name builder) n)
  builder)

(defn set-age [^TestBuilder builder a]
  (set! (.age builder) a)
  builder)

(defn set-email [^TestBuilder builder e]
  (set! (.email builder) e)
  builder)

(defn build-test-object [^TestBuilder builder]
  {:name (.name builder)
   :age (.age builder)
   :email (.email builder)})

;; ============================================================================
;; Threading Helpers Tests
;; ============================================================================

(deftest test-apply-if
  (testing "apply-if applies function when condition is true"
    (is (= {:name "Alice" :admin true}
           (-> {:name "Alice"}
               (macros/apply-if true assoc :admin true)))))

  (testing "apply-if skips function when condition is false"
    (is (= {:name "Alice"}
           (-> {:name "Alice"}
               (macros/apply-if false assoc :admin true)))))

  (testing "apply-if works with multiple args"
    (is (= {:name "Alice" :roles [:user :admin]}
           (-> {:name "Alice"}
               (macros/apply-if true assoc :roles [:user :admin]))))))

(deftest test-apply-when-some
  (testing "apply-when-some applies function when value is not nil"
    (is (= {:name "Alice" :email "alice@example.com"}
           (-> {:name "Alice"}
               (macros/apply-when-some "alice@example.com" assoc :email "alice@example.com")))))

  (testing "apply-when-some skips function when value is nil"
    (is (= {:name "Alice"}
           (-> {:name "Alice"}
               (macros/apply-when-some nil assoc :email "alice@example.com")))))

  (testing "apply-when-some works with falsy values"
    (is (= {:name "Alice" :count 0}
           (-> {:name "Alice"}
               (macros/apply-when-some 0 assoc :count 0)))))

  (testing "apply-when-some common pattern with let binding"
    (let [opts {:email "test@example.com"}
          email (:email opts)
          phone (:phone opts)]
      (is (= {:name "Bob" :email "test@example.com"}
             (-> {:name "Bob"}
                 (macros/apply-when-some email assoc :email email)
                 (macros/apply-when-some phone assoc :phone phone)))))))

;; ============================================================================
;; Config Composition Tests
;; ============================================================================

(deftest test-deep-merge
  (testing "deep-merge merges flat maps"
    (is (= {:a 1 :b 2 :c 3}
           (macros/deep-merge {:a 1 :b 2} {:c 3}))))

  (testing "deep-merge merges nested maps"
    (is (= {:a 1 :b {:c 2 :d 3} :e 4}
           (macros/deep-merge {:a 1 :b {:c 2}} {:b {:d 3} :e 4}))))

  (testing "deep-merge prefers later values"
    (is (= {:a 2}
           (macros/deep-merge {:a 1} {:a 2}))))

  (testing "deep-merge handles nil"
    (is (= {:a 1}
           (macros/deep-merge nil {:a 1})))
    (is (= {:a 1}
           (macros/deep-merge {:a 1} nil))))

  (testing "deep-merge with multiple maps"
    (is (= {:a 1 :b 2 :c 3}
           (macros/deep-merge {:a 1} {:b 2} {:c 3})))))

(deftest test-with-defaults
  (testing "with-defaults prefers config values"
    (is (= {:model "gpt-4" :temperature 0.7}
           (macros/with-defaults {:model "gpt-4"}
             {:model "gpt-3.5" :temperature 0.7}))))

  (testing "with-defaults uses defaults when config lacks values"
    (is (= {:model "gpt-3.5" :temperature 0.7}
           (macros/with-defaults {}
             {:model "gpt-3.5" :temperature 0.7})))))

;; ============================================================================
;; Builder Field Converters Tests
;; ============================================================================

(deftest test-kebab->camel
  (testing "kebab->camel converts single word"
    (is (= "name" (macros/kebab->camel :name))))

  (testing "kebab->camel converts two words"
    (is (= "apiKey" (macros/kebab->camel :api-key))))

  (testing "kebab->camel converts multiple words"
    (is (= "modelNameString" (macros/kebab->camel :model-name-string))))

  (testing "kebab->camel handles single letter words"
    (is (= "aB" (macros/kebab->camel :a-b)))))

(deftest test-build-field-map
  (testing "build-field-map converts simple fields"
    (is (= {:api-key :apiKey
            :model-name :modelName}
           (macros/build-field-map [:api-key :model-name]))))

  (testing "build-field-map handles transformer pairs"
    (let [transformer identity
          field-map (macros/build-field-map [:api-key [:timeout transformer]])]
      (is (= :apiKey (:api-key field-map)))
      (is (vector? (:timeout field-map)))
      (is (= :timeout (first (:timeout field-map))))
      (is (fn? (second (:timeout field-map))))))

  (testing "build-field-map mixes simple and transformer fields"
    (let [field-map (macros/build-field-map [:api-key [:timeout identity] :model])]
      (is (= :apiKey (:api-key field-map)))
      (is (vector? (:timeout field-map)))
      (is (= :model (:model field-map))))))

;; ============================================================================
;; Deprecation Warning Test
;; ============================================================================

(deftest test-deprecation-warning
  (testing "deprecation-warning prints message without throwing"
    (is (nil? (macros/deprecation-warning "old-fn" "(-> config new-fn)" "v1.0.0")))))

;; ============================================================================
;; Integration Tests with Real-World Patterns
;; ============================================================================

(deftest test-threading-pattern
  (testing "threading with apply-if and apply-when-some"
    (let [opts {:email "alice@example.com" :admin? true}
          email (:email opts)
          phone (:phone opts)]
      (is (= {:name "Alice"
              :email "alice@example.com"
              :role :admin}
             (-> {:name "Alice"}
                 (macros/apply-when-some email assoc :email email)
                 (macros/apply-if (:admin? opts) assoc :role :admin)
                 (macros/apply-when-some phone assoc :phone phone)))))))

(deftest test-config-composition
  (testing "composing configs with deep-merge and with-defaults"
    (let [base-config {:model "gpt-3.5" :temperature 0.7}
          user-config {:model "gpt-4" :options {:timeout 30}}
          default-options {:options {:retries 3 :timeout 60}}
          result (-> default-options
                     (macros/deep-merge base-config)
                     (macros/deep-merge user-config))]
      (is (= {:model "gpt-4"
              :temperature 0.7
              :options {:retries 3 :timeout 30}}
             result)))))
