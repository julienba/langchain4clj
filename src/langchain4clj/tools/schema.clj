(ns langchain4clj.tools.schema
  "Plumatic Schema implementation of SchemaProvider protocol"
  (:require [schema.core :as s]
            [schema.coerce :as coerce]
            [schema.utils :as utils]
            [langchain4clj.tools.protocols :as p]))

;; ============================================================================
;; Schema to JSON Schema conversion
;; ============================================================================

(defn schema->json-schema
  "Converts a Plumatic Schema to JSON Schema"
  [schema]
  (cond
    ;; Primitive types
    (= schema s/Str) {:type "string"}
    (= schema s/Num) {:type "number"}
    (= schema s/Int) {:type "integer"}
    (= schema s/Bool) {:type "boolean"}
    (= schema s/Keyword) {:type "string" :format "keyword"}
    (= schema s/Symbol) {:type "string" :format "symbol"}
    (= schema s/Any) {:type "any"}
    (= schema s/Uuid) {:type "string" :format "uuid"}
    (= schema s/Inst) {:type "string" :format "date-time"}

    ;; Regex
    (instance? java.util.regex.Pattern schema)
    {:type "string" :pattern (str schema)}

    ;; Enums
    (satisfies? s/Schema schema)
    (cond
      (instance? schema.core.EnumSchema schema)
      {:type "string"
       :enum (vec (.vs ^schema.core.EnumSchema schema))}

      (instance? schema.core.EqSchema schema)
      {:const (.v ^schema.core.EqSchema schema)}

      (instance? schema.core.Maybe schema)
      (assoc (schema->json-schema (.schema ^schema.core.Maybe schema))
             :nullable true)

      :else {:type "any"})

    ;; Arrays/Vectors
    (vector? schema)
    (if (= (count schema) 1)
      {:type "array"
       :items (schema->json-schema (first schema))}
      {:type "array"
       :items {:type "any"}})

    ;; Maps/Objects
    (map? schema)
    (let [properties (reduce-kv
                      (fn [acc k v]
                        (let [key-name (cond
                                         (s/optional-key? k) (name (:k k))
                                         (s/required-key? k) (name (:k k))
                                         (keyword? k) (name k)
                                         :else (str k))
                              json-schema (schema->json-schema v)]
                          (assoc acc key-name json-schema)))
                      {}
                      schema)
          required (vec (keep #(when-not (s/optional-key? %)
                                 (cond
                                   (s/required-key? %) (name (:k %))
                                   (keyword? %) (name %)
                                   :else (str %)))
                              (keys schema)))]
      {:type "object"
       :properties properties
       :required required})

    ;; Predicates and functions
    (fn? schema)
    {:type "any" :description "Custom predicate"}

    ;; Default
    :else {:type "any"}))

;; ============================================================================
;; SchemaProvider implementation for Plumatic Schema
;; ============================================================================

(defrecord SchemaProvider [schema coercer]
  p/SchemaProvider

  (validate [_ data]
    (try
      (s/validate schema data)
      data
      (catch Exception e
        (throw (ex-info "Validation failed"
                        {:type :validation-error
                         :schema schema
                         :data data
                         :error (.getMessage e)})))))

  (coerce [_ data]
    (let [result (coercer data)]
      (if (utils/error? result)
        (throw (ex-info "Coercion failed"
                        {:type :coercion-error
                         :schema schema
                         :data data
                         :error (pr-str result)}))
        result)))

  (to-json-schema [_]
    (schema->json-schema schema))

  (explain-error [_ data]
    (try
      (s/validate schema data)
      "Valid"
      (catch Exception e
        (.getMessage e)))))

;; ============================================================================
;; Public API
;; ============================================================================

(defn create-schema-provider
  "Creates a SchemaProvider for a Plumatic Schema"
  ([schema]
   (create-schema-provider schema coerce/json-coercion-matcher))
  ([schema coercion-matcher]
   (->SchemaProvider schema (coerce/coercer schema coercion-matcher))))

(defn schema?
  "Checks if the given value is a Plumatic Schema"
  [x]
  (or (satisfies? s/Schema x)
      (map? x)
      (vector? x)
      (#{s/Str s/Num s/Int s/Bool s/Keyword s/Symbol s/Any s/Uuid s/Inst} x)))
