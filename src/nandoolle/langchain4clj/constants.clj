(ns nandoolle.langchain4clj.constants
  "Default configuration constants for LangChain4Clj.
  
  All values are configurable by clients - these are just sensible defaults.
  Users can override any of these by passing values in their configuration maps.")

;; ============================================================================
;; Model Configuration Defaults
;; ============================================================================

(def default-temperature
  "Default temperature for LLM sampling.
  Higher values (closer to 1.0) make output more random/creative.
  Lower values (closer to 0.0) make output more deterministic."
  0.7)

(def default-timeout-ms
  "Default timeout for model requests in milliseconds.
  Prevents requests from hanging indefinitely."
  60000) ; 60 seconds

;; ============================================================================
;; Retry & Validation Defaults
;; ============================================================================

(def default-max-attempts
  "Default maximum attempts for retry logic and validation loops.
  Used in:
  - structured output validation (chat-with-validation)
  - tool execution retries (with-retry)"
  3)

;; ============================================================================
;; Circuit Breaker Defaults
;; ============================================================================

(def default-failure-threshold
  "Number of consecutive failures before opening the circuit breaker.
  Once open, requests fail fast without attempting the call."
  5)

(def default-success-threshold
  "Number of consecutive successes needed to close circuit from half-open state.
  Gradual recovery ensures the service is truly healthy."
  3)

(def default-circuit-breaker-timeout-ms
  "Time circuit breaker stays open before entering half-open state (ms).
  Allows failing service time to recover."
  60000) ; 60 seconds

;; ============================================================================
;; Retry Backoff Defaults  
;; ============================================================================

(def default-retry-delay-ms
  "Initial delay between retry attempts in milliseconds.
  Often multiplied by attempt number for exponential backoff."
  1000) ; 1 second

(def default-max-retries
  "Default maximum number of retries for transient failures."
  2)
