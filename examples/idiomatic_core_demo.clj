(ns examples.idiomatic-core-demo
  "Demonstrates the new idiomatic Core API (v0.2.0)."
  (:require [langchain4clj.core :as core]))

(println "=== Idiomatic Core API Demo ===\n")

;; ============================================================================
;; Example 1: Traditional API (still works!)
;; ============================================================================

(println "Example 1: Traditional API (Backward Compatible)\n")

(def model-old-style
  (core/create-model {:provider :openai
                      :api-key "sk-test"
                      :model "gpt-4"
                      :temperature 0.7}))

(println "✓ Traditional create-model still works")
(println "  Type:" (class model-old-style))

;; ============================================================================
;; Example 2: New Idiomatic API - Simple
;; ============================================================================

(println "\nExample 2: New Idiomatic API - Simple\n")

(def model-new-simple
  (core/openai-model {:api-key "sk-test"
                      :model "gpt-4"
                      :temperature 0.7}))

(println "✓ New openai-model function")
(println "  Type:" (class model-new-simple))
(println "  Benefit: No need for :provider key!")

;; ============================================================================
;; Example 3: Threading-First Pattern
;; ============================================================================

(println "\nExample 3: Threading-First Pattern\n")

(def model-threaded
  (-> {:api-key "sk-test"}
      (core/with-model "gpt-4")
      (core/with-temperature 0.9)
      (core/with-timeout 30000)
      (core/with-logging)
      core/openai-model))

(println "✓ Threading-first with -> macro")
(println "  Type:" (class model-threaded))
(println "  Benefit: Composable, readable, idiomatic Clojure!")

;; ============================================================================
;; Example 4: Config Composition
;; ============================================================================

(println "\nExample 4: Config Composition\n")

;; Base configuration
(def base-config
  {:api-key "sk-test"
   :temperature 0.5
   :timeout 60000})

;; Development overrides
(def dev-overrides
  {:model "gpt-4o-mini"
   :log-requests? true
   :log-responses? true})

;; Production overrides
(def prod-overrides
  {:model "gpt-4"
   :temperature 0.3
   :max-retries 3})

;; Compose and create
(def dev-model
  (-> base-config
      (merge dev-overrides)
      core/openai-model))

(def prod-model
  (-> base-config
      (merge prod-overrides)
      core/openai-model))

(println "✓ Config composition with merge")
(println "  Dev model:" (class dev-model))
(println "  Prod model:" (class prod-model))
(println "  Benefit: Reusable configs, easy environment switching!")

;; ============================================================================
;; Example 5: Conditional Configuration
;; ============================================================================

(println "\nExample 5: Conditional Configuration\n")

(defn create-model-for-env [api-key env]
  (-> {:api-key api-key}
      (core/with-model (case env
                         :dev "gpt-4o-mini"
                         :staging "gpt-4"
                         :prod "gpt-4"))
      (core/with-temperature (case env
                               :dev 0.9
                               :staging 0.7
                               :prod 0.3))
      (core/with-timeout (case env
                           :dev 30000
                           :staging 45000
                           :prod 60000))
      (cond-> (= env :dev) (core/with-logging))
      core/openai-model))

(def dev-model-2 (create-model-for-env "sk-test" :dev))
(def prod-model-2 (create-model-for-env "sk-test" :prod))

(println "✓ Conditional config based on environment")
(println "  Dev:" (class dev-model-2))
(println "  Prod:" (class prod-model-2))
(println "  Benefit: Single function, multiple environments!")

;; ============================================================================
;; Example 6: Anthropic Models
;; ============================================================================

(println "\nExample 6: Anthropic Models\n")

(def claude-simple
  (core/anthropic-model {:api-key "sk-ant-test"}))

(def claude-threaded
  (-> {:api-key "sk-ant-test"}
      (core/with-model "claude-3-opus-20240229")
      (core/with-temperature 0.8)
      core/anthropic-model))

(println "✓ Anthropic models work the same way")
(println "  Simple:" (class claude-simple))
(println "  Threaded:" (class claude-threaded))

;; ============================================================================
;; Comparison: Before vs After
;; ============================================================================

(println "\n=== Comparison: Before vs After ===\n")

(println "BEFORE (Traditional):")
(println "(def model")
(println "  (create-model {:provider :openai")
(println "                 :api-key \"sk-...\"")
(println "                 :model \"gpt-4\"")
(println "                 :temperature 0.9}))")

(println "\nAFTER (Idiomatic):")
(println "(def model")
(println "  (-> {:api-key \"sk-...\"}")
(println "      (with-model \"gpt-4\")")
(println "      (with-temperature 0.9)")
(println "      openai-model))")

(println "\nBenefits:")
(println "  ✓ 20-30% less code")
(println "  ✓ Threading-first support (->)")
(println "  ✓ No :provider key needed")
(println "  ✓ Composable with merge, cond->, etc")
(println "  ✓ More idiomatic Clojure")
(println "  ✓ 100% backward compatible!")

;; ============================================================================
;; Summary
;; ============================================================================

(println "\n=== Summary ===")
(println "
New functions in v0.2.0:

Model Creation:
  • openai-model     - Create OpenAI models
  • anthropic-model  - Create Anthropic models

Threading Helpers:
  • with-model       - Set model name
  • with-temperature - Set temperature
  • with-timeout     - Set timeout
  • with-logging     - Enable logging

Old functions still work:
  • create-model     - Traditional API (uses :provider)
  • build-model      - Internal multimethod

Migration is optional - both APIs coexist!
")

(println "Run this demo with:")
(println "  clojure -M:dev -m examples.idiomatic-core-demo")
