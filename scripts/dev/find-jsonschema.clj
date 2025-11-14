(println "=== Finding JsonSchema ===\n")

(def packages-to-try
  ["dev.langchain4j.model.output.structured.JsonSchema"
   "dev.langchain4j.model.output.JsonSchema"
   "dev.langchain4j.model.chat.request.JsonSchema"
   "dev.langchain4j.model.chat.JsonSchema"
   "dev.langchain4j.agent.tool.JsonSchema"
   "dev.langchain4j.data.message.JsonSchema"])

(doseq [pkg packages-to-try]
  (try
    (Class/forName pkg)
    (println (str "✅ FOUND: " pkg))
    (catch ClassNotFoundException e
      (println (str "❌ " pkg)))))

(println "\n=== Search Complete ===")
