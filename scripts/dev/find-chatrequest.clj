(println "=== Finding ChatRequest and Builder ===\n")

(def packages-to-try
  [;; ChatRequest locations
   "dev.langchain4j.model.chat.request.ChatRequest"
   "dev.langchain4j.model.chat.ChatRequest"
   "dev.langchain4j.data.message.ChatRequest"

   ;; Builder as inner class
   "dev.langchain4j.model.chat.request.ChatRequest$ChatRequestBuilder"
   "dev.langchain4j.model.chat.request.ChatRequest$Builder"
   "dev.langchain4j.model.chat.ChatRequest$ChatRequestBuilder"
   "dev.langchain4j.model.chat.ChatRequest$Builder"])

(doseq [pkg packages-to-try]
  (try
    (Class/forName pkg)
    (println (str "✅ FOUND: " pkg))
    (catch ClassNotFoundException e
      (println (str "❌ " pkg)))))

;; Try to find methods on ChatRequest if it exists
(println "\n=== Checking ChatRequest methods ===")
(try
  (let [cls (Class/forName "dev.langchain4j.model.chat.request.ChatRequest")
        methods (.getMethods cls)
        static-methods (filter #(java.lang.reflect.Modifier/isStatic (.getModifiers %)) methods)
        builder-methods (filter #(re-find #"(?i)builder" (.getName %)) static-methods)]
    (println "Static builder-related methods:")
    (doseq [m builder-methods]
      (println (str "  - " (.getName m) " returns " (.getName (.getReturnType m))))))
  (catch Exception e
    (println (str "Could not inspect ChatRequest: " (.getMessage e)))))

(println "\n=== Search Complete ===")
