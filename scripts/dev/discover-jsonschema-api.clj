(println "=== Discovering JsonObjectSchema API ===\n")

(import '[dev.langchain4j.model.chat.request.json
          JsonObjectSchema
          JsonStringSchema
          JsonIntegerSchema
          JsonNumberSchema
          JsonBooleanSchema
          JsonArraySchema
          JsonEnumSchema])

;; Inspect JsonObjectSchema
(println "JsonObjectSchema methods:")
(doseq [method (.getMethods JsonObjectSchema)]
  (when (or (.contains (.getName method) "builder")
            (.contains (.getName method) "property")
            (.contains (.getName method) "properties")
            (.contains (.getName method) "required"))
    (println (str "  " (.getName method)
                  " - params: " (seq (.getParameterTypes method))
                  " - return: " (.getReturnType method)))))

(println "\n=== Testing Simple Schema Creation ===\n")

;; Try creating a simple string property
(try
  (let [string-schema (-> (JsonStringSchema/builder)
                          (.description "A string property")
                          (.build))]
    (println "✅ Created JsonStringSchema:" string-schema))
  (catch Exception e
    (println "❌ Error creating JsonStringSchema:" (.getMessage e))))

;; Try creating an integer property
(try
  (let [int-schema (-> (JsonIntegerSchema/builder)
                       (.description "An integer property")
                       (.build))]
    (println "✅ Created JsonIntegerSchema:" int-schema))
  (catch Exception e
    (println "❌ Error creating JsonIntegerSchema:" (.getMessage e))))

;; Try creating object schema with properties
(try
  (let [name-prop (-> (JsonStringSchema/builder)
                      (.description "Person's name")
                      (.build))
        age-prop (-> (JsonIntegerSchema/builder)
                     (.description "Person's age")
                     (.build))
        person-schema (-> (JsonObjectSchema/builder)
                          (.addProperty "name" name-prop)
                          (.addProperty "age" age-prop)
                          (.required ["name"])
                          (.build))]
    (println "✅ Created JsonObjectSchema:" person-schema))
  (catch Exception e
    (println "❌ Error creating JsonObjectSchema:" (.getMessage e))))

(println "\n=== Schema Creation Complete ===")
