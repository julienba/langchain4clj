(println "=== Finding ResponseFormat ===\n")

(def packages-to-try
  ["dev.langchain4j.model.output.structured.ResponseFormat"
   "dev.langchain4j.model.output.ResponseFormat"
   "dev.langchain4j.model.chat.ResponseFormat"
   "dev.langchain4j.model.chat.request.ResponseFormat"
   "dev.langchain4j.model.chat.response.ResponseFormat"
   "dev.langchain4j.data.message.ResponseFormat"])

(doseq [pkg packages-to-try]
  (try
    (Class/forName pkg)
    (println (str "✅ FOUND: " pkg))
    (catch ClassNotFoundException e
      (println (str "❌ " pkg)))))

(println "\n=== Search Complete ===")
