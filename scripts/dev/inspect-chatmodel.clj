(println "=== Inspecting ChatModel ===\n")

(try
  (let [cls (Class/forName "dev.langchain4j.model.chat.ChatModel")]
    (println (str "Is interface? " (.isInterface cls)))
    (println (str "Is abstract? " (java.lang.reflect.Modifier/isAbstract (.getModifiers cls))))

    (println "\nMethods:")
    (doseq [m (.getMethods cls)]
      (when (not (re-find #"(equals|hashCode|toString|wait|notify|getClass)" (.getName m)))
        (println (str "  - " (.getName m)))))

    ;; Try to find a builder or factory
    (println "\nLooking for test/mock utilities...")
    (let [methods (.getMethods cls)
          static-methods (filter #(java.lang.reflect.Modifier/isStatic (.getModifiers %)) methods)]
      (doseq [m static-methods]
        (println (str "  Static: " (.getName m))))))
  (catch Exception e
    (println (str "Error: " (.getMessage e)))))

(println "\n=== Complete ===")
