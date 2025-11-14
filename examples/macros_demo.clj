(ns examples.macros-demo
  "Demonstrates the new idiomatic macros for builder patterns."
  (:require [langchain4clj.macros :as macros])
  (:import [dev.langchain4j.model.chat.request ChatRequest]
           [dev.langchain4j.data.message UserMessage]
           [dev.langchain4j.model.chat.request.json JsonStringSchema JsonIntegerSchema]))

;; ============================================================================
;; Example 1: defbuilder macro with ChatRequest
;; ============================================================================

(println "=== Example 1: defbuilder with ChatRequest ===\n")

;; Define a builder function using defbuilder
(macros/defbuilder build-chat-request
  (ChatRequest/builder)
  {:messages :messages
   :model-name :modelName
   :temperature :temperature})

;; Create some messages
(def user-message (UserMessage. "What is the capital of France?"))
(def messages [user-message])

;; Use the builder - simple map-based API
(def request1 (build-chat-request {:messages messages
                                   :model-name "gpt-4"
                                   :temperature 0.7}))

(println "Request 1:")
(println "  Model:" (.modelName request1))
(println "  Temperature:" (.temperature request1))
(println "  Messages:" (count (.messages request1)))

;; ============================================================================
;; Example 2: Threading-first pattern with defbuilder
;; ============================================================================

(println "\n=== Example 2: Threading-first pattern ===\n")

;; Use threading-first to compose configuration
(def request2
  (-> {:messages messages}
      (assoc :model-name "gpt-4")
      (assoc :temperature 0.8)
      build-chat-request))

(println "Request 2 (built with ->):")
(println "  Model:" (.modelName request2))
(println "  Temperature:" (.temperature request2))

;; ============================================================================
;; Example 3: Composing configurations
;; ============================================================================

(println "\n=== Example 3: Config composition ===\n")

;; Base config
(def base-config
  {:messages messages
   :model-name "gpt-3.5-turbo"
   :temperature 0.5})

;; Production overrides
(def prod-overrides
  {:model-name "gpt-4"
   :temperature 0.3})

;; Compose configs and build
(def request3
  (-> base-config
      (merge prod-overrides)
      build-chat-request))

(println "Request 3 (composed):")
(println "  Model:" (.modelName request3))
(println "  Temperature:" (.temperature request3))

;; ============================================================================
;; Example 4: build-with for inline building
;; ============================================================================

(println "\n=== Example 4: build-with for inline use ===\n")

;; Use build-with for one-off builders without defining a function
(def string-schema
  (macros/build-with (JsonStringSchema/builder)
                     {:description "A person's name"
                      :min-length 1
                      :max-length 100}))

(println "String Schema:")
(println "  Description:" (.description string-schema))
(println "  Min Length:" (.minLength string-schema))
(println "  Max Length:" (.maxLength string-schema))

(def integer-schema
  (macros/build-with (JsonIntegerSchema/builder)
                     {:description "A person's age"
                      :minimum 0
                      :maximum 150}))

(println "\nInteger Schema:")
(println "  Description:" (.description integer-schema))
(println "  Minimum:" (.minimum integer-schema))
(println "  Maximum:" (.maximum integer-schema))

;; ============================================================================
;; Example 5: Conditional configuration with apply-if and apply-when-some
;; ============================================================================

(println "\n=== Example 5: Conditional configuration ===\n")

(defn build-request-with-options [messages opts]
  (let [model-name (:model opts)
        temperature (:temperature opts)
        use-gpt4? (:use-gpt4? opts)]
    (-> {:messages messages}
        (macros/apply-when-some model-name assoc :model-name model-name)
        (macros/apply-when-some temperature assoc :temperature temperature)
        (macros/apply-if use-gpt4? assoc :model-name "gpt-4")
        build-chat-request)))

;; Build with partial config
(def request4 (build-request-with-options messages {:model "gpt-3.5" :use-gpt4? true}))
(def request5 (build-request-with-options messages {:temperature 0.9}))

(println "Request 4 (conditional, use-gpt4? = true):")
(println "  Model:" (.modelName request4))

(println "\nRequest 5 (only temperature set):")
(println "  Temperature:" (.temperature request5))

;; ============================================================================
;; Example 6: Deep merge for nested configs
;; ============================================================================

(println "\n=== Example 6: Deep merge ===\n")

(def default-config
  {:model "gpt-3.5-turbo"
   :options {:temperature 0.7
             :retries 3
             :timeout 30000}})

(def user-config
  {:model "gpt-4"
   :options {:temperature 0.5}})

(def merged-config (macros/deep-merge default-config user-config))

(println "Default config:" default-config)
(println "User config:" user-config)
(println "Merged config:" merged-config)
(println "\nNote: Nested :options map was deep-merged!")

;; ============================================================================
;; Summary
;; ============================================================================

(println "\n=== Summary ===")
(println "
These macros enable idiomatic Clojure patterns:

1. defbuilder - Eliminates Java builder boilerplate
2. build-with - Inline builder usage with reflection
3. apply-if - Conditional transformations in threading
4. apply-when-some - Safe nil handling in threading
5. deep-merge - Recursive map merging
6. with-defaults - Config with fallbacks

Benefits:
✓ 30-40% less code
✓ Threading-first support (->)
✓ Pure data until the last moment
✓ Composable and testable
✓ Familiar to Clojure developers
")

(println "Run this demo with:")
(println "  clojure -M:dev -m examples.macros-demo")
