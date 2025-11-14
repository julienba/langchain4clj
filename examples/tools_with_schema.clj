(ns examples.tools-with-schema
  "Exemplo de tools usando Plumatic Schema em vez de Malli"
  (:require [langchain4clj :as llm]
            [langchain4clj.agents :as agents]
            [schema.core :as s]
            [schema.coerce :as coerce])
  (:import [dev.langchain4j.agent.tool ToolSpecification]
           [dev.langchain4j.model.output.structured JsonSchema]))

;; ============================================================================
;; CONVERSOR: Schema → JSON Schema para LangChain4j
;; ============================================================================

(defn schema->json-schema
  "Converte Plumatic Schema para JSON Schema do LangChain4j"
  [schema]
  (cond
    ;; Tipos primitivos
    (= schema s/Str) {:type "string"}
    (= schema s/Num) {:type "number"}
    (= schema s/Int) {:type "integer"}
    (= schema s/Bool) {:type "boolean"}
    (= schema s/Any) {:type "any"}

    ;; Enums
    (instance? schema.core.EnumSchema schema)
    {:type "string"
     :enum (vec (.vs ^schema.core.EnumSchema schema))}

    ;; Arrays
    (vector? schema)
    {:type "array"
     :items (schema->json-schema (first schema))}

    ;; Maps/Objects
    (map? schema)
    (let [properties (reduce-kv
                      (fn [m k v]
                        (let [key (if (s/optional-key? k)
                                    (name (:k k))
                                    (name k))]
                          (assoc m key (schema->json-schema v))))
                      {}
                      schema)
          required (vec (keep #(when-not (s/optional-key? %)
                                 (name %))
                              (keys schema)))]
      {:type "object"
       :properties properties
       :required required})

    ;; Default
    :else {:type "any"}))

;; ============================================================================
;; DEFININDO SCHEMAS COM PLUMATIC SCHEMA
;; ============================================================================

;; Schema para Calculator Tool
(s/defschema CalculatorParams
  {:expression s/Str
   (s/optional-key :precision) s/Int})

(s/defschema CalculatorResult
  {:result s/Num
   :expression s/Str})

;; Schema para Weather Tool
(s/defschema WeatherParams
  {:location s/Str
   (s/optional-key :units) (s/enum "celsius" "fahrenheit" "kelvin")
   (s/optional-key :detailed) s/Bool})

(s/defschema WeatherResult
  {:location s/Str
   :temperature s/Num
   :units s/Str
   :conditions s/Str
   :humidity s/Int
   (s/optional-key :wind-speed) s/Num
   (s/optional-key :pressure) s/Num})

;; Schema para Database Tool
(s/defschema DatabaseParams
  {:query s/Str
   (s/optional-key :limit) s/Int
   (s/optional-key :offset) s/Int})

(s/defschema DatabaseResult
  {:query s/Str
   :results [s/Any]
   :count s/Int
   :execution-time-ms s/Num})

;; ============================================================================
;; CRIANDO TOOLS COM SCHEMA
;; ============================================================================

(defn create-tool-with-schema
  "Cria uma tool specification com validação via Schema"
  [{:keys [name description params-schema result-schema fn]}]
  (let [;; Converter para JSON Schema
        json-schema (schema->json-schema params-schema)

        ;; Criar coercer para validação e coerção automática
        input-coercer (coerce/coercer params-schema coerce/json-coercion-matcher)

        ;; Validar resultado se schema fornecido
        output-validator (when result-schema
                           (s/validator result-schema))

        ;; Wrapper com validação completa
        validated-fn (fn [params]
                      ;; Coerce e valida input
                       (let [coerced-input (input-coercer params)]
                         (when (s/error? coerced-input)
                           (throw (ex-info "Invalid input parameters"
                                           {:error (s/explain coerced-input)
                                            :params params})))

                        ;; Executa função
                         (let [result (fn coerced-input)]
                          ;; Valida output se schema definido
                           (when output-validator
                             (try
                               (output-validator result)
                               (catch Exception e
                                 (throw (ex-info "Invalid output from tool"
                                                 {:error (.getMessage e)
                                                  :result result})))))
                           result)))]

    {:name name
     :description description
     :specification (-> (ToolSpecification/builder)
                        (.name name)
                        (.description description)
                        (.parameters json-schema)
                        (.build))
     :executor-fn validated-fn
     :params-schema params-schema
     :result-schema result-schema}))

;; ============================================================================
;; IMPLEMENTANDO AS TOOLS
;; ============================================================================

(def calculator-tool
  (create-tool-with-schema
   {:name "calculator"
    :description "Performs mathematical calculations with optional precision"
    :params-schema CalculatorParams
    :result-schema CalculatorResult
    :fn (s/fn calculate :- CalculatorResult
          [params :- CalculatorParams]
          (let [{:keys [expression precision]} params
                result (eval (read-string expression))
                rounded (if precision
                          (/ (Math/round (* result (Math/pow 10 precision)))
                             (Math/pow 10 precision))
                          result)]
            {:result rounded
             :expression expression}))}))

(def weather-tool
  (create-tool-with-schema
   {:name "weather"
    :description "Gets weather information for a location"
    :params-schema WeatherParams
    :result-schema WeatherResult
    :fn (s/fn get-weather :- WeatherResult
          [{:keys [location units detailed]
            :or {units "celsius" detailed false}} :- WeatherParams]
          (let [base-temp 20
                temp (+ base-temp (- (rand-int 20) 10))
                converted-temp (case units
                                 "fahrenheit" (+ 32 (* temp 1.8))
                                 "kelvin" (+ temp 273.15)
                                 temp)]
            (cond-> {:location location
                     :temperature converted-temp
                     :units units
                     :conditions (rand-nth ["sunny" "cloudy" "rainy" "stormy"])
                     :humidity (+ 40 (rand-int 40))}
              detailed (assoc :wind-speed (rand-int 30)
                              :pressure (+ 990 (rand-int 40))))))}))

(def database-tool
  (create-tool-with-schema
   {:name "database"
    :description "Query the database"
    :params-schema DatabaseParams
    :result-schema DatabaseResult
    :fn (s/fn query-db :- DatabaseResult
          [{:keys [query limit offset]
            :or {limit 10 offset 0}} :- DatabaseParams]
           ;; Simulação - em produção seria query real
          {:query query
           :results (vec (for [i (range offset (min (+ offset limit) 50))]
                           {:id i
                            :name (str "Record-" i)
                            :value (rand-int 1000)}))
           :count (min limit (- 50 offset))
           :execution-time-ms (+ 10 (rand-int 90))})}))

;; ============================================================================
;; USO COM SCHEMA ANNOTATIONS
;; ============================================================================

;; Alternativa: Definir funções com schema annotations diretamente
(s/defn ^:tool calculate-tax :- s/Num
  "Calculates tax for a given amount and rate"
  [amount :- s/Num
   rate :- s/Num]
  (* amount rate))

(s/defn ^:tool format-currency :- s/Str
  "Formats a number as currency"
  [amount :- s/Num
   currency :- (s/enum "USD" "EUR" "BRL")]
  (case currency
    "USD" (str "$" (format "%.2f" amount))
    "EUR" (str "€" (format "%.2f" amount))
    "BRL" (str "R$ " (format "%.2f" amount))))

;; Função para extrair tool spec de s/defn anotada
(defn extract-schema-tool [fn-var]
  (let [meta (meta fn-var)
        schema-meta (:schema meta)
        input-schema (-> schema-meta :input-schema first) ; pega primeiro arg
        output-schema (:output-schema schema-meta)]

    (create-tool-with-schema
     {:name (name fn-var)
      :description (:doc meta)
      :params-schema input-schema
      :result-schema output-schema
      :fn @fn-var})))

;; ============================================================================
;; EXEMPLO DE USO COMPLETO
;; ============================================================================

(defn demo-schema-tools []
  (println "\n=== DEMO: Tools com Plumatic Schema ===\n")

  ;; Testar calculator
  (println "1. Calculator Tool:")
  (let [result ((:executor-fn calculator-tool)
                {:expression "(+ 10 (* 3.14159 2))"
                 :precision 2})]
    (println "   Input: (+ 10 (* 3.14159 2)) with precision 2")
    (println "   Result:" result))

  ;; Testar weather
  (println "\n2. Weather Tool:")
  (let [result ((:executor-fn weather-tool)
                {:location "São Paulo"
                 :units "celsius"
                 :detailed true})]
    (println "   Location: São Paulo")
    (println "   Result:" result))

  ;; Testar database
  (println "\n3. Database Tool:")
  (let [result ((:executor-fn database-tool)
                {:query "SELECT * FROM users"
                 :limit 5})]
    (println "   Query: SELECT * FROM users LIMIT 5")
    (println "   Results:" (:count result) "records in"
             (:execution-time-ms result) "ms"))

  ;; Demonstrar validação
  (println "\n4. Schema Validation:")
  (try
    ((:executor-fn weather-tool)
     {:location "Tokyo"
      :units "invalid-unit"}) ; Vai falhar - enum validation
    (catch Exception e
      (println "   ✓ Validation working:" (.getMessage e))))

  ;; Demonstrar coerção
  (println "\n5. Automatic Coercion:")
  (let [result ((:executor-fn database-tool)
                {:query "SELECT * FROM products"
                 :limit "20" ; String será coercido para int
                 :offset "5"})]
    (println "   String '20' coerced to int:" (type (:count result))))

  (println "\n=== Schema Benefits Demonstrated ===")
  (println "✓ Type validation")
  (println "✓ Automatic coercion")
  (println "✓ Enum validation")
  (println "✓ Optional keys")
  (println "✓ Clear error messages"))

;; Para executar:
;; (demo-schema-tools)

;; ============================================================================
;; INTEGRAÇÃO COM LANGCHAIN4J
;; ============================================================================

(defn create-agent-with-schema-tools []
  (let [tools [calculator-tool weather-tool database-tool]

        ;; Extrair specifications
        tool-specs (map :specification tools)

        ;; Criar ChatRequest com tools
        model (llm/create-model {:provider :openai
                                 :api-key (System/getenv "OPENAI_API_KEY")
                                 :model "gpt-4"})]

    ;; Em produção, isso seria encapsulado em uma função helper
    {:model model
     :tools tools
     :tool-specs tool-specs}))
