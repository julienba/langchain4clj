(ns streaming-demo
  "Interactive streaming chat demo"
  (:require [langchain4clj.streaming :as streaming]))

;; =============================================================================
;; Example 1: Basic Streaming
;; =============================================================================

(defn basic-streaming-example []
  (println "\n=== Example 1: Basic Streaming ===\n")

  (let [model (streaming/create-streaming-model
               {:provider :openai
                :api-key (or (System/getenv "OPENAI_API_KEY")
                             (throw (Exception. "OPENAI_API_KEY not set")))
                :model "gpt-4o-mini"})]

    (print "AI: ")
    (flush)

    (streaming/stream-chat model "Say hello in 5 words"
                           {:on-token (fn [token]
                                        (print token)
                                        (flush))
                            :on-complete (fn [response]
                                           (println "\n✓ Complete!")
                                           (println "Tokens used:" (-> response .tokenUsage .totalTokenCount)))
                            :on-error (fn [error]
                                        (println "\n✗ Error:" (.getMessage error)))})))

;; =============================================================================
;; Example 2: Accumulate Response
;; =============================================================================

(defn accumulate-response-example []
  (println "\n=== Example 2: Accumulate Response ===\n")

  (let [model (streaming/create-streaming-model
               {:provider :openai
                :api-key (System/getenv "OPENAI_API_KEY")
                :model "gpt-4o-mini"})
        accumulated (atom "")
        result (promise)]

    (print "AI: ")
    (flush)

    (streaming/stream-chat model "Count from 1 to 5"
                           {:on-token (fn [token]
                                        (print token)
                                        (flush)
                                        (swap! accumulated str token))
                            :on-complete (fn [response]
                                           (deliver result {:text @accumulated
                                                            :response response}))
                            :on-error (fn [error]
                                        (deliver result {:error error}))})

    (let [{:keys [text response error]} @result]
      (if error
        (println "\n✗ Error:" (.getMessage error))
        (do
          (println "\n\n✓ Full response accumulated:")
          (println "Text:" text)
          (println "Tokens:" (-> response .tokenUsage .totalTokenCount)))))))

;; =============================================================================
;; Example 3: Interactive Chat CLI
;; =============================================================================

(defn interactive-chat []
  (println "\n=== Example 3: Interactive Chat ===")
  (println "Type your messages (or 'exit' to quit)\n")

  (let [model (streaming/create-streaming-model
               {:provider :openai
                :api-key (System/getenv "OPENAI_API_KEY")
                :model "gpt-4o-mini"})]

    (loop []
      (print "\nYou: ")
      (flush)
      (when-let [input (read-line)]
        (when-not (= "exit" (clojure.string/lower-case input))
          (print "AI: ")
          (flush)

          (let [done? (promise)]
            (streaming/stream-chat model input
                                   {:on-token (fn [token]
                                                (print token)
                                                (flush))
                                    :on-complete (fn [_]
                                                   (println)
                                                   (deliver done? true))
                                    :on-error (fn [error]
                                                (println "\nError:" (.getMessage error))
                                                (deliver done? true))})
            @done?)

          (recur))))))

;; =============================================================================
;; Example 4: Multiple Providers
;; =============================================================================

(defn compare-providers-example []
  (println "\n=== Example 4: Compare Providers ===\n")

  (let [providers [{:name "OpenAI"
                    :config {:provider :openai
                             :api-key (System/getenv "OPENAI_API_KEY")
                             :model "gpt-4o-mini"}}
                   {:name "Anthropic"
                    :config {:provider :anthropic
                             :api-key (System/getenv "ANTHROPIC_API_KEY")
                             :model "claude-3-5-sonnet-20241022"}}]
        prompt "Explain streaming in one sentence"]

    (doseq [{:keys [name config]} providers]
      (when (-> config :api-key some?)
        (println (str name ":"))
        (print "  ")
        (flush)

        (let [model (streaming/create-streaming-model config)
              done? (promise)]
          (streaming/stream-chat model prompt
                                 {:on-token (fn [token]
                                              (print token)
                                              (flush))
                                  :on-complete (fn [_]
                                                 (println)
                                                 (deliver done? true))
                                  :on-error (fn [error]
                                              (println "\n  Error:" (.getMessage error))
                                              (deliver done? true))})
          @done?)))))

;; =============================================================================
;; Example 5: Error Handling
;; =============================================================================

(defn error-handling-example []
  (println "\n=== Example 5: Error Handling ===\n")

  ;; Intentionally use invalid API key to trigger error
  (let [model (streaming/create-streaming-model
               {:provider :openai
                :api-key "invalid-key"
                :model "gpt-4o-mini"})
        tokens-before-error (atom [])]

    (println "Attempting to stream with invalid API key...")

    (streaming/stream-chat model "Hello"
                           {:on-token (fn [token]
                                        (swap! tokens-before-error conj token)
                                        (print token)
                                        (flush))
                            :on-complete (fn [_]
                                           (println "\n✓ Unexpected completion"))
                            :on-error (fn [error]
                                        (println "\n✗ Error caught (as expected):")
                                        (println "  Message:" (.getMessage error))
                                        (println "  Tokens received before error:" (count @tokens-before-error)))})

    (Thread/sleep 2000))) ;; Wait for error

;; =============================================================================
;; Main
;; =============================================================================

(defn -main [& args]
  (let [example (or (first args) "basic")]
    (case example
      "basic" (basic-streaming-example)
      "accumulate" (accumulate-response-example)
      "interactive" (interactive-chat)
      "compare" (compare-providers-example)
      "error" (error-handling-example)
      "all" (do
              (basic-streaming-example)
              (Thread/sleep 1000)
              (accumulate-response-example)
              (Thread/sleep 1000)
              (compare-providers-example)
              (Thread/sleep 1000)
              (error-handling-example))
      (do
        (println "Unknown example:" example)
        (println "Available examples: basic, accumulate, interactive, compare, error, all")))))

(comment
  ;; Run examples
  (basic-streaming-example)
  (accumulate-response-example)
  (interactive-chat)
  (compare-providers-example)
  (error-handling-example)

  ;; Run from command line
  ;; clojure -M -m streaming-demo basic
  ;; clojure -M -m streaming-demo interactive
  )
