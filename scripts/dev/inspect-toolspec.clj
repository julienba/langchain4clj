(println "=== Inspecting ToolSpecification ===\n")

(try
  (let [cls (Class/forName "dev.langchain4j.agent.tool.ToolSpecification")
        methods (.getMethods cls)
        static-methods (filter #(java.lang.reflect.Modifier/isStatic (.getModifiers %)) methods)
        builder-methods (filter #(re-find #"(?i)builder" (.getName %)) static-methods)]

    (println "Static builder-related methods:")
    (doseq [m builder-methods]
      (println (str "  - " (.getName m) " returns " (.getName (.getReturnType m)))))

    (println "\n\nAll static methods:")
    (doseq [m static-methods]
      (println (str "  - " (.getName m) " returns " (.getName (.getReturnType m)))))

    ;; Try to get the Builder class
    (println "\n\nTrying to find Builder class:")
    (try
      (let [builder-cls (Class/forName "dev.langchain4j.agent.tool.ToolSpecification$Builder")
            builder-methods (.getMethods builder-cls)]
        (println "✅ Found ToolSpecification$Builder")
        (println "\nBuilder methods that mention 'parameter' or 'schema':")
        (doseq [m builder-methods]
          (when (re-find #"(?i)(parameter|schema)" (.getName m))
            (println (str "  - " (.getName m)
                          " params: " (count (.getParameterTypes m))
                          " returns: " (.getName (.getReturnType m)))))))
      (catch Exception e
        (println (str "❌ Builder not found: " (.getMessage e))))))
  (catch Exception e
    (println (str "Could not inspect ToolSpecification: " (.getMessage e)))))

(println "\n=== Search Complete ===")
