(ns nandoolle.langchain4clj.resilience
  "Provider failover and circuit breaker for high availability.
  
  This namespace provides automatic failover between LLM providers when
  the primary provider fails. It includes:
  - Automatic retry with configurable delay
  - Fallback to backup providers
  - Circuit breaker to prevent cascading failures
  - Error classification (recoverable vs non-recoverable)
  
  Example usage:
  
    (require '[nandoolle.langchain4clj.core :as core])
    (require '[nandoolle.langchain4clj.resilience :as resilience])
    
    ;; Create models with circuit breakers
    (def openai (resilience/create-resilient-model 
                  (core/create-model {:provider :openai :api-key \"...\"})))
    
    (def anthropic (resilience/create-resilient-model
                     (core/create-model {:provider :anthropic :api-key \"...\"})))
    
    ;; Use failover model
    (def model (resilience/with-failover openai anthropic))
    (core/chat model \"Hello\")  ; Falls back to anthropic if openai fails"
  (:require [nandoolle.langchain4clj.core :as core]
            [nandoolle.langchain4clj.constants :as const]
            [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import [dev.langchain4j.model.chat ChatModel]
           [dev.langchain4j.model.chat.request ChatRequest]
           [dev.langchain4j.model.chat.response ChatResponse]))

;; ============================================================================
;; Error Classification
;; ============================================================================

(defn- retryable-error?
  "Returns true if the error justifies retrying on the SAME provider.
  
  Examples: rate limits, timeouts, temporary service unavailability."
  [^Exception exception]
  (let [msg (str (.getMessage exception) " " (.getClass exception))]
    (or
     ;; Rate limiting
     (str/includes? msg "429")
     (str/includes? msg "rate limit")
     (str/includes? msg "too many requests")

     ;; Temporary unavailability
     (str/includes? msg "503")
     (str/includes? msg "Service Unavailable")
     (str/includes? msg "temporarily unavailable")

     ;; Timeouts
     (str/includes? msg "timeout")
     (str/includes? msg "timed out")
     (str/includes? msg "SocketTimeoutException"))))

(defn- recoverable-error?
  "Returns true if the error justifies trying the NEXT provider.
  
  Examples: authentication failures, model not found, persistent connection issues."
  [^Exception exception]
  (let [msg (str (.getMessage exception) " " (.getClass exception))]
    (or
     ;; Authentication/Authorization
     (str/includes? msg "401")
     (str/includes? msg "Unauthorized")
     (str/includes? msg "Invalid API key")
     (str/includes? msg "authentication")

     (str/includes? msg "403")
     (str/includes? msg "Forbidden")

     ;; Resource not found
     (str/includes? msg "404")
     (str/includes? msg "not found")
     (str/includes? msg "model not found")

     ;; Connection issues
     (str/includes? msg "connection")
     (str/includes? msg "ConnectException")
     (str/includes? msg "network")
     (str/includes? msg "unreachable"))))

(defn- non-recoverable-error?
  "Returns true if the error should be immediately thrown to the user.
  
  Examples: invalid input, quota exceeded permanently."
  [^Exception exception]
  (let [msg (str (.getMessage exception) " " (.getClass exception))]
    (or
     ;; Bad request - user's fault
     (str/includes? msg "400")
     (str/includes? msg "Bad Request")
     (str/includes? msg "invalid")

     ;; Quota/billing issues
     (str/includes? msg "quota")
     (str/includes? msg "billing")
     (str/includes? msg "payment"))))

;; ============================================================================
;; Retry Logic
;; ============================================================================

(defn- retry-provider
  "Attempts to call a provider with retry logic.
  
  Returns:
  - The response if successful
  - nil if all retries exhausted or error is recoverable (try next provider)
  - throws if error is non-recoverable"
  [provider message-or-request max-retries delay-ms chat-fn]
  (loop [attempt 0
         _last-error nil]
    (let [result (try
                   {:success true
                    :value (chat-fn provider message-or-request)}
                   (catch Exception e
                     {:success false
                      :error e}))]
      (if (:success result)
        ;; Success - return the value
        (do
          (when (> attempt 0)
            (log/info "Provider call succeeded after" attempt "retries"))
          (:value result))

        ;; Error - handle based on type
        (let [e (:error result)]
          (cond
            ;; Non-recoverable error - throw immediately
            (non-recoverable-error? e)
            (do
              (log/error "Non-recoverable error from provider:" (.getMessage e))
              (throw e))

            ;; Retryable error and we have retries left
            (and (retryable-error? e)
                 (< attempt max-retries))
            (do
              (log/warn "Retry attempt" (inc attempt) "/" max-retries "after retryable error:" (.getMessage e))
              (Thread/sleep delay-ms)
              (recur (inc attempt) e))

            ;; Retryable but retries exhausted - try next provider
            (retryable-error? e)
            (do
              (log/debug "All retries exhausted after retryable error, trying next provider")
              nil)

            ;; Recoverable error - try next provider
            (recoverable-error? e)
            (do
              (log/debug "Recoverable error from provider, trying next:" (.getMessage e))
              nil)

            ;; Unknown error - treat as non-recoverable
            :else
            (do
              (log/error "Unknown error from provider:" (.getMessage e))
              (throw e))))))))

;; ============================================================================
;; Provider Chain Logic
;; ============================================================================

(defn- try-providers-with-retry
  "Tries each provider in sequence with retry logic.
  
  For simple String chat."
  [providers ^String message max-retries delay-ms]
  (loop [remaining-providers providers
         providers-tried []
         provider-index 0]
    (if-let [provider (first remaining-providers)]
      (do
        (when (> provider-index 0)
          (log/warn "Failing over to fallback provider" provider-index))
        (if-let [result (retry-provider provider message max-retries delay-ms
                                        (fn [p m] (.chat ^ChatModel p m)))]
          result
          (recur (rest remaining-providers)
                 (conj providers-tried provider)
                 (inc provider-index))))

      ;; All providers failed
      (do
        (log/error "All" (count providers) "providers failed")
        (throw (ex-info "All providers failed"
                        {:providers-count (count providers)
                         :providers-tried (count providers-tried)}))))))

(defn- try-providers-with-retry-request
  "Tries each provider in sequence with retry logic.
  
  For ChatRequest (advanced features)."
  [providers ^ChatRequest request max-retries delay-ms]
  (loop [remaining-providers providers
         providers-tried []
         provider-index 0]
    (if-let [provider (first remaining-providers)]
      (do
        (when (> provider-index 0)
          (log/warn "Failing over to fallback provider" provider-index))
        (if-let [result (retry-provider provider request max-retries delay-ms
                                        (fn [p r] (.chat ^ChatModel p r)))]
          result
          (recur (rest remaining-providers)
                 (conj providers-tried provider)
                 (inc provider-index))))

      ;; All providers failed
      (do
        (log/error "All" (count providers) "providers failed")
        (throw (ex-info "All providers failed"
                        {:providers-count (count providers)
                         :providers-tried (count providers-tried)}))))))

;; ============================================================================
;; Circuit Breaker (Phase 2)
;; ============================================================================

(defn- create-circuit-breaker-state
  "Creates initial circuit breaker state for a provider."
  []
  (atom {:state :closed
         :failure-count 0
         :success-count 0
         :last-failure-time nil
         :total-calls 0
         :total-failures 0}))

(defn- circuit-breaker-half-open?
  "Returns true if circuit breaker should transition to half-open."
  [state-atom timeout-ms]
  (let [state @state-atom]
    (and (= :open (:state state))
         (:last-failure-time state)
         (>= (- (System/currentTimeMillis) (:last-failure-time state))
             timeout-ms))))

(defn- record-success!
  "Records a successful call and updates circuit breaker state."
  [state-atom success-threshold]
  (let [old-state (:state @state-atom)
        new-state (swap! state-atom
                         (fn [state]
                           (let [new-success-count (inc (:success-count state))]
                             (case (:state state)
                               :closed
                               (-> state
                                   (assoc :failure-count 0)
                                   (update :total-calls inc))

                               :half-open
                               (if (>= new-success-count success-threshold)
                                 ;; Enough successes - close the circuit
                                 {:state :closed
                                  :failure-count 0
                                  :success-count 0
                                  :last-failure-time nil
                                  :total-calls (inc (:total-calls state))
                                  :total-failures (:total-failures state)}
                                 ;; Not enough yet - keep counting
                                 (-> state
                                     (assoc :success-count new-success-count)
                                     (update :total-calls inc)))

                               :open
                               (update state :total-calls inc)))))]
    ;; Log state transitions
    (when (and (= :half-open old-state) (= :closed (:state new-state)))
      (log/info "Circuit breaker transitioned to Closed state"))
    new-state))

(defn- record-failure!
  "Records a failed call and updates circuit breaker state."
  [state-atom failure-threshold]
  (let [old-state (:state @state-atom)
        new-state (swap! state-atom
                         (fn [state]
                           (let [new-failure-count (inc (:failure-count state))]
                             (case (:state state)
                               :closed
                               (if (>= new-failure-count failure-threshold)
                                 ;; Too many failures - open the circuit
                                 {:state :open
                                  :failure-count new-failure-count
                                  :success-count 0
                                  :last-failure-time (System/currentTimeMillis)
                                  :total-calls (inc (:total-calls state))
                                  :total-failures (inc (:total-failures state))}
                                 ;; Not enough yet - keep counting
                                 (-> state
                                     (assoc :failure-count new-failure-count)
                                     (update :total-calls inc)
                                     (update :total-failures inc)))

                               :half-open
                               ;; Any failure in half-open - back to open
                               {:state :open
                                :failure-count new-failure-count
                                :success-count 0
                                :last-failure-time (System/currentTimeMillis)
                                :total-calls (inc (:total-calls state))
                                :total-failures (inc (:total-failures state))}

                               :open
                               (-> state
                                   (update :total-calls inc)
                                   (update :total-failures inc))))))]
    ;; Log state transitions
    (when (and (= :closed old-state) (= :open (:state new-state)))
      (log/warn "Circuit breaker opened due to failure threshold"))
    (when (and (= :half-open old-state) (= :open (:state new-state)))
      (log/warn "Circuit breaker reopened after failed test"))
    new-state))

(defn- transition-to-half-open!
  "Transitions circuit breaker from open to half-open."
  [state-atom]
  (let [old-state (:state @state-atom)
        new-state (swap! state-atom
                         (fn [state]
                           (if (= :open (:state state))
                             (assoc state
                                    :state :half-open
                                    :success-count 0
                                    :failure-count 0)
                             state)))]
    (when (and (= :open old-state) (= :half-open (:state new-state)))
      (log/info "Circuit breaker transitioned to Half-Open state for testing"))
    new-state))

(defn- should-allow-request?
  "Determines if a request should be allowed based on circuit breaker state."
  [state-atom timeout-ms]
  (let [state @state-atom]
    (case (:state state)
      :closed true
      :half-open true
      :open (when (circuit-breaker-half-open? state-atom timeout-ms)
              (transition-to-half-open! state-atom)
              true))))

(defn- retry-provider-with-circuit-breaker
  "Attempts to call a provider with retry logic and circuit breaker.
  
  Returns:
  - The response if successful
  - nil if circuit breaker is open or errors are recoverable
  - throws if error is non-recoverable"
  [provider message-or-request max-retries delay-ms chat-fn
   circuit-breaker-state cb-config]

  (let [{:keys [failure-threshold success-threshold timeout-ms]} cb-config]
    ;; Check if circuit breaker allows the request
    (if-not (should-allow-request? circuit-breaker-state timeout-ms)
      ;; Circuit is open - skip this provider
      (do
        (log/warn "Circuit breaker is open, skipping provider")
        nil)

      ;; Circuit allows request - try with retry logic
      (loop [attempt 0
             _last-error nil]
        (let [result (try
                       {:success true
                        :value (chat-fn provider message-or-request)}
                       (catch Exception e
                         {:success false
                          :error e}))]

          (if (:success result)
            ;; Success - record and return
            (do
              (when (> attempt 0)
                (log/info "Provider call succeeded after" attempt "retries"))
              (record-success! circuit-breaker-state success-threshold)
              (:value result))

            ;; Error - handle based on type
            (let [e (:error result)]
              (cond
                ;; Non-recoverable error - record failure and throw
                (non-recoverable-error? e)
                (do
                  (log/error "Non-recoverable error from provider:" (.getMessage e))
                  (record-failure! circuit-breaker-state failure-threshold)
                  (throw e))

                ;; Retryable error and we have retries left
                (and (retryable-error? e)
                     (< attempt max-retries))
                (do
                  (log/warn "Retry attempt" (inc attempt) "/" max-retries "after retryable error:" (.getMessage e))
                  (Thread/sleep delay-ms)
                  (recur (inc attempt) e))

                ;; Retryable but retries exhausted - record failure, try next
                (retryable-error? e)
                (do
                  (log/debug "All retries exhausted after retryable error, trying next provider")
                  (record-failure! circuit-breaker-state failure-threshold)
                  nil)

                ;; Recoverable error - record failure, try next provider
                (recoverable-error? e)
                (do
                  (log/debug "Recoverable error from provider, trying next:" (.getMessage e))
                  (record-failure! circuit-breaker-state failure-threshold)
                  nil)

                ;; Unknown error - record failure and throw
                :else
                (do
                  (log/error "Unknown error from provider:" (.getMessage e))
                  (record-failure! circuit-breaker-state failure-threshold)
                  (throw e))))))))))

(defn- try-providers-with-circuit-breaker
  "Tries each provider with circuit breaker support.
  
  For simple String chat."
  [providers message max-retries delay-ms circuit-breakers cb-config]
  (loop [remaining-providers providers
         remaining-breakers circuit-breakers
         providers-tried []
         provider-index 0]
    (if-let [provider (first remaining-providers)]
      (let [breaker (first remaining-breakers)]
        (when (> provider-index 0)
          (log/warn "Failing over to fallback provider" provider-index))
        (if-let [result (retry-provider-with-circuit-breaker
                         provider message max-retries delay-ms
                         (fn [p m] (.chat ^ChatModel p m))
                         breaker cb-config)]
          result
          (recur (rest remaining-providers)
                 (rest remaining-breakers)
                 (conj providers-tried provider)
                 (inc provider-index))))

      ;; All providers failed or were skipped
      (do
        (log/error "All" (count providers) "providers failed or unavailable")
        (throw (ex-info "All providers failed or unavailable"
                        {:providers-count (count providers)
                         :providers-tried (count providers-tried)}))))))

(defn- try-providers-with-circuit-breaker-request
  "Tries each provider with circuit breaker support.
  
  For ChatRequest (advanced features)."
  [providers request max-retries delay-ms circuit-breakers cb-config]
  (loop [remaining-providers providers
         remaining-breakers circuit-breakers
         providers-tried []
         provider-index 0]
    (if-let [provider (first remaining-providers)]
      (let [breaker (first remaining-breakers)]
        (when (> provider-index 0)
          (log/warn "Failing over to fallback provider" provider-index))
        (if-let [result (retry-provider-with-circuit-breaker
                         provider request max-retries delay-ms
                         (fn [p r] (.chat ^ChatModel p r))
                         breaker cb-config)]
          result
          (recur (rest remaining-providers)
                 (rest remaining-breakers)
                 (conj providers-tried provider)
                 (inc provider-index))))

      ;; All providers failed or were skipped
      (do
        (log/error "All" (count providers) "providers failed or unavailable")
        (throw (ex-info "All providers failed or unavailable"
                        {:providers-count (count providers)
                         :providers-tried (count providers-tried)}))))))

;; ============================================================================
;; Public API
;; ============================================================================

(defn create-resilient-model
  "Creates a resilient ChatModel wrapper with automatic failover.
  
  When the primary provider fails, automatically tries fallback providers in order.
  Includes retry logic and optional circuit breaker for production resilience.
  
  Configuration:
  - :primary - Primary ChatModel instance (required)
  - :fallbacks - Vector of fallback ChatModel instances (optional, default [])
  - :max-retries - Max retries per provider on retryable errors (optional, default 2)
  - :retry-delay-ms - Delay between retries in milliseconds (optional, default 1000)
  
  Circuit Breaker (optional, Phase 2):
  - :circuit-breaker? - Enable circuit breaker (optional, default false)
  - :failure-threshold - Failures before opening circuit (optional, default 5)
  - :success-threshold - Successes before closing from half-open (optional, default 2)
  - :timeout-ms - Time in open before half-open (optional, default 60000)
  
  Error Handling:
  - Retryable errors (429, 503, timeout) → retry on same provider
  - Recoverable errors (401, 404, connection) → try next provider
  - Non-recoverable errors (400, quota) → throw immediately
  
  Circuit Breaker States (when enabled):
  - Closed: Normal operation, requests pass through
  - Open: Too many failures, skip provider temporarily
  - Half-Open: Testing recovery, limited requests allowed
  
  Example without circuit breaker (Phase 1):
  
    (def model
      (create-resilient-model
        {:primary openai-model
         :fallbacks [anthropic-model ollama-model]
         :max-retries 2
         :retry-delay-ms 1000}))
  
  Example with circuit breaker (Phase 2):
  
    (def model
      (create-resilient-model
        {:primary openai-model
         :fallbacks [anthropic-model ollama-model]
         :max-retries 2
         :retry-delay-ms 1000
         :circuit-breaker? true
         :failure-threshold 5
         :success-threshold 2
         :timeout-ms 60000}))
    
    ;; Use like normal model
    (chat model \"Hello\")
    ;; Tries: OpenAI (with retries + CB) → Anthropic (with retries + CB) → Ollama
  
  Returns a ChatModel instance that can be used with `chat` function."
  [{:keys [primary fallbacks max-retries retry-delay-ms
           circuit-breaker? failure-threshold success-threshold timeout-ms]
    :or {fallbacks []
         max-retries const/default-max-retries
         retry-delay-ms const/default-retry-delay-ms
         circuit-breaker? false
         failure-threshold const/default-failure-threshold
         success-threshold const/default-success-threshold
         timeout-ms const/default-circuit-breaker-timeout-ms}}]

  {:pre [(some? primary)
         (>= max-retries 0)
         (> retry-delay-ms 0)
         (> failure-threshold 0)
         (> success-threshold 0)
         (> timeout-ms 0)]}

  (let [all-providers (cons primary fallbacks)]

    (if circuit-breaker?
      ;; Phase 2: With circuit breaker
      (let [circuit-breakers (vec (repeatedly (count all-providers)
                                              create-circuit-breaker-state))
            cb-config {:failure-threshold failure-threshold
                       :success-threshold success-threshold
                       :timeout-ms timeout-ms}]
        (reify ChatModel
          ;; Simple string chat
          (^String chat [_ ^String message]
            (try-providers-with-circuit-breaker all-providers message
                                                max-retries retry-delay-ms
                                                circuit-breakers cb-config))

          ;; ChatRequest chat (for advanced features like tools, JSON mode)
          (^ChatResponse chat [_ ^ChatRequest request]
            (try-providers-with-circuit-breaker-request all-providers request
                                                        max-retries retry-delay-ms
                                                        circuit-breakers cb-config))))

      ;; Phase 1: Without circuit breaker (backward compatible)
      (reify ChatModel
        ;; Simple string chat
        (^String chat [_ ^String message]
          (try-providers-with-retry all-providers message
                                    max-retries retry-delay-ms))

        ;; ChatRequest chat (for advanced features like tools, JSON mode)
        (^ChatResponse chat [_ ^ChatRequest request]
          (try-providers-with-retry-request all-providers request
                                            max-retries retry-delay-ms))))))
