(ns examples.tools-example
  "Exemplo completo de como tools funcionariam com a API proposta"
  (:require [langchain4clj :as llm]
            [langchain4clj.agents :as agents]
            [langchain4clj.tools :as tools] ; namespace proposto
            [malli.core :as m])
  (:import [dev.langchain4j.agent.tool ToolSpecification]
           [dev.langchain4j.agent.tool ToolExecutionRequest]
           [dev.langchain4j.data.message ToolExecutionResultMessage]))

;; ============================================================================
;; EXEMPLO COMPLETO: Sistema de Assistente com Tools
;; ============================================================================

;; -----------------------------------------------------------------------------
;; 1. DEFININDO AS TOOLS - Três opções diferentes
;; -----------------------------------------------------------------------------

;; OPÇÃO A: Definição direta com especificação completa
(def calculator-tool
  (tools/create-tool-specification
   {:name "calculator"
    :description "Performs mathematical calculations on expressions"
    :parameters {:type "object"
                 :properties {:expression {:type "string"
                                           :description "Mathematical expression to evaluate"}}
                 :required ["expression"]}
    :executor-fn (fn [{:keys [expression]}]
                   (try
                     {:result (eval (read-string expression))}
                     (catch Exception e
                       {:error (.getMessage e)})))}))

;; OPÇÃO B: Usando metadata em funções (mais idiomático)
(defn ^{:tool {:description "Gets current weather for a location"
               :parameters {:location {:type :string
                                       :description "City name or coordinates"}
                            :units {:type :string
                                    :enum ["celsius" "fahrenheit"]
                                    :default "celsius"
                                    :description "Temperature units"}}}}
  get-weather
  [{:keys [location units]}]
  ;; Simulação - em produção, chamaria API real
  (let [temp (+ 15 (rand-int 20))
        temp-converted (if (= units "fahrenheit")
                         (+ 32 (* temp 1.8))
                         temp)]
    {:location location
     :temperature temp-converted
     :units units
     :conditions (rand-nth ["sunny" "cloudy" "rainy" "partly cloudy"])
     :humidity (+ 40 (rand-int 40))}))

;; Extrair tool spec do metadata
(def weather-tool
  (tools/extract-tool-spec #'get-weather))

;; OPÇÃO C: Usando Malli schema
(def database-tool-def
  {:name "query-database"
   :description "Query customer database for information"
   :schema [:map
            [:query [:string {:description "SQL query to execute"}]]
            [:limit {:optional true}
             [:int {:min 1 :max 100 :default 10
                    :description "Maximum number of results"}]]]
   :fn (fn [{:keys [query limit]}]
         ;; Simulação de query
         {:query query
          :results [{:id 1 :name "Alice" :status "active"}
                    {:id 2 :name "Bob" :status "pending"}]
          :count 2
          :limit (or limit 10)})})

(def database-tool
  (tools/malli->tool-spec database-tool-def))

;; -----------------------------------------------------------------------------
;; 2. CRIANDO AGENTE COM TOOLS
;; -----------------------------------------------------------------------------

(defn create-assistant-with-tools []
  (let [;; Coleção de todas as tools
        tools [calculator-tool weather-tool database-tool]

        ;; Criar modelo base com configuração
        model-config {:provider :openai
                      :api-key (System/getenv "OPENAI_API_KEY")
                      :model "gpt-4"
                      :temperature 0.7}

        ;; Criar modelo com suporte a function calling
        model (tools/create-chat-model-with-tools
               {:model-config model-config
                :tools tools})]

    ;; Retornar agente configurado
    {:model model
     :tools tools
     :memory (agents/create-memory 20)}))

;; -----------------------------------------------------------------------------
;; 3. FLUXO DE CONVERSAÇÃO COM TOOLS
;; -----------------------------------------------------------------------------

(defn process-with-tools
  "Processa uma mensagem do usuário, lidando com tool calls automaticamente"
  [{:keys [model tools memory]} user-message]

  (println "\n🤖 User:" user-message)

  ;; Construir mensagens incluindo histórico
  (let [messages (agents/build-messages
                  {:history (when memory
                              (agents/get-messages memory))
                   :user user-message})

        ;; Primeira chamada ao modelo
        response (llm/chat-with-tools model messages tools)]

    ;; Verificar se o modelo quer usar tools
    (if-let [tool-calls (:tool-execution-requests response)]
      (do
        (println "🔧 Model wants to use tools:"
                 (map :name tool-calls))

        ;; Executar cada tool requisitada
        (let [tool-results
              (for [tool-call tool-calls]
                (let [tool-name (:name tool-call)
                      tool-args (:arguments tool-call)
                      tool (first (filter #(= tool-name (:name %)) tools))
                      executor-fn (:executor-fn tool)]

                  (println (str "  📍 Executing: " tool-name))
                  (println (str "     Args: " tool-args))

                  (let [result (executor-fn tool-args)]
                    (println (str "     Result: " result))

                    ;; Criar mensagem de resultado
                    (tools/create-tool-result-message
                     {:tool-call tool-call
                      :result result}))))]

          ;; Segunda chamada com resultados das tools
          (println "🔄 Sending tool results back to model...")

          (let [messages-with-results
                (concat messages
                        [(:ai-message response)]
                        tool-results)

                final-response
                (llm/chat model messages-with-results)]

            ;; Salvar na memória
            (when memory
              (agents/add-message memory (:user-message user-message))
              (agents/add-message memory (:ai-message final-response)))

            (println "🤖 Assistant:" (:content final-response))
            final-response)))

      ;; Sem tool calls - resposta direta
      (do
        (when memory
          (agents/add-message memory (:user-message user-message))
          (agents/add-message memory (:ai-message response)))

        (println "🤖 Assistant:" (:content response))
        response))))

;; -----------------------------------------------------------------------------
;; 4. EXEMPLO DE USO
;; -----------------------------------------------------------------------------

(defn run-example []
  (println "=" (apply str (repeat 60 "=")))
  (println "EXEMPLO: Assistente com Multiple Tools")
  (println "=" (apply str (repeat 60 "=")))

  (let [assistant (create-assistant-with-tools)]

    ;; Exemplo 1: Usando calculator
    (process-with-tools assistant
                        "What's the square root of 144 plus 25% of 200?")

    ;; Exemplo 2: Usando weather
    (process-with-tools assistant
                        "What's the weather like in São Paulo? Should I bring an umbrella?")

    ;; Exemplo 3: Usando database
    (process-with-tools assistant
                        "Show me all active customers in our database")

    ;; Exemplo 4: Combinando múltiplas tools
    (process-with-tools assistant
                        "If the temperature in Tokyo is above 20°C, calculate how many hours 
       of sunlight we'd need to generate 500kWh with a 50kW solar panel")

    ;; Exemplo 5: Sem tools (apenas conversação)
    (process-with-tools assistant
                        "What are the benefits of renewable energy?")))

;; -----------------------------------------------------------------------------
;; 5. ALTERNATIVA: Controle Manual (compatibilidade com versão atual)
;; -----------------------------------------------------------------------------

(defn manual-tool-selection-example []
  (println "\n=== Modo Manual (retrocompatível) ===\n")

  ;; Ainda suporta o modo atual com tool-selector
  (let [tools [calculator-tool weather-tool]

        ;; Função de seleção manual
        tool-selector (fn [input tools context]
                        (cond
                          (re-find #"(?i)calculate|math|sqrt" input)
                          (first (filter #(= "calculator" (:name %)) tools))

                          (re-find #"(?i)weather|temperature|rain" input)
                          (first (filter #(= "get-weather" (:name %)) tools))

                          :else nil))

        ;; Criar agente com seleção manual
        agent (agents/create-agent-with-tools
               {:agent (agents/create-agent
                        {:name "ManualAgent"
                         :provider :openai
                         :api-key (System/getenv "OPENAI_API_KEY")})
                :tools tools
                :tool-selector tool-selector})]

    (println "Resultado:"
             (agents/process agent
                             "Calculate the square root of 169"
                             nil))))

;; -----------------------------------------------------------------------------
;; 6. HELPERS E UTILITIES PROPOSTAS
;; -----------------------------------------------------------------------------

(comment
  ;; Namespace tools proposto teria:

  ;; Converter schemas Clojure para JSON Schema
  (tools/clojure-schema->json-schema
   {:type :map
    :properties {:name :string
                 :age :int}})

  ;; Validar argumentos antes de executar
  (tools/validate-args tool-spec args)

  ;; Batch execution de múltiplas tools
  (tools/execute-batch tools requests)

  ;; Tool composition
  (tools/compose-tools
   tool1
   tool2
   (fn [result1 result2] ...))

  ;; Tool middleware (logging, metrics, etc)
  (tools/with-logging tool)
  (tools/with-metrics tool)
  (tools/with-retry tool {:max-attempts 3})

  ;; Tool discovery/registry
  (tools/register! :calculator calculator-tool)
  (tools/get-tool :calculator)
  (tools/list-available-tools))

;; Para executar:
;; (run-example)
