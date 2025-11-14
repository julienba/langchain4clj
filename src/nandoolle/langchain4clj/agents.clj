(ns nandoolle.langchain4clj.agents
  "Idiomatic Clojure wrapper for LangChain4j agent capabilities.
   Provides protocols and functions to work with agents without imposing
   any specific implementations or prompts."
  (:require [nandoolle.langchain4clj :as llm])
  (:import [dev.langchain4j.data.message UserMessage SystemMessage AiMessage]
           [dev.langchain4j.memory.chat MessageWindowChatMemory]
           [java.util ArrayList]))

;; ============================================================================
;; Protocols
;; ============================================================================

(defprotocol Agent
  "Protocol for agent abstraction"
  (process [this input context]
    "Process input with optional context and return result")
  (get-name [this]
    "Return the agent's name")
  (get-description [this]
    "Return the agent's description")
  (get-model [this]
    "Return the underlying chat model"))

(defprotocol MemoryProvider
  "Protocol for memory/context management"
  (add-message [this message]
    "Add a message to memory")
  (get-messages [this]
    "Get all messages from memory")
  (clear-memory [this]
    "Clear all messages from memory"))

(defprotocol Pipeline
  "Protocol for agent pipelines/chains"
  (execute [this input]
    "Execute the pipeline with the given input")
  (add-agent [this agent]
    "Add an agent to the pipeline")
  (get-agents [this]
    "Get all agents in the pipeline"))

(defprotocol Tool
  "Protocol for tools that agents can use"
  (execute-tool [this args]
    "Execute the tool with given arguments")
  (get-tool-info [this]
    "Get the tool's information"))

;; ============================================================================
;; Memory Implementation
;; ============================================================================

(defrecord ChatMemory [^MessageWindowChatMemory memory]
  MemoryProvider
  (add-message [_ message]
    (.add memory message))

  (get-messages [_]
    (vec (.messages memory)))

  (clear-memory [_]
    (.clear memory)))

(defn create-memory
  "Create a new chat memory with optional max messages"
  ([]
   (create-memory 10))
  ([max-messages]
   (->ChatMemory (MessageWindowChatMemory/withMaxMessages max-messages))))

;; ============================================================================
;; Base Agent Implementation
;; ============================================================================

(defrecord BaseAgent [name description model system-prompt memory]
  Agent
  (process [_this input context]
    (let [messages (ArrayList.)
          system-msg (when system-prompt
                       (SystemMessage. system-prompt))
          user-msg (UserMessage. (str input))]

      ;; Add system message if present
      (when system-msg
        (.add messages system-msg))

      ;; Add memory messages if available
      (when memory
        (doseq [msg (get-messages memory)]
          (.add messages msg)))

      ;; Add context if provided
      (when context
        (.add messages (SystemMessage. (str "Context: " (pr-str context)))))

      ;; Add current user message
      (.add messages user-msg)

      ;; Get response from model using .chat (changed from .generate in LangChain4j 1.0.0)
      (let [chat-response (.chat model messages)
            ai-message (.aiMessage chat-response)]
        ;; Store interaction in memory if available
        (when memory
          (add-message memory user-msg)
          (add-message memory ai-message))

        ;; Return the response text
        (.text ai-message))))

  (get-name [_] name)
  (get-description [_] description)
  (get-model [_] model))

(defn create-agent
  "Create a new agent with the given configuration.
   Config keys:
   - :name - Agent name
   - :description - Agent description  
   - :system-prompt - Optional system prompt
   - :memory - Optional memory provider
   - All other keys are passed to create-model"
  [{:keys [name description system-prompt memory]
    :or {memory nil}
    :as config}]
  (let [model-config (dissoc config :name :description :system-prompt :memory)
        model (llm/create-model model-config)]
    (->BaseAgent name description model system-prompt memory)))

;; ============================================================================
;; Agent Pipeline/Chain Implementation
;; ============================================================================

(defrecord AgentPipeline [agents]
  Pipeline
  (execute [_ input]
    (reduce (fn [current-input agent]
              (process agent current-input nil))
            input
            agents))

  (add-agent [this agent]
    (update this :agents conj agent))

  (get-agents [_] agents))

(defn create-pipeline
  "Create a new agent pipeline"
  ([]
   (->AgentPipeline []))
  ([agents]
   (->AgentPipeline (vec agents))))

(defn chain
  "Chain multiple agents together in a pipeline"
  [& agents]
  (create-pipeline agents))

;; ============================================================================
;; Parallel Agent System
;; ============================================================================

(defrecord ParallelAgentSystem [agents reducer-fn]
  Pipeline
  (execute [_ input]
    (let [futures (mapv #(future (process % input nil)) agents)
          results (mapv deref futures)]
      (if reducer-fn
        (reducer-fn results)
        results)))

  (add-agent [this agent]
    (update this :agents conj agent))

  (get-agents [_] agents))

(defn create-parallel-system
  "Create a system where agents process in parallel.
   Options:
   - :agents - Vector of agents
   - :reducer - Optional function to reduce results"
  [{:keys [agents reducer]
    :or {reducer nil}}]
  (->ParallelAgentSystem (vec agents) reducer))

(defn parallel-process
  "Process input with multiple agents in parallel"
  [agents input context]
  (let [futures (mapv #(future (process % input context)) agents)]
    (mapv deref futures)))

;; ============================================================================
;; Collaborative Agent System
;; ============================================================================

(defrecord CollaborativeSystem [agents coordinator shared-context]
  Pipeline
  (execute [_ input]
    (let [context (atom (or shared-context {}))
          results (atom {})]

      ;; Each agent processes the input with shared context
      (doseq [agent agents]
        (let [agent-name (get-name agent)
              result (process agent input @context)]
          (swap! results assoc agent-name result)
          (swap! context assoc agent-name result)))

      ;; Coordinator processes results if present
      (if coordinator
        (let [coordinator-input {:input input
                                 :results @results
                                 :context @context}]
          (process coordinator coordinator-input nil))
        @results)))

  (add-agent [this agent]
    (update this :agents conj agent))

  (get-agents [_] agents))

(defn create-collaborative-system
  "Create a collaborative multi-agent system.
   Config keys:
   - :agents - Vector of agents
   - :coordinator - Optional coordinator agent
   - :shared-context - Initial shared context map"
  [{:keys [agents coordinator shared-context]
    :or {shared-context {}}}]
  (->CollaborativeSystem (vec agents) coordinator shared-context))

;; ============================================================================
;; Tool Support
;; ============================================================================

(defrecord SimpleTool [name description fn]
  Tool
  (execute-tool [_ args]
    (fn args))
  (get-tool-info [_]
    {:name name :description description}))

(defn create-tool
  "Create a simple tool that can be used by agents.
   Config keys:
   - :name - Tool name
   - :description - Tool description
   - :fn - Function to execute"
  [{:keys [name description fn]}]
  (->SimpleTool name description fn))

;; ============================================================================
;; Agent with Tools
;; ============================================================================

(defrecord ToolEnabledAgent [base-agent tools tool-selector]
  Agent
  (process [_this input context]
    ;; Let tool-selector decide if/which tool to use
    (if-let [tool (when tool-selector (tool-selector input tools context))]
      (let [result (execute-tool tool input)]
        ;; Optionally pass tool result through base agent for formatting
        (if (:pass-through-agent context)
          (process base-agent result context)
          result))
      (process base-agent input context)))

  (get-name [_]
    (get-name base-agent))

  (get-description [_]
    (get-description base-agent))

  (get-model [_]
    (get-model base-agent)))

(defn create-agent-with-tools
  "Create an agent that can use tools.
   Config keys:
   - :agent - Base agent or agent config
   - :tools - Vector of tools
   - :tool-selector - Function (input tools context) -> tool or nil"
  [{:keys [agent tools tool-selector]}]
  (let [base-agent (if (satisfies? Agent agent)
                     agent
                     (create-agent agent))]
    (->ToolEnabledAgent base-agent (vec tools) tool-selector)))

;; ============================================================================
;; Router Implementation
;; ============================================================================

(defrecord RouterAgent [agents route-fn default-agent]
  Agent
  (process [_this input context]
    (let [selected-agent (or (route-fn input context agents)
                             default-agent
                             (first agents))]
      (if selected-agent
        (process selected-agent input context)
        (throw (ex-info "No agent available for routing"
                        {:input input :context context})))))

  (get-name [_] "Router")
  (get-description [_] "Routes requests to appropriate agents")
  (get-model [_] nil))

(defn create-router
  "Create a router that directs requests to appropriate agents.
   Config keys:
   - :agents - Vector of agents to route to
   - :route-fn - Function (input context agents) -> agent
   - :default-agent - Optional default agent"
  [{:keys [agents route-fn default-agent]}]
  (->RouterAgent (vec agents) route-fn default-agent))

;; ============================================================================
;; Utility Functions
;; ============================================================================

(defn with-retry
  "Execute agent processing with retry logic"
  [agent input context max-retries]
  (loop [attempt 1]
    (let [result (try
                   {:success true
                    :value (process agent input context)}
                   (catch Exception e
                     {:success false
                      :error e}))]
      (if (:success result)
        (:value result)
        (if (< attempt max-retries)
          (do
            (Thread/sleep (* 1000 attempt)) ; Exponential backoff
            (recur (inc attempt)))
          (throw (:error result)))))))

(defn with-memory
  "Wrap an agent with memory management"
  [agent memory]
  (reify Agent
    (process [_ input context]
      (add-message memory (UserMessage. (str input)))
      (let [response (process agent input context)]
        (add-message memory (AiMessage. response))
        response))
    (get-name [_] (get-name agent))
    (get-description [_] (get-description agent))
    (get-model [_] (get-model agent))))

(defn compose
  "Compose multiple agents into a single agent using a composition function.
   The comp-fn receives a vector of results from all agents."
  [agents comp-fn]
  (reify Agent
    (process [_ input context]
      (let [results (mapv #(process % input context) agents)]
        (comp-fn results)))
    (get-name [_] "Composed")
    (get-description [_] "Composed agent")
    (get-model [_] nil)))

(defn map-reduce-agents
  "Map input through agents and reduce results.
   Config keys:
   - :agents - Vector of agents
   - :map-fn - Function to map over input before sending to agents
   - :reduce-fn - Function to reduce agent results"
  [{:keys [agents map-fn reduce-fn]
    :or {map-fn identity
         reduce-fn (fn [results] results)}}]
  (reify Agent
    (process [_ input context]
      (let [mapped-input (map-fn input)
            results (mapv #(process % mapped-input context) agents)]
        (reduce-fn results)))
    (get-name [_] "MapReduce")
    (get-description [_] "Map-reduce agent system")
    (get-model [_] nil)))

;; ============================================================================
;; Message Building Utilities
;; ============================================================================

(defn build-messages
  "Build a message list for LLM interaction.
   Config keys:
   - :system - System message text
   - :user - User message text
   - :assistant - Assistant message text
   - :history - Vector of previous messages
   - :context - Additional context to include"
  [{:keys [system user assistant history context]}]
  (let [messages (ArrayList.)]
    (when system
      (.add messages (SystemMessage. system)))
    (when history
      (doseq [msg history]
        (.add messages msg)))
    (when context
      (.add messages (SystemMessage. (str "Context: " (pr-str context)))))
    (when user
      (.add messages (UserMessage. user)))
    (when assistant
      (.add messages (AiMessage. assistant)))
    messages))

;; ============================================================================
;; Specs for Public API (basic coverage)
;; ============================================================================
;; Note: Requires adding [nandoolle.langchain4clj.specs :as specs] and
;; [clojure.spec.alpha :as s] to ns :require
