(ns langchain4clj.tools
  "Unified tool support with automatic schema detection.
   Supports Clojure Spec, Plumatic Schema, and Malli."
  (:require [langchain4clj.tools.protocols :as p]
            [langchain4clj.tools.spec :as spec-impl]
            [langchain4clj.specs :as specs]
            [clojure.spec.alpha :as s]
            [clojure.walk :as walk]
            [clojure.string :as str])
  (:import [dev.langchain4j.agent.tool ToolSpecification]
           [dev.langchain4j.data.message ToolExecutionResultMessage]
           [dev.langchain4j.model.chat.request.json
            JsonObjectSchema
            JsonStringSchema
            JsonIntegerSchema
            JsonNumberSchema
            JsonBooleanSchema
            JsonArraySchema]))

;; ============================================================================
;; deftool Macro - Idiomatic Clojure Tool Definition
;; ============================================================================

(defn- generate-tool-spec
  "Generates a Clojure spec from a simple schema map.
   
   For :req-un specs, the namespace of the spec keyword is stripped when
   matching keys in the data. So :user.tool-name/param-name matches :param-name.
   
   Input: {:pokemon-name string? :level int?}
   Output: Spec definition that validates a map with those keys"
  [tool-name schema-map]
  (let [spec-ns (str *ns*)
        ;; Create a unique namespace for this tool to avoid collisions
        tool-spec-ns (str spec-ns "." tool-name)
        ;; Generate individual parameter specs
        ;; :user.tool-name/param-name will match :param-name in the data (for :req-un)
        param-specs (for [[k pred] schema-map]
                      (let [spec-name (keyword tool-spec-ns (name k))]
                        `(s/def ~spec-name ~pred)))
        param-spec-name (keyword tool-spec-ns "params")
        ;; For :req-un, these namespaced specs will match unqualified keys
        param-spec-keys (mapv (fn [[k _]]
                                (keyword tool-spec-ns (name k)))
                              schema-map)]
    {:param-specs param-specs
     :param-spec-name param-spec-name
     :param-spec `(s/def ~param-spec-name
                    (s/keys :req-un ~param-spec-keys))}))

(defmacro deftool
  "Creates a tool with defn-like syntax and mandatory inline schema.
   
   This is the RECOMMENDED way to create tools in langchain4clj.
   
   Syntax:
     (deftool name
       \"docstring\"
       {:param-name type-predicate}
       [destructured-args]
       body...)
   
   The schema map uses simple type predicates:
     - string? for strings
     - int? for integers  
     - boolean? for booleans
     - keyword? for keywords
     - Or any custom predicate function
   
   For tools with no parameters, use an empty map {}:
     (deftool list-all-items
       \"Lists all items\"
       {}
       [_]
       (fetch-all-items))
   
   Automatically:
     - Generates Clojure specs from the schema map
     - Registers the tool as a def in the current namespace
     - Validates parameters at runtime
     - Normalizes kebab-case ↔ camelCase for OpenAI compatibility
   
   Examples:
   
     ;; Simple tool
     (deftool get-pokemon
       \"Fetches Pokemon information by name\"
       {:pokemon-name string?}
       [{:keys [pokemon-name]}]
       (fetch-pokemon-data pokemon-name))
     
     ;; Multiple parameters
     (deftool compare-pokemon
       \"Compares two Pokemon side by side\"
       {:pokemon1 string?
        :pokemon2 string?}
       [{:keys [pokemon1 pokemon2]}]
       (str \"Comparing \" pokemon1 \" vs \" pokemon2))
     
     ;; No parameters
     (deftool get-all-users
       \"Retrieves all users from database\"
       {}
       [_]
       (db/get-all-users))
   
   The generated tool can be used directly in assistants:
     (create-assistant {:model model :tools [get-pokemon compare-pokemon]})"
  [name docstring schema-map args & body]
  {:pre [(symbol? name)
         (string? docstring)
         (map? schema-map)
         (vector? args)]}

  (let [tool-name-str (clojure.core/name name)]
    (if (empty? schema-map)
      ;; Tool with no parameters - create simple tool
      `(def ~name
         ~docstring
         (create-tool
          {:name ~tool-name-str
           :description ~docstring
           :params-schema :no-params ; Special marker for no-param tools
           :fn (fn ~args ~@body)}))

      ;; Tool with parameters - generate specs
      (let [{:keys [param-specs param-spec-name param-spec]}
            (generate-tool-spec tool-name-str schema-map)]
        `(do
           ;; Define all the parameter specs
           ~@param-specs

           ;; Define the params spec (combines all param specs)
           ~param-spec

           ;; Define the tool itself
           (def ~name
             ~docstring
             (create-tool
              {:name ~tool-name-str
               :description ~docstring
               :params-schema ~param-spec-name
               :fn (fn ~args ~@body)})))))))

;; Conditional requires for optional dependencies
(try
  (require '[langchain4clj.tools.schema :as schema-impl])
  (catch Exception _
    ;; Prismatic Schema not available
    nil))

(try
  (require '[langchain4clj.tools.malli :as malli-impl])
  (catch Exception _
    ;; Malli not available
    nil))

;; ============================================================================
;; Schema Detection
;; ============================================================================

(defn detect-schema-type
  "Automatically detects the type of schema library being used.
   Returns :spec, :schema, :malli, or nil"
  [schema]
  (cond
    ;; Check for Spec first (keywords are common)
    (spec-impl/spec? schema) :spec

    ;; Then Malli (vectors with keyword first)
    (and (find-ns 'langchain4clj.tools.malli)
         ((resolve 'langchain4clj.tools.malli/malli?) schema)) :malli

    ;; Then Plumatic Schema
    (and (find-ns 'langchain4clj.tools.schema)
         ((resolve 'langchain4clj.tools.schema/schema?) schema)) :schema

    ;; Unknown
    :else nil))

(defn create-provider
  "Creates the appropriate SchemaProvider based on the schema type.
   Can auto-detect or accept explicit type."
  ([schema]
   (create-provider schema nil))
  ([schema schema-type]
   (let [type (or schema-type (detect-schema-type schema))]
     (case type
       :spec (spec-impl/create-spec-provider schema)
       :schema (if (find-ns 'langchain4clj.tools.schema)
                 ((resolve 'langchain4clj.tools.schema/create-schema-provider) schema)
                 (throw (ex-info "Prismatic Schema not available. Add [prismatic/schema \"1.4.1\"] to deps.edn"
                                 {:schema-type :schema})))
       :malli (if (find-ns 'langchain4clj.tools.malli)
                ((resolve 'langchain4clj.tools.malli/create-malli-provider) schema)
                (throw (ex-info "Malli not available. Add [metosin/malli \"0.16.1\"] to deps.edn"
                                {:schema-type :malli})))
       (throw (ex-info "Cannot determine schema type"
                       {:schema schema
                        :attempted-type schema-type}))))))

;; ============================================================================
;; JSON Schema Building
;; ============================================================================

(defn build-json-schema
  "Builds a JsonObjectSchema object from a Clojure map representation.
   
   Recursively converts JSON Schema maps to LangChain4j JsonSchemaElement objects.
   
   Supported types: string, integer, number, boolean, array, object
   
   Example input:
   {:type \"object\"
    :properties {\"name\" {:type \"string\" :description \"User name\"}
                 \"age\" {:type \"integer\"}}
    :required [\"name\"]}"
  [schema-map]
  (letfn [(build-element [elem-map]
            (let [type (:type elem-map)]
              (case type
                "string"
                (cond-> (JsonStringSchema/builder)
                  (:description elem-map) (.description (:description elem-map))
                  (:enum elem-map) (.enumValues (:enum elem-map))
                  true (.build))

                "integer"
                (cond-> (JsonIntegerSchema/builder)
                  (:description elem-map) (.description (:description elem-map))
                  true (.build))

                "number"
                (cond-> (JsonNumberSchema/builder)
                  (:description elem-map) (.description (:description elem-map))
                  true (.build))

                "boolean"
                (cond-> (JsonBooleanSchema/builder)
                  (:description elem-map) (.description (:description elem-map))
                  true (.build))

                "array"
                (let [items-schema (when-let [items (:items elem-map)]
                                     (build-element items))]
                  (cond-> (JsonArraySchema/builder)
                    items-schema (.items items-schema)
                    (:description elem-map) (.description (:description elem-map))
                    true (.build)))

                "object"
                (let [properties (:properties elem-map)
                      required (:required elem-map)
                      builder (JsonObjectSchema/builder)]
                  ;; Add each property
                  (doseq [[prop-name prop-schema] properties]
                    (.addProperty builder
                                  (name prop-name)
                                  (build-element prop-schema)))
                  ;; Add required fields
                  (when (seq required)
                    (.required builder (mapv name required)))
                  ;; Add description if present
                  (when-let [desc (:description elem-map)]
                    (.description builder desc))
                  (.build builder))

                ;; Default: treat as any type (use string as fallback)
                (-> (JsonStringSchema/builder)
                    (.build)))))]
    (build-element schema-map)))

;; ============================================================================
;; Parameter Normalization
;; ============================================================================

(defn- is-kebab-case?
  "Checks if a string contains characters beyond alphanumeric and underscore.
   Returns true if the string is kebab-case (has hyphens or other special chars).
   
   Client preference detection:
   - If string contains ONLY [a-zA-Z0-9_] → client's preferred format (preserve)
   - If string contains other chars (hyphens, etc.) → kebab-case (convert)
   
   Examples:
     \"userName\"     → false (camelCase, client preference)
     \"user_name\"   → false (snake_case, client preference)
     \"user-name\"   → true  (kebab-case, needs conversion)
     \"id\"          → false (simple word, client preference)"
  [s]
  (boolean (re-find #"[^a-zA-Z0-9_]" s)))

(defn- kebab->camel
  "Converts a kebab-case keyword or string to camelCase string.
   Preserves already camelCase/snake_case/etc strings.
   
   Examples:
     :pokemon-name    → \"pokemonName\"
     \"pokemon-name\"  → \"pokemonName\"
     :pokemonName     → \"pokemonName\"
     \"pokemonName\"   → \"pokemonName\"
     \"user_name\"     → \"user_name\" (preserved)"
  [k]
  (let [s (if (keyword? k) (name k) (str k))]
    (if (is-kebab-case? s)
      (let [parts (str/split s #"-")]
        (str (first parts)
             (apply str (map str/capitalize (rest parts)))))
      s)))

(defn- camel->kebab
  "Converts a camelCase keyword or string to kebab-case keyword.
   Preserves already kebab-case strings.
   
   Examples:
     \"pokemonName\"    → :pokemon-name
     :pokemonName     → :pokemon-name
     \"pokemon-name\"  → :pokemon-name
     :pokemon-name    → :pokemon-name"
  [k]
  (let [s (if (keyword? k) (name k) (str k))]
    (if (re-find #"[A-Z]" s)
      ;; Has uppercase letters - convert camelCase to kebab-case
      (keyword (str/lower-case
                (str/replace s #"([a-z])([A-Z])" "$1-$2")))
      ;; No uppercase - return as keyword
      (if (keyword? k) k (keyword s)))))

(defn normalize-tool-params
  "Normalizes parameter keys based on client preference detection.
   
   CLIENT PREFERENCE DETECTION:
   - If a string key contains ONLY alphanumeric + underscore, we assume that's 
     the client's preferred format and DON'T convert it to kebab-case
   - This respects clients using camelCase, snake_case, or other conventions
   
   CONVERSION RULES:
   1. Keyword keys: Always add string version
      - Kebab-case keyword (:pokemon-name) → camelCase string (\"pokemonName\")
      - Other keyword (:alreadyCamel) → string as-is (\"alreadyCamel\")
   
   2. String keys:
      - Kebab-case string (\"pokemon-name\") → add camelCase string (\"pokemonName\") + kebab keyword (:pokemon-name)
      - Non-kebab string (\"mixedCase\", \"user_name\") → NO CHANGE (client preference)
   
   WHY:
   - Clojure code uses kebab-case keywords naturally
   - External APIs (LLM providers) expect camelCase/snake_case strings
   - If client sends non-kebab format, they expect that format back
   - Spec validation requires keyword keys
   
   Performance: Uses regex-based detection with single-pass walk (< 1ms per call).
   
   Examples:
     {:pokemon-name \"pikachu\"} 
     → {:pokemon-name \"pikachu\", \"pokemonName\" \"pikachu\"}
     
     {\"pokemon-name\" \"pikachu\"}
     → {\"pokemon-name\" \"pikachu\", \"pokemonName\" \"pikachu\", :pokemon-name \"pikachu\"}
     
     {:alreadyCamel 1}
     → {:alreadyCamel 1, \"alreadyCamel\" 1}
     
     {\"mixedCase\" \"value\"}
     → {\"mixedCase\" \"value\"}  ; NO CHANGE - client preference detected
     
     {\"user_name\" 123}
     → {\"user_name\" 123}  ; NO CHANGE - respects snake_case clients"
  [params]
  (walk/postwalk
   (fn [form]
     (if (map? form)
       (reduce-kv
        (fn [m k v]
          (cond
            ;; Case 1: Keyword key (Clojure internal representation)
            (keyword? k)
            (let [k-str (name k)]
              (if (is-kebab-case? k-str)
                ;; Kebab-case keyword → add original + camelCase string
                (assoc m
                       k v
                       (kebab->camel k) v)
                ;; Non-kebab keyword → add original + string version as-is
                (assoc m
                       k v
                       k-str v)))

            ;; Case 2: String key (external/client representation)
            (string? k)
            (if (is-kebab-case? k)
              ;; Kebab-case string → add original + camelCase string + kebab keyword
              ;; (likely created by Clojure code, not external client)
              ;; Need kebab keyword for Spec validation
              (assoc m
                     k v
                     (kebab->camel k) v
                     (keyword k) v)
              ;; Non-kebab string → PRESERVE AS-IS (client preference!)
              ;; This is the key insight: if client sent camelCase/snake_case,
              ;; they expect that format back
              (assoc m k v))

            ;; Case 3: Other key type (rare, but handle it)
            :else
            (assoc m k v)))
        {}
        form)
       form))
   params))

;; ============================================================================
;; Tool Creation
;; ============================================================================

(defn create-tool-specification
  "Creates a LangChain4j ToolSpecification from a tool definition"
  [{:keys [name description parameters]}]
  (-> (ToolSpecification/builder)
      (.name name)
      (.description description)
      (.parameters (build-json-schema parameters))
      (.build)))

(defn create-tool
  "Creates a tool with automatic schema detection and validation.
   
   Parameters are automatically normalized from kebab-case to camelCase
   for compatibility with OpenAI's parameter naming conventions.
   
   IMPORTANT: params-schema is REQUIRED. Tools without schemas will never
   be called by OpenAI, as it won't know what parameters to send.
   
   For tools with no parameters, use :no-params as the schema.
   
   Options:
   - :name - Tool name (required)
   - :description - Tool description (required)
   - :params-schema - Schema for parameters (REQUIRED, use :no-params for tools with no params)
   - :result-schema - Schema for result (optional)
   - :schema-type - Force schema type (:spec, :schema, :malli)
   - :fn - Function to execute (required)
   
   Examples:
   
   ;; With Clojure Spec
   (create-tool {:name \"calculator\"
                 :description \"Performs calculations\"
                 :params-schema ::calc-params
                 :fn calculate})
   
   ;; With Plumatic Schema
   (create-tool {:name \"weather\"
                 :description \"Gets weather\"
                 :params-schema {:location s/Str}
                 :fn get-weather})
   
   ;; With no parameters
   (create-tool {:name \"get_all_items\"
                 :description \"Gets all items\"
                 :params-schema :no-params
                 :fn get-all-items})
   
   RECOMMENDATION: Use the `deftool` macro for a more idiomatic API:
   
   (deftool calculator
     \"Performs calculations\"
     {:x number? :y number?}
     [{:keys [x y]}]
     (+ x y))"
  [{:keys [name description params-schema result-schema schema-type]
    tool-fn :fn}]
  {:pre [(string? name)
         (string? description)
         (some? params-schema) ;; REQUIRED!
         (ifn? tool-fn)]}

  (let [;; Create providers for schemas if provided (skip if :no-params)
        params-provider (when (and params-schema (not= params-schema :no-params))
                          (create-provider params-schema schema-type))
        result-provider (when result-schema
                          (create-provider result-schema schema-type))

        ;; Create validated/coerced function wrapper with parameter normalization
        executor-fn (cond
                     ;; No parameters tool
                      (= params-schema :no-params)
                      (fn [params]
                        (tool-fn params))

                     ;; Both params and result validation
                      (and params-provider result-provider)
                      (fn [params]
                        (let [normalized-params (normalize-tool-params params)
                              coerced-params (p/coerce params-provider normalized-params)
                              _ (p/validate params-provider coerced-params)
                              result (tool-fn coerced-params)]
                          (p/validate result-provider result)))

                     ;; Only params validation
                      params-provider
                      (fn [params]
                        (let [normalized-params (normalize-tool-params params)
                              coerced-params (p/coerce params-provider normalized-params)]
                          (p/validate params-provider coerced-params)
                          (tool-fn coerced-params)))

                     ;; Only result validation
                      result-provider
                      (fn [params]
                        (let [normalized-params (normalize-tool-params params)
                              result (tool-fn normalized-params)]
                          (p/validate result-provider result)))

                     ;; No validation - still normalize for consistency
                      :else (fn [params]
                              (tool-fn (normalize-tool-params params))))

        ;; Generate JSON Schema for parameters
        json-schema (when params-provider
                      (p/to-json-schema params-provider))

        ;; Create tool specification
        spec (create-tool-specification
              {:name name
               :description description
               :parameters (or json-schema {:type "object"})})]

    ;; Return tool map
    {:name name
     :description description
     :specification spec
     :executor-fn executor-fn
     :params-schema params-schema
     :result-schema result-schema
     :schema-type (or schema-type
                      (when (and params-schema (not= params-schema :no-params))
                        (detect-schema-type params-schema)))}))

;; ============================================================================
;; Threading-First Tool API (New in v0.2.0)
;; ============================================================================

(defn tool
  "DEPRECATED: Use `deftool` macro or `create-tool` function instead.
  
  Creates a tool with threading-first support, but WITHOUT schema validation.
  
  WARNING: Tools without schemas will never be invoked by OpenAI!
  This function is deprecated because it encourages creating tools without
  schemas, which results in tools that silently fail to be called.
  
  Args:
    name - Tool name
    description - Tool description
    fn - Function to execute
  
  Migration:
  
  OLD (deprecated):
    (-> (tool \"calculator\" \"Performs math\" calculate-fn)
        (with-params-schema ::calc-params))
  
  NEW (recommended):
    (deftool calculator
      \"Performs math\"
      {:x number? :y number?}
      [{:keys [x y]}]
      (calculate x y))
  
  OR (programmatic):
    (create-tool {:name \"calculator\"
                  :description \"Performs math\"
                  :params-schema ::calc-params
                  :fn calculate-fn})"
  [name description fn]
  (println "WARNING: `tool` function is deprecated. Use `deftool` macro or `create-tool` function instead.")
  {:name name
   :description description
   :executor-fn fn
   :specification (create-tool-specification
                   {:name name
                    :description description
                    :parameters {:type "object"}})})

(defn with-params-schema
  "DEPRECATED: Use `deftool` macro or `create-tool` function instead.
  
  Adds parameter schema validation to a tool. Use in threading.
  
  WARNING: This function is part of the deprecated threading API.
  The threading pattern (tool + with-params-schema) is verbose and error-prone.
  
  Parameters are automatically normalized from kebab-case to camelCase.
  
  Migration:
  
  OLD (deprecated):
    (-> (tool \"calculator\" \"Math\" calc-fn)
        (with-params-schema ::calc-params))
  
  NEW (recommended):
    (deftool calculator
      \"Math\"
      {:x number? :y number?}
      [{:keys [x y]}]
      (calc x y))
  
  OR (programmatic):
    (create-tool {:name \"calculator\"
                  :description \"Math\"
                  :params-schema ::calc-params
                  :fn calc-fn})"
  [tool schema]
  (println "WARNING: `with-params-schema` is deprecated. Use `deftool` macro or `create-tool` function instead.")
  (let [provider (create-provider schema nil)
        json-schema (p/to-json-schema provider)
        validated-fn (fn [params]
                       (let [normalized-params (normalize-tool-params params)
                             coerced (p/coerce provider normalized-params)]
                         (p/validate provider coerced)
                         ((:executor-fn tool) coerced)))]
    (assoc tool
           :executor-fn validated-fn
           :params-schema schema
           :schema-type (detect-schema-type schema)
           :specification (create-tool-specification
                           {:name (:name tool)
                            :description (:description tool)
                            :parameters (or json-schema {:type "object"})}))))

(defn with-result-schema
  "Adds result schema validation to a tool. Use in threading.
  
  Example:
  (-> (tool \"query\" \"DB query\" query-fn)
      (with-result-schema ::query-result))"
  [tool schema]
  (let [provider (create-provider schema nil)
        validated-fn (fn [params]
                       (let [result ((:executor-fn tool) params)]
                         (p/validate provider result)
                         result))]
    (assoc tool
           :executor-fn validated-fn
           :result-schema schema)))

(defn with-validation
  "Enables validation for a tool with schemas. Use in threading.
  Alias for when schemas are already set via with-params-schema.
  
  Example:
  (-> (tool \"calc\" \"Math\" calc-fn)
      (with-params-schema ::params)
      (with-validation))"
  [tool]
  tool) ;; Validation is already added by with-params-schema

(defn with-description
  "Updates tool description. Use in threading.
  
  Example:
  (-> (tool \"api\" \"API\" api-fn)
      (with-description \"Enhanced API with retry logic\"))"
  [tool new-description]
  (assoc tool
         :description new-description
         :specification (create-tool-specification
                         {:name (:name tool)
                          :description new-description
                          :parameters (or (when-let [schema (:params-schema tool)]
                                            (p/to-json-schema
                                             (create-provider schema nil)))
                                          {:type "object"})})))

;; ============================================================================
;; Tool Execution
;; ============================================================================

(defn execute-tool
  "Executes a tool with the given arguments"
  [tool args]
  ((:executor-fn tool) args))

(defn find-tool
  "Finds a tool by name in a collection of tools"
  [tool-name tools]
  (first (filter #(= (:name %) tool-name) tools)))

(defn create-tool-result-message
  "Creates a ToolExecutionResultMessage for LangChain4j"
  [{:keys [tool-request result]}]
  (ToolExecutionResultMessage/from tool-request (pr-str result)))

;; ============================================================================
;; Batch Operations
;; ============================================================================

(defn validate-batch
  "Validates a batch of data against a schema"
  [schema-or-provider data-seq]
  (let [provider (if (satisfies? p/SchemaProvider schema-or-provider)
                   schema-or-provider
                   (create-provider schema-or-provider))]
    (mapv #(p/validate provider %) data-seq)))

(defn coerce-batch
  "Coerces a batch of data"
  [schema-or-provider data-seq]
  (let [provider (if (satisfies? p/SchemaProvider schema-or-provider)
                   schema-or-provider
                   (create-provider schema-or-provider))]
    (mapv #(p/coerce provider %) data-seq)))

;; ============================================================================
;; Tool Registry (optional)
;; ============================================================================

(def ^:private tool-registry (atom {}))

(defn register-tool!
  "Registers a tool globally for reuse"
  [tool]
  (swap! tool-registry assoc (:name tool) tool)
  tool)

(defn get-tool
  "Gets a tool from the registry"
  [tool-name]
  (get @tool-registry tool-name))

(defn list-tools
  "Lists all registered tools"
  []
  (keys @tool-registry))

(defn clear-tools!
  "Clears the tool registry"
  []
  (reset! tool-registry {}))

;; ============================================================================
;; Middleware Support
;; ============================================================================

(defn with-logging
  "Adds logging to a tool's execution"
  [tool]
  (update tool :executor-fn
          (fn [f]
            (fn [params]
              (println (str "[" (:name tool) "] Input: " (pr-str params)))
              (let [result (f params)]
                (println (str "[" (:name tool) "] Output: " (pr-str result)))
                result)))))

(defn with-timing
  "Adds timing information to a tool's execution"
  [tool]
  (update tool :executor-fn
          (fn [f]
            (fn [params]
              (let [start (System/currentTimeMillis)
                    result (f params)
                    elapsed (- (System/currentTimeMillis) start)]
                (println (str "[" (:name tool) "] Execution time: " elapsed "ms"))
                result)))))

(defn with-retry
  "Adds retry logic to a tool"
  ([tool]
   (with-retry tool 3))
  ([tool max-attempts]
   (update tool :executor-fn
           (fn [f]
             (fn [params]
               (loop [attempt 1]
                 (let [result (try
                                {:success true
                                 :value (f params)}
                                (catch Exception e
                                  {:success false
                                   :error e}))]
                   (if (:success result)
                     (:value result)
                     (if (< attempt max-attempts)
                       (do
                         (println (str "[" (:name tool) "] Attempt " attempt " failed, retrying..."))
                         (Thread/sleep (* 1000 attempt))
                         (recur (inc attempt)))
                       (throw (:error result)))))))))))

;; ============================================================================
;; Specs for Public API
;; ============================================================================

(s/fdef create-tool
  :args (s/cat :config ::specs/tool-definition)
  :ret map?)

(s/fdef execute-tool
  :args (s/cat :tool map? :args map?)
  :ret any?)

(s/fdef with-retry
  :args (s/alt :tool-only (s/cat :tool map?)
               :with-attempts (s/cat :tool map? :max-attempts pos-int?))
  :ret map?)

(s/fdef register-tool!
  :args (s/cat :tool map?)
  :ret nil?)

(s/fdef get-tool
  :args (s/cat :tool-name (s/or :keyword keyword? :string string?))
  :ret (s/nilable map?))
