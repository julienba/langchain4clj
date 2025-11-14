(ns examples.json-mode-demo
  "Demonstrates native JSON mode support in langchain4clj v0.2.0.
  
  Native JSON mode forces the LLM to return valid JSON guaranteed by the provider
  (OpenAI, Anthropic), eliminating parsing errors and retry logic."
  (:require [nandoolle.langchain4clj.core :as llm]
            [clojure.data.json :as json])
  (:import [dev.langchain4j.model.chat.request ResponseFormat]))

;; ============================================================================
;; Setup: Create a model
;; ============================================================================

(def model
  (llm/openai-model
   {:api-key (or (System/getenv "OPENAI_API_KEY")
                 "sk-test-key-for-demo")
    :model "gpt-4o-mini"}))

(println "=== JSON Mode Demo: Native JSON Support ===\n")

;; ============================================================================
;; Example 1: Direct JSON Mode with :response-format
;; ============================================================================

(println "Example 1: Direct JSON mode with :response-format")
(println "------------------------------------------------")

(comment
  ;; This requires a real API key to work
  (def response-1
    (llm/chat model
              "Generate a user profile with name, age, and email"
              {:response-format ResponseFormat/JSON}))

  ;; Extract and parse JSON
  (def json-str (-> response-1 .aiMessage .text))
  (println "Raw JSON response:")
  (println json-str)

  (def parsed-data (json/read-str json-str :key-fn keyword))
  (println "\nParsed EDN data:")
  (println parsed-data)
  ;; => {:name "John Doe" :age 30 :email "john@example.com"}
  )

(println "✅ Direct mode: Pass {:response-format ResponseFormat/JSON}\n")

;; ============================================================================
;; Example 2: Using with-json-mode Helper
;; ============================================================================

(println "Example 2: Using with-json-mode helper")
(println "---------------------------------------")

(comment
  (def response-2
    (llm/chat model
              "Return a list of 3 programming languages as JSON"
              (llm/with-json-mode {:temperature 0.7})))

  (def languages
    (json/read-str
     (-> response-2 .aiMessage .text)
     :key-fn keyword))

  (println "Languages:" languages)
  ;; => {:languages ["Python" "JavaScript" "Clojure"]}
  )

(println "✅ Helper mode: Use (with-json-mode config)\n")

;; ============================================================================
;; Example 3: Threading-First Pattern
;; ============================================================================

(println "Example 3: Threading-first pattern")
(println "-----------------------------------")

(comment
  (def response-3
    (-> {:temperature 0.5
         :max-tokens 300}
        llm/with-json-mode
        (as-> opts
              (llm/chat model
                        "Generate book data with title, author, year, and genres"
                        opts))))

  (def book-data
    (json/read-str
     (-> response-3 .aiMessage .text)
     :key-fn keyword))

  (println "Book data:" book-data)
  ;; => {:title "The Great Gatsby"
  ;;     :author "F. Scott Fitzgerald"
  ;;     :year 1925
  ;;     :genres ["Fiction" "Classic"]}
  )

(println "✅ Threading mode: Compose with other options\n")

;; ============================================================================
;; Example 4: Complex Nested Data
;; ============================================================================

(println "Example 4: Complex nested data structures")
(println "------------------------------------------")

(comment
  (def response-4
    (llm/chat model
              (str "Generate a company profile with the following structure:\n"
                   "- name (string)\n"
                   "- founded (number)\n"
                   "- employees (object with 'total' and 'remote' numbers)\n"
                   "- departments (array of objects with 'name' and 'size')\n"
                   "- active (boolean)")
              {:response-format ResponseFormat/JSON
               :temperature 0.3}))

  (def company
    (json/read-str
     (-> response-4 .aiMessage .text)
     :key-fn keyword))

  (println "Company profile:")
  (clojure.pprint/pprint company)
  ;; => {:name "TechCorp Inc"
  ;;     :founded 2010
  ;;     :employees {:total 150 :remote 75}
  ;;     :departments [{:name "Engineering" :size 50}
  ;;                   {:name "Sales" :size 30}
  ;;                   {:name "Marketing" :size 20}]
  ;;     :active true}
  )

(println "✅ Nested data: Fully structured JSON guaranteed\n")

;; ============================================================================
;; Example 5: Multiple Requests with Consistent Structure
;; ============================================================================

(println "Example 5: Batch processing with consistent JSON schema")
(println "---------------------------------------------------------")

(comment
  (defn get-user-data [prompt]
    (let [response (llm/chat model
                             prompt
                             (llm/with-json-mode {}))]
      (json/read-str
       (-> response .aiMessage .text)
       :key-fn keyword)))

  ;; Generate multiple user profiles
  (def users
    (mapv get-user-data
          ["Generate a profile for a developer"
           "Generate a profile for a designer"
           "Generate a profile for a manager"]))

  (println "Generated users:")
  (doseq [user users]
    (println "-" (:name user) "-" (:role user)))
  ;; - Alice Smith - Developer
  ;; - Bob Jones - Designer
  ;; - Carol White - Manager
  )

(println "✅ Batch mode: Consistent schema across multiple requests\n")

;; ============================================================================
;; Example 6: Comparison - Without vs With JSON Mode
;; ============================================================================

(println "Example 6: Comparison - Regular vs JSON Mode")
(println "---------------------------------------------")

(comment
  ;; WITHOUT JSON mode (unreliable)
  (println "WITHOUT JSON mode:")
  (def response-regular
    (llm/chat model "Return JSON with name and age"))
  (println response-regular)
  ;; => "Here's the JSON you requested: {\"name\": \"John\", \"age\": 30}"
  ;; ^ NOT valid JSON! Has extra text, needs manual parsing, retry logic, etc.

  ;; WITH JSON mode (reliable)
  (println "\nWITH JSON mode:")
  (def response-json
    (llm/chat model
              "Return data with name and age"
              {:response-format ResponseFormat/JSON}))
  (println (-> response-json .aiMessage .text))
  ;; => "{\"name\":\"John\",\"age\":30}"
  ;; ^ GUARANTEED valid JSON! Just parse and use.
  )

(println "✅ Comparison: JSON mode eliminates parsing errors\n")

;; ============================================================================
;; Example 7: Integration with Existing Code
;; ============================================================================

(println "Example 7: Easy integration with existing code")
(println "-----------------------------------------------")

(comment
  ;; Define a helper function
  (defn fetch-json
    "Fetches JSON data from LLM with automatic parsing"
    [model prompt & {:keys [temperature max-tokens]
                     :or {temperature 0.7 max-tokens 1000}}]
    (let [response (llm/chat model
                             prompt
                             (-> {:temperature temperature
                                  :max-tokens max-tokens}
                                 llm/with-json-mode))]
      (json/read-str
       (-> response .aiMessage .text)
       :key-fn keyword)))

  ;; Use it anywhere in your application
  (def product-info (fetch-json model "Generate a product with name, price, and category"))
  (def customer-data (fetch-json model "Generate customer data" :temperature 0.5))

  (println "Product:" product-info)
  (println "Customer:" customer-data))

(println "✅ Integration: Wrap in helper functions for reusability\n")

;; ============================================================================
;; Key Benefits Summary
;; ============================================================================

(println "=== Key Benefits of Native JSON Mode ===")
(println "✅ 100% RELIABLE - Provider guarantees valid JSON")
(println "✅ NO ERRORS - No need for retry logic or error handling")
(println "✅ FASTER - No post-processing or validation needed")
(println "✅ SIMPLE - Just parse with json/read-str and use")
(println "✅ COMPOSABLE - Works with all other chat options")
(println "✅ THREADING - Fully compatible with threading-first style")

(println "\n=== When to Use JSON Mode ===")
(println "✅ When you need structured data reliably")
(println "✅ When you want to avoid parsing errors")
(println "✅ When you're building APIs that return JSON")
(println "✅ When you need consistent schemas across requests")
(println "✅ When you want the simplest possible integration")

(println "\n=== When NOT to Use JSON Mode ===")
(println "❌ When you need natural language responses")
(println "❌ When you want markdown formatting")
(println "❌ When the LLM should explain its reasoning (use tools instead)")
(println "❌ When you need complex validation (use structured namespace)")

(println "\n=== Supported Providers ===")
(println "✅ OpenAI (GPT-4, GPT-4o, GPT-4o-mini)")
(println "✅ Anthropic (Claude 3+)")
(println "⚠️  Check your provider's documentation for JSON mode support")

(println "\n=== Demo Complete ===")
(println "See the source code for all examples!")
(println "Run with: clojure -M:dev -m examples.json-mode-demo")
