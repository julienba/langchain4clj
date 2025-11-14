(ns langchain4clj.tools.protocols
  "Protocolo unificado para suportar múltiplas bibliotecas de schema/spec")

;; ============================================================================
;; PROTOCOLO UNIFICADO
;; ============================================================================

(defprotocol SchemaProvider
  "Protocolo para abstrair diferentes bibliotecas de schema"
  (validate [this data]
    "Valida dados contra o schema")
  (coerce [this data]
    "Coerce dados para tipos apropriados")
  (to-json-schema [this]
    "Converte para JSON Schema do LangChain4j")
  (explain-error [this error]
    "Explica erro de validação de forma legível"))

;; ============================================================================
;; IMPLEMENTAÇÃO PARA CLOJURE.SPEC
;; ============================================================================

(ns langchain4clj.tools.spec
  (:require [clojure.spec.alpha :as spec]
            [clojure.spec.gen.alpha :as gen]
            [langchain4clj.tools.protocols :as p]))

(defrecord SpecSchema [spec-key registry]
  p/SchemaProvider
  (validate [_ data]
    (if (spec/valid? spec-key data)
      data
      (throw (ex-info "Validation failed"
                      {:error (spec/explain-data spec-key data)}))))

  (coerce [this data]
    ;; Spec não tem coerção built-in, mas podemos usar conform
    (let [conformed (spec/conform spec-key data)]
      (if (= conformed ::spec/invalid)
        (p/validate this data) ; Vai jogar exceção
        conformed)))

  (to-json-schema [_]
    ;; Converter spec para JSON Schema
    (spec->json-schema spec-key registry))

  (explain-error [_ error]
    (spec/explain-str (:spec error) (:value error))))

(defn spec->json-schema
  "Converte clojure.spec para JSON Schema"
  [spec-key registry]
  ;; Analisar a spec e gerar JSON Schema
  (let [form (spec/form spec-key)]
    (cond
      ;; Primitivos
      (= form 'string?) {:type "string"}
      (= form 'int?) {:type "integer"}
      (= form 'number?) {:type "number"}
      (= form 'boolean?) {:type "boolean"}

      ;; Keys (maps)
      (and (seq? form) (= (first form) 'spec/keys))
      (let [req (-> form (nth 1) :req)
            opt (-> form (nth 1) :opt)]
        {:type "object"
         :properties (merge
                      (into {} (map (fn [k]
                                      [(name k) (spec->json-schema k registry)])
                                    req))
                      (into {} (map (fn [k]
                                      [(name k) (spec->json-schema k registry)])
                                    opt)))
         :required (mapv name req)})

      ;; Collections
      (and (seq? form) (= (first form) 'spec/coll-of))
      {:type "array"
       :items (spec->json-schema (second form) registry)}

      ;; Default
      :else {:type "any"})))

;; ============================================================================
;; IMPLEMENTAÇÃO PARA PLUMATIC SCHEMA
;; ============================================================================

(ns langchain4clj.tools.schema
  (:require [schema.core :as s]
            [schema.coerce :as coerce]
            [langchain4clj.tools.protocols :as p]))

(defrecord SchemaSchema [schema]
  p/SchemaProvider
  (validate [_ data]
    (s/validate schema data))

  (coerce [_ data]
    (let [coercer (coerce/coercer schema coerce/json-coercion-matcher)
          result (coercer data)]
      (if (s/error? result)
        (throw (ex-info "Coercion failed" {:error result}))
        result)))

  (to-json-schema [_]
    (schema->json-schema schema))

  (explain-error [_ error]
    (s/explain error)))

(defn schema->json-schema [schema]
  ;; Implementação já mostrada anteriormente
  (cond
    (= schema s/Str) {:type "string"}
    (= schema s/Int) {:type "integer"}
    (= schema s/Num) {:type "number"}
    (= schema s/Bool) {:type "boolean"}
    ;; ... resto da implementação
    ))

;; ============================================================================
;; IMPLEMENTAÇÃO PARA MALLI
;; ============================================================================

(ns langchain4clj.tools.malli
  (:require [malli.core :as m]
            [malli.error :as me]
            [malli.json-schema :as mjs]
            [malli.transform :as mt]
            [langchain4clj.tools.protocols :as p]))

(defrecord MalliSchema [schema]
  p/SchemaProvider
  (validate [_ data]
    (if (m/validate schema data)
      data
      (throw (ex-info "Validation failed"
                      {:error (m/explain schema data)}))))

  (coerce [_ data]
    (m/decode schema data mt/json-transformer))

  (to-json-schema [_]
    (mjs/transform schema))

  (explain-error [_ error]
    (me/humanize error)))

;; ============================================================================
;; DETECTOR AUTOMÁTICO E FACTORY
;; ============================================================================

(ns langchain4clj.tools
  (:require [langchain4clj.tools.protocols :as p]
            [langchain4clj.tools.spec :as spec-impl]
            [langchain4clj.tools.schema :as schema-impl]
            [langchain4clj.tools.malli :as malli-impl]
            [clojure.spec.alpha :as spec]
            [schema.core :as s]
            [malli.core :as m])
  (:import [dev.langchain4j.agent.tool ToolSpecification]))

(defn detect-schema-type
  "Detecta automaticamente o tipo de schema"
  [schema]
  (cond
    ;; Clojure Spec - keywords ou specs registradas
    (keyword? schema) :spec
    (and (seq? schema)
         (= (first schema) 'spec/keys)) :spec

    ;; Malli - vectors começando com keyword
    (and (vector? schema)
         (keyword? (first schema))) :malli

    ;; Plumatic Schema - maps ou schemas conhecidos
    (map? schema) :schema
    (#{s/Str s/Int s/Num s/Bool} schema) :schema

    ;; Default
    :else (throw (ex-info "Cannot detect schema type"
                          {:schema schema}))))

(defn create-schema-provider
  "Cria o provider apropriado baseado no tipo ou detecção automática"
  ([schema]
   (create-schema-provider schema (detect-schema-type schema)))
  ([schema type]
   (case type
     :spec (spec-impl/->SpecSchema schema nil)
     :schema (schema-impl/->SchemaSchema schema)
     :malli (malli-impl/->MalliSchema schema)
     (throw (ex-info "Unknown schema type" {:type type})))))

;; ============================================================================
;; API UNIFICADA PARA TOOLS
;; ============================================================================

(defn create-tool
  "Cria uma tool com suporte para qualquer biblioteca de schema
   
   Exemplos:
   
   ;; Com Clojure Spec
   (create-tool {:name \"calculator\"
                 :description \"Calculates expressions\"
                 :params-schema ::calculator-params
                 :fn calculate})
   
   ;; Com Plumatic Schema
   (create-tool {:name \"weather\"
                 :description \"Gets weather\"
                 :params-schema {:location s/Str
                                (s/optional-key :units) s/Str}
                 :fn get-weather})
   
   ;; Com Malli
   (create-tool {:name \"database\"
                 :description \"Queries database\"
                 :params-schema [:map
                                [:query :string]
                                [:limit {:optional true} :int]]
                 :fn query-db})
   
   ;; Especificando tipo explicitamente
   (create-tool {:name \"custom\"
                 :schema-type :spec  ; ou :schema ou :malli
                 :params-schema my-schema
                 :fn my-fn})"
  [{:keys [name description params-schema result-schema
           schema-type fn]
    :or {schema-type :auto}}]

  (let [;; Criar providers
        params-provider (when params-schema
                          (if (= schema-type :auto)
                            (create-schema-provider params-schema)
                            (create-schema-provider params-schema schema-type)))

        result-provider (when result-schema
                          (if (= schema-type :auto)
                            (create-schema-provider result-schema)
                            (create-schema-provider result-schema schema-type)))

        ;; Criar função com validação/coerção
        validated-fn (fn [params]
                       (let [;; Coerce e valida input
                             coerced (if params-provider
                                       (p/coerce params-provider params)
                                       params)

                            ;; Executa função
                             result (fn coerced)]

                        ;; Valida output se especificado
                         (if result-provider
                           (p/validate result-provider result)
                           result)))

        ;; Gerar JSON Schema para LangChain4j
        json-schema (when params-provider
                      (p/to-json-schema params-provider))]

    {:name name
     :description description
     :specification (-> (ToolSpecification/builder)
                        (.name name)
                        (.description description)
                        (.parameters json-schema)
                        (.build))
     :executor-fn validated-fn
     :params-schema params-schema
     :result-schema result-schema
     :schema-type (if (= schema-type :auto)
                    (detect-schema-type params-schema)
                    schema-type)}))

;; ============================================================================
;; EXEMPLOS DE USO COMPLETO
;; ============================================================================

(comment
  ;; 1. Definir specs para Clojure Spec
  (spec/def ::expression string?)
  (spec/def ::precision int?)
  (spec/def ::calculator-params
    (spec/keys :req-un [::expression]
               :opt-un [::precision]))

  ;; 2. Definir schemas para Plumatic Schema  
  (s/defschema WeatherParams
    {:location s/Str
     (s/optional-key :units) (s/enum "C" "F" "K")})

  ;; 3. Definir schemas para Malli
  (def DatabaseParams
    [:map
     [:query :string]
     [:limit {:optional true :default 10}
      [:int {:min 1 :max 100}]]])

  ;; 4. Criar tools com diferentes bibliotecas
  (def calculator-tool
    (create-tool {:name "calculator"
                  :description "Math calculations"
                  :params-schema ::calculator-params ; Spec
                  :fn (fn [{:keys [expression]}]
                        (eval (read-string expression)))}))

  (def weather-tool
    (create-tool {:name "weather"
                  :description "Weather info"
                  :params-schema WeatherParams ; Schema
                  :fn (fn [{:keys [location units]}]
                        {:temp 22 :location location})}))

  (def database-tool
    (create-tool {:name "database"
                  :description "Query DB"
                  :params-schema DatabaseParams ; Malli
                  :fn (fn [{:keys [query limit]}]
                        {:results [] :count 0})}))

  ;; 5. Todas funcionam da mesma forma!
  ((:executor-fn calculator-tool) {:expression "(+ 1 2)"})
  ((:executor-fn weather-tool) {:location "SP" :units "C"})
  ((:executor-fn database-tool) {:query "SELECT *" :limit 5}))
