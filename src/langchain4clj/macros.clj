(ns langchain4clj.macros
  "Macros for creating idiomatic Clojure wrappers around Java Builder patterns.

  This namespace provides utilities to eliminate Java builder boilerplate and
  create threading-friendly, composable APIs that feel natural in Clojure."
  (:require [clojure.string :as str]))

;; ============================================================================
;; Core Builder Macro
;; ============================================================================

(defmacro ^{:clj-kondo/ignore [:unresolved-symbol]} defbuilder
  "Creates an idiomatic Clojure wrapper around a Java Builder pattern.
  
  Generates a function that:
  - Takes a Clojure map with kebab-case keys
  - Maps keys to Java builder methods
  - Applies optional transformations
  - Returns the built Java object
  
  Args:
    fn-name       - Name of the generated function
    builder-expr  - Expression that creates a builder instance
    field-map     - Map of Clojure keys to Java methods with optional transformers
  
  Field map format:
    :clojure-key :javaMethod              ; Simple mapping
    :clojure-key [:javaMethod transformer-fn]  ; With transformation
  
  Example:
    (defbuilder build-openai-model
      (OpenAiChatModel/builder)
      {:api-key :apiKey
       :model :modelName
       :temperature :temperature
       :timeout [:timeout duration-from-millis]})
    
    ;; Usage:
    (build-openai-model {:api-key \"sk-...\" :model \"gpt-4\"})
    
    ;; Threading:
    (-> {:api-key \"sk-...\"} 
        (assoc :model \"gpt-4\")
        build-openai-model)
  
  Returns:
    The built Java object from calling .build() on the builder."
  [fn-name builder-expr field-map]
  (let [config-sym (gensym "config")
        builder-sym (gensym "builder")
        reduce-body (mapcat
                     (fn [[k spec]]
                       (let [[method transformer] (if (vector? spec)
                                                    spec
                                                    [spec nil])
                             method-sym (symbol (str "." (name method)))
                             value-sym (gensym "value")]
                         `[(when-let [~value-sym (get ~config-sym ~k)]
                             (let [~value-sym ~(if transformer
                                                 `(~transformer ~value-sym)
                                                 value-sym)]
                               (when (some? ~value-sym)
                                 (~method-sym ~builder-sym ~value-sym))))]))
                     field-map)]
    `(defn ~fn-name [~config-sym]
       (let [~builder-sym ~builder-expr]
         ~@reduce-body
         (.build ~builder-sym)))))

;; ============================================================================
;; Helper Functions (must be defined before build-with)
;; ============================================================================

(defn kebab->camel
  "Converts kebab-case keyword to camelCase string.
  
  Example:
    (kebab->camel :api-key) ;=> \"apiKey\"
    (kebab->camel :model-name) ;=> \"modelName\""
  [k]
  (let [parts (clojure.string/split (name k) #"-")]
    (str (first parts)
         (apply str (map clojure.string/capitalize (rest parts))))))

(defn build-field-map
  "Builds a field map for defbuilder from a sequence of keywords.
  Automatically converts kebab-case to camelCase.
  
  Args:
    fields - Sequence of keywords or [keyword transformer] pairs
  
  Example:
    (build-field-map [:api-key :model-name [:timeout duration-converter]])
    ;=> {:api-key :apiKey 
    ;    :model-name :modelName
    ;    :timeout [:timeout duration-converter]}"
  [fields]
  (into {}
        (map (fn [field]
               (if (vector? field)
                 (let [[k transformer] field]
                   [k [(keyword (kebab->camel k)) transformer]])
                 [field (keyword (kebab->camel field))])))
        fields))

;; ============================================================================
;; Build-With Helper Macro
;; ============================================================================

(defn build-with
  "Helper function for building Java objects with a builder pattern.
  
  Takes a builder instance and a map of configurations to apply.
  Uses reflection to call methods dynamically.
  
  Args:
    builder      - A Java builder instance (already created)
    config-map   - Map of configurations (kebab-case keys)
    
  The config-map keys are converted from kebab-case to camelCase method names.
  
  Example:
    (build-with (JsonStringSchema/builder)
      {:description \"A person's name\"
       :enum-values [\"Alice\" \"Bob\"]})
    
  Returns:
    The built Java object (calls .build() automatically)."
  [builder config-map]
  (doseq [[k v] config-map]
    (when (some? v)
      (let [method-name (kebab->camel k)
            method (try
                     (first (filter #(= method-name (.getName %))
                                    (.getMethods (class builder))))
                     (catch Exception _ nil))]
        (when method
          (.invoke method builder (into-array Object [v]))))))
  (.build builder))

;; ============================================================================
;; Threading Helpers
;; ============================================================================

(defn apply-if
  "Applies function f to value only if condition is truthy.
  Useful in threading macros for conditional transformations.
  
  Example:
    (-> {:name \"Alice\"}
        (apply-if (:admin? opts) assoc :role :admin)
        (apply-if (:verified? opts) assoc :verified true))"
  [value condition f & args]
  (if condition
    (apply f value args)
    value))

(defn apply-when-some
  "Applies function f with args to value, only when x is not nil.
  If x is nil, returns value unchanged.
  The args are passed as-is to f, with x being checked but not included.
  
  Example:
    (-> {:name \"Alice\"}
        (apply-when-some \"alice@example.com\" assoc :email \"alice@example.com\")
        (apply-when-some nil assoc :phone \"555-1234\"))
        
  Or more commonly used to check if a value exists before using it:
    (let [email (get opts :email)]
      (-> {:name \"Alice\"}
          (apply-when-some email assoc :email email)))"
  [value x f & args]
  (if (some? x)
    (apply f value args)
    value))

;; ============================================================================
;; Config Composition Helpers
;; ============================================================================

(defn deep-merge
  "Recursively merges maps. Later maps take precedence.
  Useful for composing configuration maps.
  
  Example:
    (deep-merge {:a 1 :b {:c 2}} 
                {:b {:d 3} :e 4})
    ;=> {:a 1 :b {:c 2 :d 3} :e 4}"
  [& maps]
  (letfn [(merge-entry [m e]
            (let [k (key e)
                  v (val e)]
              (if (and (map? v) (map? (get m k)))
                (assoc m k (deep-merge (get m k) v))
                (assoc m k v))))]
    (reduce
     (fn [m1 m2]
       (reduce merge-entry m1 m2))
     (or (first maps) {})
     (rest maps))))

(defn with-defaults
  "Merges config with defaults, preferring config values.
  
  Example:
    (with-defaults {:model \"gpt-4\"}
                   {:model \"gpt-3.5\" :temperature 0.7})
    ;=> {:model \"gpt-4\" :temperature 0.7}"
  [config defaults]
  (merge defaults config))

;; ============================================================================
;; Deprecation Warning Helper
;; ============================================================================

(defn deprecation-warning
  "Prints a deprecation warning message.
  
  Args:
    old-fn      - Name of deprecated function
    new-usage   - String showing recommended new usage
    remove-ver  - Version when the old function will be removed
  
  Example:
    (deprecation-warning \"create-assistant\" 
                        \"(-> config assistant)\"
                        \"v1.0.0\")"
  [old-fn new-usage remove-ver]
  (println (format "⚠️  DEPRECATION WARNING: %s is deprecated.\n   Use: %s\n   Will be removed in: %s"
                   old-fn
                   new-usage
                   remove-ver)))

;; ============================================================================
;; Builder Field Converters
;; ============================================================================


