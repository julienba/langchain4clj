(println "=== Inspecting ToolExecutionRequest$Builder ===\n")

(try
  (let [builder-cls (Class/forName "dev.langchain4j.agent.tool.ToolExecutionRequest$Builder")
        methods (.getMethods builder-cls)
        relevant-methods (filter #(not (re-find #"(equals|hashCode|toString|wait|notify|getClass)" (.getName %))) methods)]

    (println "Builder methods:")
    (doseq [m (sort-by #(.getName %) relevant-methods)]
      (when (not (java.lang.reflect.Modifier/isStatic (.getModifiers m)))
        (let [params (.getParameterTypes m)
              return-type (.getReturnType m)]
          (println (str "  - " (.getName m)))
          (println (str "      params: " (count params)))
          (when (> (count params) 0)
            (doseq [p params]
              (println (str "        - " (.getName p)))))
          (println (str "      returns: " (.getName return-type)))))))
  (catch Exception e
    (println (str "Error: " (.getMessage e)))))

(println "\n=== Complete ===")
