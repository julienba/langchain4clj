(println "=== Inspecting ToolSpecification$Builder.parameters() ===\n")

(try
  (let [builder-cls (Class/forName "dev.langchain4j.agent.tool.ToolSpecification$Builder")
        parameters-methods (filter #(= "parameters" (.getName %)) (.getMethods builder-cls))]

    (println "Found parameters methods:")
    (doseq [m parameters-methods]
      (let [param-types (.getParameterTypes m)]
        (println (str "\n  Method: parameters"))
        (println (str "  Parameter count: " (count param-types)))
        (println (str "  Parameter types:"))
        (doseq [pt param-types]
          (println (str "    - " (.getName pt))))
        (println (str "  Return type: " (.getName (.getReturnType m)))))))
  (catch Exception e
    (println (str "Error: " (.getMessage e)))))

(println "\n=== Complete ===")
