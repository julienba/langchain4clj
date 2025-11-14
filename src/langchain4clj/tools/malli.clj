(ns langchain4clj.tools.malli
  "Malli implementation of SchemaProvider protocol"
  (:require [malli.core :as m]
            [malli.error :as me]
            [malli.json-schema :as mjs]
            [malli.transform :as mt]
            [langchain4clj.tools.protocols :as p]))

;; ============================================================================
;; SchemaProvider implementation for Malli
;; ============================================================================

(defrecord MalliProvider [schema]
  p/SchemaProvider

  (validate [_ data]
    (if (m/validate schema data)
      data
      (throw (ex-info "Validation failed"
                      {:type :validation-error
                       :schema schema
                       :data data
                       :explanation (m/explain schema data)}))))

  (coerce [_ data]
    ;; Use json-transformer for coercion (handles string->number, etc)
    (m/decode schema data mt/json-transformer))

  (to-json-schema [_]
    ;; Malli has built-in JSON Schema support!
    (mjs/transform schema))

  (explain-error [_ data]
    ;; Malli has excellent error messages
    (-> (m/explain schema data)
        (me/humanize))))

;; ============================================================================
;; Public API
;; ============================================================================

(defn create-malli-provider
  "Creates a SchemaProvider for a Malli schema"
  [schema]
  (->MalliProvider schema))

(defn malli?
  "Checks if the given value is a Malli schema"
  [x]
  (or (m/schema? x)
      (and (vector? x)
           (keyword? (first x)))
      (and (sequential? x)
           (keyword? (first x)))))
