(ns langchain4clj.structured
  "TRUE structured output support for LangChain4j in Clojure.
   Provides multiple strategies to guarantee structured responses in JSON or EDN."
  (:require [langchain4clj.core :as core]
            [langchain4clj.tools :as tools]
            [langchain4clj.specs :as specs]
            [langchain4clj.constants :as const]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.walk :as walk]
            ;; Using jsonista for high-performance JSON<->EDN conversion
            [jsonista.core :as j])
  (:import [dev.langchain4j.model.chat.request ChatRequest ResponseFormat]
           [dev.langchain4j.model.openai OpenAiChatModel]
           [dev.langchain4j.agent.tool ToolSpecification]
           [dev.langchain4j.data.message UserMessage SystemMessage]))

;; ============================================================================
;; JSON <-> EDN Conversion using Jsonista
;; ============================================================================

(def ^:private json-mapper
  "High-performance JSON mapper with EDN-friendly configuration"
  (j/object-mapper
   {:decode-key-fn keyword ; Keywords as keys
    :encode-key-fn name ; Convert keywords to strings
    :pretty true}))

;; Forward declarations
(declare schema->example validate-schema supports-json-mode? supports-tools?)

(defn edn->json-str
  "Converts EDN data to JSON string"
  [edn-data]
  (j/write-value-as-string edn-data json-mapper))

(defn json-str->edn
  "Converts JSON string to EDN data"
  [json-str]
  (j/read-value json-str json-mapper))

;; ============================================================================
;; Strategy 1: Native JSON Mode with EDN Support
;; ============================================================================

(defn chat-json-mode
  "Uses native JSON mode when available.
   GUARANTEES valid JSON output, returns EDN data.
   
   IMPORTANT: Some providers (OpenAI, Mistral) REQUIRE the word 'json' 
   in the prompt when using JSON mode. This function automatically adds
   a JSON instruction if not present in the prompt.
   
   Options:
   - :schema - Optional schema to include in prompt
   - :output-format - :edn (default), :json, or :json-str
   - :add-json-instruction? - true (default) to auto-add JSON instruction
   
   The JSON instruction is only added if:
   1. :add-json-instruction? is true (default)
   2. The prompt doesn't already contain 'json' (case-insensitive)
   
   Examples:
   
   ;; Auto-adds JSON instruction (recommended)
   (chat-json-mode model \"Describe a user\" :output-format :edn)
   
   ;; Skip auto-instruction if you have your own
   (chat-json-mode model \"Return JSON: describe a user\" 
                   :add-json-instruction? false)
   
   ;; With schema
   (chat-json-mode model \"Describe a user\" 
                   :schema {:name string? :age int?})"
  [model prompt & {:keys [schema output-format add-json-instruction?]
                   :or {output-format :edn
                        add-json-instruction? true}}]
  (let [;; Check if prompt already mentions JSON (case-insensitive)
        has-json? (re-find #"(?i)json" prompt)

        ;; Build enhanced prompt intelligently
        enhanced-prompt (cond
                          ;; User explicitly disabled auto-instruction
                          (false? add-json-instruction?)
                          prompt

                          ;; Prompt already has "json" - don't duplicate
                          has-json?
                          (if schema
                            (str prompt "\n\nMatch this structure:\n"
                                 (edn->json-str (schema->example schema)))
                            prompt)

                          ;; No "json" in prompt - add instruction
                          :else
                          (if schema
                            (str prompt "\n\nReturn JSON matching this structure:\n"
                                 (edn->json-str (schema->example schema)))
                            (str prompt "\n\nReturn your response as valid JSON.")))

        ;; Create UserMessage for LangChain4j 1.8.0 API
        user-message (UserMessage. enhanced-prompt)

        ;; Create request with JSON mode using correct API
        request (-> (ChatRequest/builder)
                    (.messages [user-message])
                    (.responseFormat ResponseFormat/JSON)
                    (.build))

        response (.chat model request)
        json-response (-> response .aiMessage .text)]

    ;; Return in requested format
    (case output-format
      :json json-response
      :json-str json-response
      :edn (json-str->edn json-response)
      ;; Default to EDN
      (json-str->edn json-response))))

(defn chat-edn-mode
  "Convenience function that always returns EDN data.
   Uses JSON mode under the hood but converts to EDN."
  [model prompt & {:keys [schema]}]
  (chat-json-mode model prompt :schema schema :output-format :edn))

;; ============================================================================
;; Strategy 2: Tool-based Structured Output with EDN
;; ============================================================================

(defn custom-schema->json-schema
  "Converts custom schema format {:name :string} to JSON Schema format.
   This allows structured.clj to use its simple schema format while being
   compatible with tools.clj's JSON Schema requirement."
  [schema]
  (cond
    ;; Already JSON Schema format
    (and (map? schema) (:type schema))
    schema

    ;; Clojure map with type keywords -> convert to JSON Schema
    (map? schema)
    {:type "object"
     :properties (into {} (map (fn [[k v]]
                                 [(name k)
                                  (cond
                                    ;; String types
                                    (#{:string :str} v)
                                    {:type "string"}

                                    ;; Integer types
                                    (#{:int :integer} v)
                                    {:type "integer"}

                                    ;; Number types
                                    (#{:number :float :double} v)
                                    {:type "number"}

                                    ;; Boolean types
                                    (#{:boolean :bool} v)
                                    {:type "boolean"}

                                    ;; Nested map
                                    (map? v)
                                    (custom-schema->json-schema v)

                                    ;; Array
                                    (vector? v)
                                    {:type "array"
                                     :items (if (seq v)
                                              (custom-schema->json-schema (first v))
                                              {:type "string"})}

                                    ;; Default to string
                                    :else
                                    {:type "string"})])
                               schema))}

    ;; Vector schema -> array
    (vector? schema)
    {:type "array"
     :items (if (seq schema)
              (custom-schema->json-schema (first schema))
              {:type "string"})}

    ;; Keyword type
    (keyword? schema)
    (case schema
      (:string :str) {:type "string"}
      (:int :integer) {:type "integer"}
      (:number :float :double) {:type "number"}
      (:boolean :bool) {:type "boolean"}
      {:type "string"})

    ;; Default
    :else
    {:type "object"}))

(defn chat-with-output-tool
  "Uses function calling to guarantee structured output.
   Returns EDN by default."
  [model prompt output-schema & {:keys [output-format]
                                 :or {output-format :edn}}]
  (let [;; Convert custom schema to JSON Schema format
        json-schema (custom-schema->json-schema output-schema)

        ;; Build JsonObjectSchema directly using tools/build-json-schema
        json-object-schema (tools/build-json-schema json-schema)

        ;; Create ToolSpecification directly (bypass create-tool's schema detection)
        tool-spec (-> (ToolSpecification/builder)
                      (.name "return_structured_data")
                      (.description "Returns the structured response")
                      (.parameters json-object-schema)
                      (.build))

        ;; Instruct model to use the tool
        enhanced-prompt (str prompt
                             "\n\nYou MUST call the 'return_structured_data' "
                             "function with your response.")

        ;; Chat with tool
        response (core/chat model enhanced-prompt
                            {:tools [tool-spec]})]

    ;; Check if we got a response
    (when-not response
      (throw (ex-info "Chat returned nil response"
                      {:model model :prompt enhanced-prompt})))

    ;; Get aiMessage from response
    (let [ai-message (.aiMessage response)]
      ;; Check if we got an aiMessage
      (when-not ai-message
        (throw (ex-info "Model did not return an AI message"
                        {:response response})))

      ;; Extract the tool call arguments as our structured output
      (let [tool-calls (.toolExecutionRequests ai-message)]
        (if (and tool-calls (seq tool-calls))
          (let [json-args (-> tool-calls first .arguments)]
            (case output-format
              :json json-args
              :json-str json-args
              :edn (json-str->edn json-args)
              ;; Default to EDN
              (json-str->edn json-args)))
          (throw (ex-info "Model did not use the output tool"
                          {:response response
                           :ai-message ai-message})))))))

;; ============================================================================
;; Strategy 3: Multi-shot Prompting with EDN Validation
;; ============================================================================

(defn chat-with-validation
  "Uses iterative prompting to ensure valid structured output.
   Can work with either JSON or EDN."
  [model prompt schema & {:keys [max-attempts output-format]
                          :or {max-attempts const/default-max-attempts
                               output-format :edn}}]
  (loop [attempt 1]
    (let [;; Create prompt with examples
          example (schema->example schema)
          example-str (if (= output-format :edn)
                        (pr-str example) ; EDN format
                        (edn->json-str example)) ; JSON format

          format-name (if (= output-format :edn) "EDN" "JSON")

          enhanced-prompt (if (= attempt 1)
                            (str prompt "\n\nReturn ONLY valid " format-name
                                 " matching this structure:\n" example-str)
                            (str "That was not valid " format-name
                                 ". Please return ONLY valid " format-name
                                 " matching the structure."))

          ;; Get response
          response (core/chat model enhanced-prompt)

          ;; Try to parse based on format
          parsed (try
                   (case output-format
                     :edn (read-string response)
                     :json (json-str->edn response)
                     :json-str response
                    ;; Default to EDN
                     (read-string response))
                   (catch Exception _e
                     nil))]

      (cond
        ;; Success!
        (and parsed (validate-schema schema parsed))
        parsed

        ;; Retry if attempts remaining
        (< attempt max-attempts)
        (recur (inc attempt))

        ;; Failed after all attempts
        :else
        (throw (ex-info "Could not get valid structured output"
                        {:attempts attempt
                         :last-response response}))))))

;; ============================================================================
;; Unified API with Auto-detection
;; ============================================================================

(defn structured-output
  "Intelligent structured output that uses the best available method.
   
   Strategies (in order):
   1. Native JSON mode (if supported)
   2. Tool-based output (if tools supported)
   3. Validated prompting (fallback)
   
   Options:
   - :model - The LLM model (required)
   - :schema - Output schema in any format (required)
   - :strategy - Force specific strategy (:json-mode, :tools, :validation)
   - :output-format - :edn (default), :json, or :json-str
   - :validate? - Whether to validate output (default true)
   
   Examples:
   
   ;; Get EDN data (default)
   (structured-output model \"Create a recipe\" 
                     {:schema recipe-schema})
   ;; => {:name \"Cake\" :ingredients [...]}
   
   ;; Get JSON string
   (structured-output model \"Create a recipe\"
                     {:schema recipe-schema
                      :output-format :json-str})
   ;; => \"{\\\"name\\\":\\\"Cake\\\",\\\"ingredients\\\":[...]}\"
   
   ;; Force specific strategy
   (structured-output model \"Create a recipe\"
                     {:schema recipe-schema
                      :strategy :json-mode
                      :output-format :edn})"
  [model prompt {:keys [schema strategy output-format validate?]
                 :or {output-format :edn
                      validate? true}}]
  {:pre [(some? model) (string? prompt) (some? schema)]}

  (let [;; Detect model capabilities
        supports-json-mode? (supports-json-mode? model)
        supports-tools? (supports-tools? model)

        ;; Choose strategy
        chosen-strategy (or strategy
                            (cond
                              supports-json-mode? :json-mode
                              supports-tools? :tools
                              :else :validation))

        ;; Execute chosen strategy
        result (case chosen-strategy
                 :json-mode (chat-json-mode model prompt
                                            :schema schema
                                            :output-format output-format)
                 :tools (chat-with-output-tool model prompt schema
                                               :output-format output-format)
                 :validation (chat-with-validation model prompt schema
                                                   :output-format output-format)
                 (throw (ex-info "Unknown strategy"
                                 {:strategy chosen-strategy})))]

    ;; Validate if requested (and if not already a string)
    (if (and validate? (not= output-format :json-str))
      (if (validate-schema schema result)
        result
        (throw (ex-info "Output validation failed"
                        {:output result
                         :schema schema})))
      result)))

;; ============================================================================
;; Helper Functions
;; ============================================================================

(defn supports-json-mode?
  "Checks if model supports native JSON mode"
  [model]
  ;; Check if it's an OpenAI model that supports JSON mode
  (or (instance? OpenAiChatModel model)
      ;; Add other providers that support JSON mode
      false))

(defn supports-tools?
  "Checks if model supports function calling"
  [_model]
  ;; Most modern models support tools
  true)

(defn schema->example
  "Generates an example from schema (returns EDN).
   
   Supports:
   - Simple map schemas with type keywords: {:name :string :age :int}
   - JSON Schema format: {:type \"object\" :properties {...}}
   - Nested structures
   
   Returns a sample data structure that conforms to the schema."
  [schema]
  (cond
    ;; JSON Schema format
    (and (map? schema) (:type schema))
    (let [type (:type schema)]
      (case type
        "object"
        (let [properties (:properties schema)]
          (into {} (map (fn [[k v]]
                          [(keyword k) (schema->example v)])
                        properties)))

        "array"
        (if-let [items (:items schema)]
          [(schema->example items)]
          [])

        "string"
        (or (:enum schema)
            (if (:description schema)
              (str "example " (:description schema))
              "example string"))

        "integer" 42
        "number" 3.14
        "boolean" true

        ;; default
        "example"))

    ;; Clojure map with type keywords
    (map? schema)
    (into {} (map (fn [[k v]]
                    [k (cond
                         ;; Type keywords
                         (= v :string) "example"
                         (= v :str) "example"
                         (= v :int) 42
                         (= v :integer) 42
                         (= v :number) 3.14
                         (= v :float) 3.14
                         (= v :double) 3.14
                         (= v :boolean) true
                         (= v :bool) true
                         (= v :map) {}
                         (= v :vector) []
                         (= v :list) []

                         ;; Nested map schema (like {:name :string})
                         (map? v) (schema->example v)

                         ;; Vector schema (like [:string] or [{:name :string}])
                         (vector? v) [(if (seq v)
                                        (schema->example (first v))
                                        "example")]

                         ;; Default
                         :else "example")])
                  schema))

    ;; Vector schema (e.g., [:string] or [schema])
    (vector? schema)
    [(if (seq schema)
       (schema->example (first schema))
       "example")]

    ;; Keyword type (e.g., :string)
    (keyword? schema)
    (case schema
      :string "example"
      :str "example"
      :int 42
      :integer 42
      :number 3.14
      :float 3.14
      :double 3.14
      :boolean true
      :bool true
      :map {}
      :vector []
      :list []
      "example")

    ;; Default: return a simple map
    :else {}))

(defn validate-schema
  "Validates data against schema"
  [schema data]
  ;; Simple validation - enhance as needed
  (if (map? schema)
    (every? (fn [[k _v]]
              (contains? data k))
            schema)
    true))

;; ============================================================================
;; Convenience Functions
;; ============================================================================

(defmacro defstructured
  "Define a structured output type with guaranteed parsing.
   Returns EDN by default.
   
   Example:
   (defstructured Recipe
     {:name :string
      :ingredients [:vector :string]
      :steps [:vector :string]
      :prep-time :int})
   
   ;; Creates two functions:
   ;; get-recipe - returns EDN
   ;; get-recipe-json - returns JSON string"
  [name schema]
  (let [edn-fn-name (symbol (str "get-" (str/lower-case (str name))))
        json-fn-name (symbol (str "get-" (str/lower-case (str name)) "-json"))]
    `(do
       ;; EDN version (default)
       (defn ~edn-fn-name
         ([model# prompt#]
          (~edn-fn-name model# prompt# {}))
         ([model# prompt# options#]
          (structured-output model# prompt#
                             (merge {:schema ~schema
                                     :output-format :edn}
                                    options#))))

       ;; JSON version
       (defn ~json-fn-name
         ([model# prompt#]
          (~json-fn-name model# prompt# {}))
         ([model# prompt# options#]
          (structured-output model# prompt#
                             (merge {:schema ~schema
                                     :output-format :json-str}
                                    options#)))))))

;; ============================================================================
;; EDN-specific Features
;; ============================================================================

(defn edn-prompt
  "Creates a prompt that explicitly asks for EDN output"
  [prompt schema]
  (str prompt
       "\n\nReturn the response as valid EDN (Clojure data notation) "
       "matching this structure:\n"
       (pr-str (schema->example schema))))

(defn preserve-clojure-types
  "Preserves Clojure-specific types when round-tripping through JSON"
  [data]
  ;; Add metadata to preserve types like keywords, symbols, etc.
  (walk/postwalk
   (fn [x]
     (cond
       (keyword? x) ^{:type :keyword} (name x)
       (symbol? x) ^{:type :symbol} (name x)
       (set? x) ^{:type :set} (vec x)
       :else x))
   data))

(defn restore-clojure-types
  "Restores Clojure types after JSON round-trip"
  [data]
  ;; Restore based on metadata
  (walk/postwalk
   (fn [x]
     (if-let [type-meta (meta x)]
       (case (:type type-meta)
         :keyword (keyword x)
         :symbol (symbol x)
         :set (set x)
         x)
       x))
   data))

;; ============================================================================
;; Examples
;; ============================================================================

(comment
  ;; clj-kondo/ignore unresolved-symbol - Examples show macro-generated functions
  ;; Create model
  (def model (core/create-model {:provider :openai
                                 :api-key (System/getenv "OPENAI_API_KEY")}))

  ;; Method 1: Get EDN data (default)
  (structured-output model
                     "Create a recipe for chocolate cake"
                     {:schema {:name :string
                               :ingredients [:vector :string]
                               :steps [:vector :string]
                               :prep-time :int
                               :cook-time :int}})
  ;; => {:name "Chocolate Cake" 
  ;;     :ingredients ["flour" "sugar" ...] 
  ;;     :steps ["Preheat oven" ...]
  ;;     :prep-time 20
  ;;     :cook-time 35}

  ;; Method 2: Get JSON string
  (structured-output model
                     "List 3 programming languages"
                     {:schema {:languages [:vector :string]}
                      :output-format :json-str})
  ;; => "{\"languages\":[\"Python\",\"JavaScript\",\"Clojure\"]}"

  ;; Method 3: Use EDN-specific function
  (chat-edn-mode model
                 "Describe a person"
                 :schema {:name :string :age :int})
  ;; => {:name "John Doe" :age 30}

  ;; Method 4: Define reusable structured types
  (defstructured Person
    {:name :string
     :age :int
     :email :string
     :interests [:vector :string]})

  ;; Get as EDN (default)
  (get-person model "Generate a fictional person profile")
  ;; => {:name "Alice Smith" :age 28 :email "alice@example.com" 
  ;;     :interests ["reading" "hiking" "cooking"]}

  ;; Get as JSON string
  (get-person-json model "Generate a fictional person profile")
  ;; => "{\"name\":\"Bob Jones\",\"age\":35,...}"

  ;; Method 5: Explicit EDN prompt
  (core/chat model (edn-prompt "List colors" {:colors [:vector :keyword]})))
  ;; Model will try to return EDN format like:
  ;; {:colors [:red :blue :green]}

;; ============================================================================
;; Specs for Public API
;; ============================================================================

(s/fdef chat-json-mode
  :args (s/cat :model ::specs/chat-model
               :prompt string?
               :opts (s/keys* :opt-un [::specs/schema ::specs/output-format]))
  :ret (s/or :edn map? :json string?))

(s/fdef chat-edn-mode
  :args (s/cat :model ::specs/chat-model
               :prompt string?
               :opts (s/keys* :opt-un [::specs/schema]))
  :ret map?)

(s/fdef chat-with-output-tool
  :args (s/cat :model ::specs/chat-model
               :prompt string?
               :output-schema ::specs/schema
               :opts (s/keys* :opt-un [::specs/output-format]))
  :ret (s/or :edn map? :json string?))

(s/fdef chat-with-validation
  :args (s/cat :model ::specs/chat-model
               :prompt string?
               :schema ::specs/schema
               :opts (s/keys* :opt-un [::specs/max-attempts ::specs/output-format]))
  :ret (s/or :edn map? :json string?))

(s/fdef structured-output
  :args (s/cat :model ::specs/chat-model
               :prompt string?
               :options ::specs/structured-output-opts)
  :ret (s/or :edn map? :json string?))

