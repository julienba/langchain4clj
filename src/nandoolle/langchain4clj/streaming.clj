(ns nandoolle.langchain4clj.streaming
  "Streaming response support for LangChain4j models.
  
  Provides simple callback-based API for receiving tokens in real-time as they are generated.
  
  Example:
    (require '[nandoolle.langchain4clj.streaming :as streaming])
    
    (def model (streaming/create-streaming-model {:provider :openai
                                                   :api-key \"sk-...\"
                                                   :model \"gpt-4\"}))
    
    (streaming/stream-chat model \"Explain AI\"
      {:on-token (fn [token] (print token) (flush))
       :on-complete (fn [response] (println \"\\nDone!\"))
       :on-error (fn [error] (println \"Error:\" error))})"
  (:require [nandoolle.langchain4clj.macros :as macros]
            [nandoolle.langchain4clj.specs :as specs]
            [nandoolle.langchain4clj.constants :as const]
            [clojure.spec.alpha :as s])
  (:import [dev.langchain4j.model.chat StreamingChatModel]
           [dev.langchain4j.model.chat.response StreamingChatResponseHandler]
           [dev.langchain4j.model.openai OpenAiStreamingChatModel]
           [dev.langchain4j.model.anthropic AnthropicStreamingChatModel]
           [dev.langchain4j.model.ollama OllamaStreamingChatModel]
           ;; Note: GoogleAiGeminiStreamingChatModel and VertexAiGeminiStreamingChatModel
           ;; will be available in langchain4j 1.9.0+. For now, only OpenAI, Anthropic,
           ;; and Ollama streaming are supported.
           ;; [dev.langchain4j.model.googleai GoogleAiGeminiStreamingChatModel]
           ;; [dev.langchain4j.model.vertexai VertexAiGeminiStreamingChatModel]
           [java.time Duration]))

;; =============================================================================
;; Private Helpers
;; =============================================================================

(defn- duration-from-millis
  "Converts milliseconds to Java Duration"
  [millis]
  (Duration/ofMillis millis))

(defn- int-from-long
  "Converts Long to Integer for Java interop"
  [n]
  (when n (int n)))

(defn- create-handler
  "Creates Java StreamingChatResponseHandler from Clojure callbacks.
  
  Parameters:
  - :on-token (required) - (fn [token-string]) called for each token
  - :on-complete (optional) - (fn [response]) called when streaming completes
  - :on-error (optional) - (fn [throwable]) called on error"
  [{:keys [on-token on-complete on-error]}]
  (reify StreamingChatResponseHandler
    (onPartialResponse [_ token]
      (on-token token))

    (onCompleteResponse [_ response]
      (when on-complete
        (on-complete response)))

    (onError [_ error]
      (when on-error
        (on-error error)))))

;; =============================================================================
;; Builders for Streaming Models (using defbuilder)
;; =============================================================================

(macros/defbuilder build-openai-streaming-model
  (OpenAiStreamingChatModel/builder)
  {:api-key :apiKey
   :model :modelName
   :temperature :temperature
   :timeout [:timeout duration-from-millis]
   :max-tokens [:maxTokens int-from-long]
   :top-p :topP
   :log-requests :logRequests
   :log-responses :logResponses})

(macros/defbuilder build-anthropic-streaming-model
  (AnthropicStreamingChatModel/builder)
  {:api-key :apiKey
   :model :modelName
   :temperature :temperature
   :timeout [:timeout duration-from-millis]
   :max-tokens [:maxTokens int-from-long]
   :top-p :topP
   :top-k :topK})

(macros/defbuilder build-ollama-streaming-model
  (OllamaStreamingChatModel/builder)
  {:base-url :baseUrl
   :model :modelName
   :temperature :temperature
   :timeout [:timeout duration-from-millis]
   :top-k [:topK int-from-long]
   :top-p :topP
   :seed [:seed int-from-long]
   :num-predict [:numPredict int-from-long]
   :stop :stop
   :response-format :responseFormat
   :log-requests :logRequests
   :log-responses :logResponses
   :max-retries [:maxRetries int-from-long]})

;; TODO: Uncomment when langchain4j 1.9.0+ is released with Gemini streaming support
;; (macros/defbuilder build-google-ai-gemini-streaming-model
;;   (GoogleAiGeminiStreamingChatModel/builder)
;;   {:api-key :apiKey
;;    :model :modelName
;;    :temperature :temperature
;;    :timeout [:timeout duration-from-millis]
;;    :max-output-tokens :maxOutputTokens
;;    :top-p :topP
;;    :top-k :topK})

;; (macros/defbuilder build-vertex-ai-gemini-streaming-model
;;   (VertexAiGeminiStreamingChatModel/builder)
;;   {:project :project
;;    :location :location
;;    :model :modelName
;;    :temperature :temperature
;;    :max-output-tokens :maxOutputTokens
;;    :top-p :topP
;;    :top-k :topK})

;; =============================================================================
;; Public API - Model Creation
;; =============================================================================

(defmulti create-streaming-model
  "Creates a streaming chat model.
  
  Config keys (same as regular models):
  - :provider (required) - :openai, :anthropic, :google-ai-gemini, :vertex-ai-gemini
  - :api-key (required for most) - Provider API key
  - :model (optional) - Model name (defaults vary by provider)
  - :temperature (optional) - Sampling temperature (default 0.7)
  - :timeout (optional) - Timeout in milliseconds
  - :max-tokens (optional) - Maximum tokens to generate
  - :top-p (optional) - Nucleus sampling parameter
  - :top-k (optional) - Top-k sampling parameter (Gemini/Anthropic)
  
  Returns StreamingChatLanguageModel instance.
  
  Examples:
    ;; OpenAI
    (create-streaming-model {:provider :openai
                             :api-key \"sk-...\"
                             :model \"gpt-4\"})
    
    ;; Anthropic
    (create-streaming-model {:provider :anthropic
                             :api-key \"sk-ant-...\"
                             :model \"claude-3-5-sonnet-20241022\"})
    
    ;; Google AI Gemini
    (create-streaming-model {:provider :google-ai-gemini
                             :api-key \"AIza...\"
                             :model \"gemini-1.5-flash\"})
    
    ;; Vertex AI Gemini
    (create-streaming-model {:provider :vertex-ai-gemini
                             :project \"my-gcp-project\"
                             :location \"us-central1\"
                             :model \"gemini-1.5-pro\"})"
  :provider)

(defmethod create-streaming-model :openai
  [{:keys [api-key model temperature timeout max-tokens top-p log-requests log-responses]
    :or {model "gpt-4o-mini"
         temperature const/default-temperature}}]
  (build-openai-streaming-model
   (cond-> {:api-key api-key
            :model model
            :temperature temperature}
     timeout (assoc :timeout timeout)
     max-tokens (assoc :max-tokens max-tokens)
     top-p (assoc :top-p top-p)
     log-requests (assoc :log-requests log-requests)
     log-responses (assoc :log-responses log-responses))))

(defmethod create-streaming-model :anthropic
  [{:keys [api-key model temperature timeout max-tokens top-p top-k]
    :or {model "claude-3-5-sonnet-20241022"
         temperature const/default-temperature}}]
  (build-anthropic-streaming-model
   (cond-> {:api-key api-key
            :model model
            :temperature temperature}
     timeout (assoc :timeout timeout)
     max-tokens (assoc :max-tokens max-tokens)
     top-p (assoc :top-p top-p)
     top-k (assoc :top-k top-k))))

(defmethod create-streaming-model :ollama
  [{:keys [base-url model temperature timeout top-k top-p seed num-predict stop log-requests log-responses max-retries]
    :or {base-url "http://localhost:11434"
         model "llama3.1"
         temperature const/default-temperature}}]
  (build-ollama-streaming-model
   (cond-> {:base-url base-url
            :model model
            :temperature temperature}
     timeout (assoc :timeout timeout)
     top-k (assoc :top-k top-k)
     top-p (assoc :top-p top-p)
     seed (assoc :seed seed)
     num-predict (assoc :num-predict num-predict)
     stop (assoc :stop stop)
     log-requests (assoc :log-requests log-requests)
     log-responses (assoc :log-responses log-responses)
     max-retries (assoc :max-retries max-retries))))

;; TODO: Uncomment when langchain4j 1.9.0+ is released with Gemini streaming support
;; (defmethod create-streaming-model :google-ai-gemini
;;   [{:keys [api-key model temperature timeout max-output-tokens top-p top-k]
;;     :or {model "gemini-1.5-flash"
;;          temperature 0.7}}]
;;   (build-google-ai-gemini-streaming-model
;;    (cond-> {:api-key api-key
;;             :model model
;;             :temperature temperature}
;;      timeout (assoc :timeout timeout)
;;      max-output-tokens (assoc :max-output-tokens max-output-tokens)
;;      top-p (assoc :top-p top-p)
;;      top-k (assoc :top-k top-k))))

;; (defmethod create-streaming-model :vertex-ai-gemini
;;   [{:keys [project location model temperature max-output-tokens top-p top-k]
;;     :or {model "gemini-1.5-flash"
;;          location "us-central1"
;;          temperature 0.7}}]
;;   (build-vertex-ai-gemini-streaming-model
;;    (cond-> {:project project
;;             :location location
;;             :model model
;;             :temperature temperature}
;;      max-output-tokens (assoc :max-output-tokens max-output-tokens)
;;      top-p (assoc :top-p top-p)
;;      top-k (assoc :top-k top-k))))

;; =============================================================================
;; Public API - Streaming Chat
;; =============================================================================

(defn stream-chat
  "Streams chat response with callbacks.
  
  Parameters:
  - model: StreamingChatLanguageModel instance
  - message: String message to send
  - opts: Map with callbacks and optional chat options
  
  Required callbacks:
  - :on-token (fn [token-string]) - Called for each token as it arrives
  
  Optional callbacks:
  - :on-complete (fn [response]) - Called when streaming completes with Response object
  - :on-error (fn [throwable]) - Called on error with Throwable
  
  Optional chat options (same as regular chat):
  - :temperature - Sampling temperature
  - :max-tokens - Maximum tokens to generate
  - :system-message - System message
  - :top-p - Nucleus sampling
  - And other provider-specific options
  
  Examples:
    ;; Basic usage
    (stream-chat model \"Explain AI\"
      {:on-token (fn [token] (print token) (flush))
       :on-complete (fn [resp] (println \"\\nDone!\"))})
    
    ;; With error handling
    (stream-chat model \"Hello\"
      {:on-token println
       :on-complete #(println \"Complete:\")
       :on-error #(println \"Error:\" %)})
    
    ;; With chat options
    (stream-chat model \"Tell me a story\"
      {:on-token println
       :on-complete #(println \"Done!\")
       :temperature 0.9
       :max-tokens 500})"
  [^StreamingChatModel model message {:keys [on-token on-complete on-error]}]
  {:pre [(some? model)
         (string? message)
         (fn? on-token)]}
  (let [handler (create-handler {:on-token on-token
                                 :on-complete on-complete
                                 :on-error on-error})
        ;; TODO: Support chat options (temperature, max-tokens, etc)
        ;; For now, just use simple chat(String, handler)
        ]
    (.chat model message handler)))

(comment
  ;; Example usage
  (require '[nandoolle.langchain4clj.streaming :as streaming])

  ;; Create streaming model
  (def model (streaming/create-streaming-model
              {:provider :openai
               :api-key (System/getenv "OPENAI_API_KEY")
               :model "gpt-4o-mini"}))

  ;; Stream chat
  (streaming/stream-chat model "Say hello in 3 words"
                         {:on-token (fn [token] (print token) (flush))
                          :on-complete (fn [_resp] (println "\n✓ Done!"))
                          :on-error (fn [err] (println "\n✗ Error:" (.getMessage err)))})

  ;; Accumulate response
  (let [accumulated (atom "")
        result (promise)]
    (streaming/stream-chat model "Count to 5"
                           {:on-token (fn [token]
                                        (print token)
                                        (flush)
                                        (swap! accumulated str token))
                            :on-complete (fn [resp]
                                           (deliver result {:text @accumulated
                                                            :response resp}))
                            :on-error (fn [err]
                                        (deliver result {:error err}))})
    @result))

;; ============================================================================
;; Specs for Public API
;; ============================================================================

(s/fdef create-streaming-model
  :args (s/cat :config ::specs/model-config)
  :ret ::specs/streaming-chat-model)

(s/fdef stream-chat
  :args (s/cat :model ::specs/streaming-chat-model
               :message string?
               :callbacks ::specs/streaming-callbacks)
  :ret nil?)
