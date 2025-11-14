(ns examples.complete-assistant-demo
  "Demonstrates all three solved problems:
   1. Memory Management
   2. Tool Execution Loop  
   3. Structured Output"
  (:require [nandoolle.langchain4clj.core :as llm]
            [nandoolle.langchain4clj.assistant :as assistant]
            [nandoolle.langchain4clj.tools :as tools]
            [nandoolle.langchain4clj.structured :as structured]
            [clojure.spec.alpha :as s]))

;; ============================================================================
;; 1. MEMORY MANAGEMENT DEMO
;; ============================================================================

(defn demo-memory-management []
  (println "\n=== MEMORY MANAGEMENT AUTOMÁTICO ===\n")

  ;; Criar assistant com memória
  (let [assistant (assistant/create-assistant
                   {:model (llm/create-model {:provider :openai
                                              :api-key "demo-key"})
                    :memory (assistant/create-memory {:max-messages 10})
                    :system-message "You are a helpful assistant"})]

    ;; Primeira conversa
    (println "User: My name is João and I'm from Brazil")
    (println "Assistant:" (assistant "My name is João and I'm from Brazil"))
    (println)

    ;; Segunda conversa - LEMBRA do contexto!
    (println "User: What's my name?")
    (println "Assistant:" (assistant "What's my name?"))
    ;; -> "Your name is João"
    (println)

    ;; Terceira conversa - LEMBRA de múltiplos fatos!
    (println "User: Where am I from?")
    (println "Assistant:" (assistant "Where am I from?"))
    ;; -> "You're from Brazil"

    (println "\n✅ Memória mantida automaticamente entre chamadas!")
    (println "✅ Remove mensagens antigas quando passa do limite!")
    (println "✅ Zero gerenciamento manual!")))

;; ============================================================================
;; 2. TOOL EXECUTION LOOP DEMO
;; ============================================================================

(defn demo-tool-execution []
  (println "\n=== TOOL EXECUTION LOOP AUTOMÁTICO ===\n")

  ;; Definir tools
  (def calculator-tool
    (tools/create-tool
     {:name "calculator"
      :description "Performs mathematical calculations"
      :params-schema {:expression string?}
      :fn (fn [{:keys [expression]}]
            (println (str "  [TOOL CALLED: calculator(" expression ")]"))
            (eval (read-string expression)))}))

  (def weather-tool
    (tools/create-tool
     {:name "weather"
      :description "Gets current weather for a location"
      :params-schema {:location string?}
      :fn (fn [{:keys [location]}]
            (println (str "  [TOOL CALLED: weather(" location ")]"))
            {:temp 22 :conditions "sunny" :location location})}))

  ;; Criar assistant com tools
  (let [assistant (assistant/create-assistant
                   {:model (llm/create-model {:provider :openai})
                    :tools [calculator-tool weather-tool]})]

    ;; Pergunta que requer tool
    (println "User: What's 15 * 23 + 42?")
    (println "Assistant:" (assistant "What's 15 * 23 + 42?"))
    ;; AUTOMATICAMENTE:
    ;; 1. Model detecta que precisa calculator
    ;; 2. Chama calculator("15 * 23 + 42")
    ;; 3. Recebe resultado 387
    ;; 4. Formula resposta: "15 * 23 + 42 equals 387"
    (println)

    ;; Pergunta que requer múltiplos tools
    (println "User: What's the weather in SP and what's 100 celsius in fahrenheit?")
    (println "Assistant:" (assistant
                           "What's the weather in SP and what's 100 celsius in fahrenheit?"))
    ;; AUTOMATICAMENTE:
    ;; 1. Chama weather("SP")
    ;; 2. Chama calculator("100 * 9/5 + 32")  
    ;; 3. Combina resultados na resposta

    (println "\n✅ Tools executadas automaticamente!")
    (println "✅ Loop gerenciado pelo sistema!")
    (println "✅ Múltiplas iterações se necessário!")))

;; ============================================================================
;; 3. STRUCTURED OUTPUT DEMO
;; ============================================================================

;; Definir estruturas com nossa macro
(structured/defstructured Recipe
  {:name string?
   :servings int?
   :ingredients vector?
   :steps vector?
   :prep-time int?
   :cook-time int?})

(structured/defstructured TravelPlan
  {:destination string?
   :duration int?
   :budget number?
   :activities vector?
   :accommodations map?})

(defn demo-structured-output []
  (println "\n=== STRUCTURED OUTPUT AUTOMÁTICO ===\n")

  (let [model (llm/create-model {:provider :openai})]

    ;; Exemplo 1: Recipe
    (println "Getting structured recipe...")
    (let [recipe (get-recipe model "Create a recipe for lasagna")]
      (println "Parsed Recipe:")
      (println "  Name:" (:name recipe))
      (println "  Servings:" (:servings recipe))
      (println "  Ingredients:" (count (:ingredients recipe)) "items")
      (println "  Steps:" (count (:steps recipe)) "steps")
      (println "  Total time:" (+ (:prep-time recipe) (:cook-time recipe)) "minutes"))
    (println)

    ;; Exemplo 2: Travel Plan
    (println "Getting structured travel plan...")
    (let [plan (get-travelplan model "Plan a 5-day trip to Tokyo")]
      (println "Parsed Travel Plan:")
      (println "  Destination:" (:destination plan))
      (println "  Duration:" (:duration plan) "days")
      (println "  Budget:" (:budget plan))
      (println "  Activities:" (count (:activities plan)) "planned")
      (println "  Accommodation:" (:accommodations plan)))

    (println "\n✅ Parsing automático para Clojure data!")
    (println "✅ Validação com schemas!")
    (println "✅ Retry automático se parsing falhar!")))

;; ============================================================================
;; 4. TUDO JUNTO: Assistant Completo
;; ============================================================================

(defn demo-complete-assistant []
  (println "\n=== ASSISTANT COMPLETO: Memory + Tools + Structured ===\n")

  ;; Tools para o assistant
  (def search-tool
    (tools/create-tool
     {:name "search_flights"
      :description "Search for flights"
      :params-schema {:from string? :to string? :date string?}
      :fn (fn [{:keys [from to date]}]
            [{:flight "AA123" :price 500 :time "08:00"}
             {:flight "UA456" :price 450 :time "14:00"}])}))

  ;; Assistant com TUDO
  (let [assistant (assistant/create-assistant
                   {:model (llm/create-model {:provider :openai})
                    :tools [search-tool]
                    :memory (assistant/create-memory {:max-messages 20})
                    :system-message "You are a travel planning assistant"})

        ;; Com structured output
        structured-assistant (structured/with-structured-output
                               assistant
                               (fn [response]
                                ;; Parse para formato estruturado
                                 {:summary response
                                  :flights (re-seq #"[A-Z]{2}\d{3}" response)
                                  :total-cost (or (re-find #"\$(\d+)" response) "N/A")}))]

    ;; Conversa 1: Estabelece contexto (MEMORY)
    (println "User: I want to travel from São Paulo to New York next month")
    (println "Assistant:" (assistant
                           "I want to travel from São Paulo to New York next month"))
    (println)

    ;; Conversa 2: Usa contexto + TOOLS
    (println "User: Can you search for flights on March 15?")
    (let [response (structured-assistant
                    "Can you search for flights on March 15?")]
      (println "Structured Response:")
      (println "  Summary:" (subs (:summary response) 0 50) "...")
      (println "  Flights found:" (:flights response))
      (println "  Total cost:" (:total-cost response)))

    (println "\n✅ Memory: Lembra contexto (SP -> NY)")
    (println "✅ Tools: Busca voos automaticamente")
    (println "✅ Structured: Retorna dados parseados")))

;; ============================================================================
;; MAIN: Run all demos
;; ============================================================================

(defn -main []
  (println "\n" (apply str (repeat 60 "=")) "\n")
  (println "DEMONSTRAÇÃO COMPLETA: Assistant em Clojure")
  (println (apply str (repeat 60 "=")) "\n")

  (demo-memory-management)
  (demo-tool-execution)
  (demo-structured-output)
  (demo-complete-assistant)

  (println "\n" (apply str (repeat 60 "=")) "\n")
  (println "CONCLUSÃO:")
  (println "✅ Memory Management: RESOLVIDO")
  (println "✅ Tool Execution Loop: RESOLVIDO")
  (println "✅ Structured Output: RESOLVIDO")
  (println "\n🎉 Temos equivalência funcional ao AiServices!")
  (println "   Com a flexibilidade do Clojure!")
  (println (apply str (repeat 60 "="))))

;; Para executar:
;; (comment (-main))
