(println "=== Inspecting ToolExecutionRequest ===\n")

(try
  (let [cls (Class/forName "dev.langchain4j.agent.tool.ToolExecutionRequest")]
    (println (str "Is interface? " (.isInterface cls)))
    (println (str "Is class? " (not (.isInterface cls))))
    (println (str "\nConstructors:"))
    (doseq [c (.getConstructors cls)]
      (let [params (.getParameterTypes c)]
        (println (str "  Constructor with " (count params) " params:"))
        (doseq [p params]
          (println (str "    - " (.getName p))))))

    (println (str "\nStatic factory methods:"))
    (let [methods (.getMethods cls)
          static-methods (filter #(java.lang.reflect.Modifier/isStatic (.getModifiers %)) methods)
          builder-like (filter #(or (re-find #"(?i)builder" (.getName %))
                                    (re-find #"(?i)create" (.getName %))
                                    (re-find #"(?i)of" (.getName %)))
                               static-methods)]
      (doseq [m builder-like]
        (println (str "  - " (.getName m) " returns " (.getName (.getReturnType m)))))))
  (catch Exception e
    (println (str "Error: " (.getMessage e)))))

(println "\n=== Complete ===")
