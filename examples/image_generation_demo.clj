(ns image-generation-demo
  "Image generation examples using DALL-E 3"
  (:require [langchain4clj.image :as image]))

;; =============================================================================
;; Setup
;; =============================================================================

(defn get-api-key []
  (or (System/getenv "OPENAI_API_KEY")
      (throw (Exception. "OPENAI_API_KEY environment variable not set"))))

;; =============================================================================
;; Example 1: Basic Image Generation
;; =============================================================================

(defn basic-generation-example []
  (println "\n=== Example 1: Basic Image Generation ===\n")

  (let [model (image/create-image-model
               {:provider :openai
                :api-key (get-api-key)
                :model "dall-e-3"})

        prompt "A serene mountain landscape at sunset with a crystal clear lake"

        result (image/generate model prompt)]

    (println "Prompt:" prompt)
    (println "Generated Image URL:" (:url result))
    (println "Revised Prompt:" (:revised-prompt result))
    (println "\nOpen the URL in your browser to view the image!")))

;; =============================================================================
;; Example 2: Using Convenience Function
;; =============================================================================

(defn convenience-function-example []
  (println "\n=== Example 2: Using openai-image-model Convenience Function ===\n")

  ;; Using the convenience function instead of create-image-model
  (let [model (image/openai-image-model {:api-key (get-api-key)})

        prompt "A futuristic cityscape with flying cars and neon lights"

        result (image/generate model prompt)]

    (println "Prompt:" prompt)
    (println "Image URL:" (:url result))
    (println "Revised Prompt:" (:revised-prompt result))))

;; =============================================================================
;; Example 3: HD Quality Images
;; =============================================================================

(defn hd-quality-example []
  (println "\n=== Example 3: HD Quality Images ===\n")

  (let [model (image/create-image-model
               {:provider :openai
                :api-key (get-api-key)
                :model "dall-e-3"
                :quality "hd"
                :size "1792x1024"}) ; Landscape format

        prompt "A detailed fantasy dragon perched on a medieval castle tower"

        result (image/generate model prompt)]

    (println "Configuration:")
    (println "  Model: dall-e-3")
    (println "  Quality: hd")
    (println "  Size: 1792x1024 (landscape)")
    (println "\nPrompt:" prompt)
    (println "HD Image URL:" (:url result))
    (println "\nNote: HD images cost more but provide higher quality")))

;; =============================================================================
;; Example 4: Different Sizes and Orientations
;; =============================================================================

(defn size-variations-example []
  (println "\n=== Example 4: Different Sizes and Orientations ===\n")

  (let [configs [{:size "1024x1024" :desc "Square"}
                 {:size "1792x1024" :desc "Landscape"}
                 {:size "1024x1792" :desc "Portrait"}]

        base-prompt "A minimalist abstract art composition"]

    (doseq [{:keys [size desc]} configs]
      (let [model (image/create-image-model
                   {:provider :openai
                    :api-key (get-api-key)
                    :model "dall-e-3"
                    :size size})

            result (image/generate model base-prompt)]

        (println (str desc " (" size "):"))
        (println "  URL:" (:url result))
        (println)))))

;; =============================================================================
;; Example 5: Style Variations (Vivid vs Natural)
;; =============================================================================

(defn style-variations-example []
  (println "\n=== Example 5: Style Variations ===\n")

  (let [prompt "A coffee shop interior with warm lighting"

        ;; Generate with "vivid" style (more dramatic/hyper-real)
        vivid-model (image/create-image-model
                     {:provider :openai
                      :api-key (get-api-key)
                      :model "dall-e-3"
                      :style "vivid"})

        vivid-result (image/generate vivid-model prompt)

        ;; Generate with "natural" style (more subtle/realistic)
        natural-model (image/create-image-model
                       {:provider :openai
                        :api-key (get-api-key)
                        :model "dall-e-3"
                        :style "natural"})

        natural-result (image/generate natural-model prompt)]

    (println "Same prompt with different styles:")
    (println "Prompt:" prompt)
    (println "\nVivid Style (hyper-real, dramatic):")
    (println "  URL:" (:url vivid-result))
    (println "\nNatural Style (subtle, realistic):")
    (println "  URL:" (:url natural-result))))

;; =============================================================================
;; Example 6: DALL-E 2 (Smaller, Faster, Cheaper)
;; =============================================================================

(defn dalle2-example []
  (println "\n=== Example 6: DALL-E 2 (Faster & Cheaper) ===\n")

  (let [model (image/create-image-model
               {:provider :openai
                :api-key (get-api-key)
                :model "dall-e-2"
                :size "512x512"}) ; DALL-E 2 supports smaller sizes

        prompt "A simple cartoon robot waving hello"

        result (image/generate model prompt)]

    (println "Model: DALL-E 2")
    (println "Size: 512x512")
    (println "Prompt:" prompt)
    (println "Image URL:" (:url result))
    (println "\nNote: DALL-E 2 is faster and cheaper, but lower quality than DALL-E 3")))

;; =============================================================================
;; Example 7: Batch Generation
;; =============================================================================

(defn batch-generation-example []
  (println "\n=== Example 7: Batch Image Generation ===\n")

  (let [model (image/create-image-model
               {:provider :openai
                :api-key (get-api-key)
                :model "dall-e-3"})

        prompts ["A red apple on a wooden table"
                 "A blue butterfly on a flower"
                 "A golden sunset over the ocean"]]

    (println "Generating" (count prompts) "images...\n")

    (doseq [[idx prompt] (map-indexed vector prompts)]
      (println (str "Image " (inc idx) "/" (count prompts)))
      (println "  Prompt:" prompt)

      (try
        (let [result (image/generate model prompt)]
          (println "  ✓ Generated:" (:url result)))
        (catch Exception e
          (println "  ✗ Error:" (.getMessage e))))

      (println))))

;; =============================================================================
;; Example 8: Error Handling
;; =============================================================================

(defn error-handling-example []
  (println "\n=== Example 8: Error Handling ===\n")

  (let [model (image/create-image-model
               {:provider :openai
                :api-key (get-api-key)
                :model "dall-e-3"})]

    ;; Test with various edge cases
    (println "Testing error handling scenarios:\n")

    ;; 1. Empty prompt
    (try
      (println "1. Empty prompt...")
      (image/generate model "")
      (println "   ✓ Handled empty prompt")
      (catch AssertionError e
        (println "   ✗ AssertionError:" (.getMessage e)))
      (catch Exception e
        (println "   ✗ Error:" (.getMessage e))))

    (println)

    ;; 2. Valid prompt should work
    (try
      (println "2. Valid prompt...")
      (let [result (image/generate model "A simple test image")]
        (println "   ✓ Success:" (:url result)))
      (catch Exception e
        (println "   ✗ Error:" (.getMessage e))))))

;; =============================================================================
;; Example 9: Threaded API Usage
;; =============================================================================

(defn threaded-api-example []
  (println "\n=== Example 9: Threaded API Usage ===\n")

  ;; Create base model config
  (let [base-config {:provider :openai
                     :api-key (get-api-key)}

        ;; Thread through different configurations
        result (-> base-config
                   (assoc :model "dall-e-3")
                   (assoc :quality "hd")
                   (assoc :size "1024x1024")
                   (image/create-image-model)
                   (image/generate "A beautiful garden with colorful flowers"))]

    (println "Using threaded configuration:")
    (println "Image URL:" (:url result))
    (println "Revised Prompt:" (:revised-prompt result))))

;; =============================================================================
;; Main Entry Point
;; =============================================================================

(defn run-all-examples []
  (println "\n╔════════════════════════════════════════════════════════════╗")
  (println "║  LangChain4Clj - Image Generation Examples (DALL-E 3)     ║")
  (println "╚════════════════════════════════════════════════════════════╝")

  (try
    (basic-generation-example)
    (convenience-function-example)
    (hd-quality-example)
    (size-variations-example)
    (style-variations-example)
    (dalle2-example)
    (batch-generation-example)
    (error-handling-example)
    (threaded-api-example)

    (println "\n✓ All examples completed!")
    (println "\nNote: Open the generated URLs in your browser to view the images.")
    (println "Images are hosted temporarily by OpenAI.")

    (catch Exception e
      (println "\n✗ Error running examples:" (.getMessage e))
      (println "Make sure OPENAI_API_KEY is set in your environment"))))

(defn -main [& args]
  (if-let [example (first args)]
    (case example
      "basic" (basic-generation-example)
      "convenience" (convenience-function-example)
      "hd" (hd-quality-example)
      "sizes" (size-variations-example)
      "styles" (style-variations-example)
      "dalle2" (dalle2-example)
      "batch" (batch-generation-example)
      "errors" (error-handling-example)
      "threaded" (threaded-api-example)
      "all" (run-all-examples)
      (do
        (println "Unknown example:" example)
        (println "\nAvailable examples:")
        (println "  basic       - Basic image generation")
        (println "  convenience - Using convenience function")
        (println "  hd          - HD quality images")
        (println "  sizes       - Different sizes and orientations")
        (println "  styles      - Style variations (vivid vs natural)")
        (println "  dalle2      - Using DALL-E 2")
        (println "  batch       - Batch generation")
        (println "  errors      - Error handling")
        (println "  threaded    - Threaded API usage")
        (println "  all         - Run all examples")
        (println "\nUsage: clojure -M -m image-generation-demo <example>")))
    (run-all-examples)))

(comment
  ;; Run individual examples in REPL
  (basic-generation-example)
  (convenience-function-example)
  (hd-quality-example)
  (size-variations-example)
  (style-variations-example)
  (dalle2-example)
  (batch-generation-example)
  (error-handling-example)
  (threaded-api-example)

  ;; Or run all at once
  (run-all-examples))
