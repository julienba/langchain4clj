(ns langchain4clj.assistant
  "Clojure equivalent of LangChain4j's AiServices.
   Provides high-level abstractions for common patterns."
  (:require [langchain4clj.core :as core]
            [langchain4clj.tools :as tools]
            [langchain4clj.macros :as macros]
            [clojure.string :as str]
            [clojure.data.json :as json])
  (:import [dev.langchain4j.data.message UserMessage SystemMessage ToolExecutionResultMessage]
           [dev.langchain4j.model.chat ChatModel]))

;; ============================================================================
;; Memory Management (equivalente a ChatMemory)
;; ============================================================================

(defprotocol ChatMemory
  (add-message! [this message])
  (get-messages [this])
  (clear! [this]))

(defn create-memory
  "Creates a simple message window memory"
  [{:keys [max-messages] :or {max-messages 10}}]
  (let [messages (atom [])]
    (reify ChatMemory
      (add-message! [_ message]
        (swap! messages
               (fn [msgs]
                 (let [updated (conj msgs message)]
                   (if (> (count updated) max-messages)
                     (vec (take-last max-messages updated))
                     updated)))))

      (get-messages [_]
        @messages)

      (clear! [_]
        (reset! messages [])))))

;; ============================================================================
;; Template Processing (equivalente a @UserMessage com placeholders)
;; ============================================================================

(defn process-template
  "Processes a template string with variables.
   Example: 'Hello {{name}}' with {:name 'World'} -> 'Hello World'"
  [template variables]
  (reduce-kv
   (fn [s k v]
     (str/replace s (str "{{" (name k) "}}") (str v)))
   template
   variables))

;; ============================================================================
;; Tool Execution Loop
;; ============================================================================

(defn execute-tool-calls
  "Executes tool calls from AI response"
  [tool-requests registered-tools]
  (mapv (fn [request]
          (let [tool-name (.name request)
                tool (tools/find-tool tool-name registered-tools)]
            (if tool
              (let [;; .arguments returns JSON string, parse it
                    args-json (.arguments request)
                    args (json/read-str args-json :key-fn keyword)
                    result (tools/execute-tool tool args)]
                (ToolExecutionResultMessage/from request (pr-str result)))
              (ToolExecutionResultMessage/from request
                                               (str "Tool not found: " tool-name)))))
        tool-requests))

(defn chat-with-tools
  "Handles a complete conversation with automatic tool execution"
  [{:keys [model messages tools max-iterations]
    :or {max-iterations 10}}]
  (loop [current-messages messages
         iteration 0]
    (if (>= iteration max-iterations)
      {:error "Max iterations reached"
       :messages current-messages}

      (let [;; Call chat with tools - returns ChatResponse
            response (core/chat model current-messages
                                {:tools (map :specification tools)})
            ;; Extract aiMessage from ChatResponse
            ai-message (.aiMessage response)
            tool-requests (.toolExecutionRequests ai-message)]

        (if (empty? tool-requests)
          ;; No tools needed, return final response
          {:result (.text ai-message)
           :messages (conj current-messages ai-message)}

          ;; Execute tools and continue
          (let [tool-results (execute-tool-calls tool-requests tools)
                updated-messages (-> current-messages
                                     (conj ai-message)
                                     (into tool-results))]
            (recur updated-messages (inc iteration))))))))

;; ============================================================================
;; Assistant Builder (similar to AiServices)
;; ============================================================================

(defn create-assistant
  "Creates an assistant with model, tools, and memory.
   
   Options:
   - :model - ChatLanguageModel (required)
   - :tools - Vector of tools (optional)
   - :memory - ChatMemory instance (optional)
   - :system-message - System prompt (optional)
   - :max-iterations - Max tool execution loops (default 10)"
  [{:keys [model tools memory system-message max-iterations]
    :or {memory (create-memory {})
         max-iterations 10}}]

  {:pre [(instance? ChatModel model)]}

  (let [tools (or tools [])]

    ;; Return a function that acts as the assistant
    (fn assistant
      ([user-input]
       (assistant user-input {}))

      ([user-input {:keys [template-vars clear-memory?]}]
       ;; Clear memory if requested
       (when clear-memory?
         (clear! memory))

       ;; Process template if variables provided
       (let [processed-input (if template-vars
                               (process-template user-input template-vars)
                               user-input)

             ;; Build message list for the model
             ;; IMPORTANT: System message must come FIRST, before any conversation history
             existing-messages (get-messages memory)
             user-message (UserMessage. processed-input)
             messages (cond-> []
                        ;; Add system message first (if provided)
                        system-message (conj (SystemMessage. system-message))
                        ;; Then add conversation history from memory
                        true (into existing-messages)
                        ;; Finally add new user message
                        true (conj user-message))

             ;; Track how many messages were in memory before this call
             original-count (count existing-messages)

             ;; Execute chat with tools
             result (chat-with-tools
                     {:model model
                      :messages messages
                      :tools tools
                      :max-iterations max-iterations})]

         ;; Update memory with only NEW messages (user input + AI response + any tool messages)
         ;; Skip the existing messages that were already in memory
         ;; Also skip the system message if it was added (it's not part of conversation history)
         (let [new-messages (drop original-count (:messages result))
               ;; Filter out system messages from what we save to memory
               messages-to-save (remove #(instance? SystemMessage %) new-messages)]
           (doseq [msg messages-to-save]
             (add-message! memory msg)))

         ;; Return result
         (:result result))))))

;; ============================================================================
;; Threading-First Assistant API (New in v0.2.0)
;; ============================================================================

(defn assistant
  "Creates an assistant with threading-first support.
  
  Simplified version of create-assistant that's more idiomatic.
  Returns a configuration map that can be composed with with-* functions,
  then finalized with build-assistant.
  
  Args:
    model - ChatModel instance
  
  Examples:
  
  ;; Simple assistant
  (def my-assistant
    (-> {:model model}
        assistant
        build-assistant))
  
  ;; With threading
  (def my-assistant
    (-> {:model model}
        assistant
        (with-tools [calculator weather])
        (with-memory (memory {:max-messages 20}))
        (with-system-message \"You are helpful\")
        build-assistant))
  
  ;; Composable configuration
  (def base-config {:model model})
  
  (def my-assistant
    (-> base-config
        (merge {:max-iterations 15})
        assistant
        (with-tools my-tools)
        build-assistant))"
  [config]
  (macros/with-defaults config
    {:memory (create-memory {})
     :max-iterations 10
     :tools []}))

(defn with-tools
  "Adds tools to an assistant config. Use in threading.
  
  Example:
  (-> {:model model}
      assistant
      (with-tools [calculator weather-tool]))"
  [config tools]
  (assoc config :tools tools))

(defn with-memory
  "Sets the memory for an assistant config. Use in threading.
  
  Example:
  (-> {:model model}
      assistant
      (with-memory (memory {:max-messages 20})))"
  [config memory-instance]
  (assoc config :memory memory-instance))

(defn with-system-message
  "Sets the system message for an assistant config. Use in threading.
  
  Example:
  (-> {:model model}
      assistant
      (with-system-message \"You are a helpful coding assistant\"))"
  [config system-msg]
  (assoc config :system-message system-msg))

(defn with-max-iterations
  "Sets max tool execution iterations. Use in threading.
  
  Example:
  (-> {:model model}
      assistant
      (with-max-iterations 20))"
  [config max-iter]
  (assoc config :max-iterations max-iter))

(defn memory
  "Creates a memory instance. Alias for create-memory with threading support.
  
  Example:
  (memory {:max-messages 20})"
  [opts]
  (create-memory opts))

(defn build-assistant
  "Finalizes an assistant configuration and returns the assistant function.
  This is the last step in the threading pipeline.
  
  Takes a config map and returns an assistant function.
  
  Example:
  (def my-assistant
    (-> {:model model}
        assistant
        (with-tools [calculator])
        build-assistant))
  
  ;; Use the assistant
  (my-assistant \"What is 2+2?\")"
  [config]
  (create-assistant config))

;; ============================================================================
;; Structured Output Support
;; ============================================================================

(defn with-structured-output
  "Wraps an assistant to parse responses into structured data"
  [assistant parser-fn]
  (fn [& args]
    (let [response (apply assistant args)]
      (parser-fn response))))

;; ============================================================================
;; Example Usage
;; ============================================================================

(comment
  ;; Create an assistant similar to AiServices
  (def my-assistant
    (create-assistant
     {:model (core/create-model {:provider :openai
                                 :api-key (System/getenv "OPENAI_API_KEY")})
      :tools [(tools/create-tool
               {:name "calculator"
                :description "Performs calculations"
                :params-schema {:expression :string}
                :fn (fn [{:keys [expression]}]
                      (eval (read-string expression)))})]
      :system-message "You are a helpful assistant"
      :memory (create-memory {:max-messages 20})}))

  ;; Use it like AiServices
  (my-assistant "What is 2 + 2?")
  ;; -> "2 + 2 equals 4"

  ;; With templates
  (my-assistant "Translate '{{text}}' to {{language}}"
                {:template-vars {:text "Hello"
                                 :language "Spanish"}})
  ;; -> "The translation of 'Hello' to Spanish is 'Hola'"

  ;; Structured output
  (def recipe-assistant
    (with-structured-output
      my-assistant
      (fn [response]
        {:name (re-find #"Name: (.+)" response)
         :ingredients (re-seq #"- (.+)" response)})))

  (recipe-assistant "Create a recipe for pasta carbonara")
  ;; -> {:name "Pasta Carbonara" :ingredients ["spaghetti" "eggs" ...]}
  )
