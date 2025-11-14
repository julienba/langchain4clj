(println "=== Inspecting ChatModel.chat methods ===\n")

(try
  (let [cls (Class/forName "dev.langchain4j.model.chat.ChatModel")
        methods (.getMethods cls)
        chat-methods (filter #(= "chat" (.getName %)) methods)]

    (println "Found" (count chat-methods) "chat methods:\n")
    (doseq [m chat-methods]
      (let [params (.getParameterTypes m)
            return-type (.getReturnType m)]
        (println "Method: chat")
        (println (str "  Parameters (" (count params) "):"))
        (doseq [p params]
          (println (str "    - " (.getName p))))
        (println (str "  Returns: " (.getName return-type)))
        (println))))
  (catch Exception e
    (println (str "Error: " (.getMessage e)))))

(println "=== Complete ===")
