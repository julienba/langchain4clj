(println "=== Discovering LangChain4j 1.0.0 API ===\n")

;; Try different package combinations
(def classes-to-find
  ["UserMessage"
   "SystemMessage"
   "AiMessage"
   "AssistantMessage"
   "ChatMessage"
   "ToolExecutionResultMessage"])

(def packages-to-try
  ["dev.langchain4j.data.message"
   "dev.langchain4j.model.chat"
   "dev.langchain4j.model.chat.message"
   "dev.langchain4j.message"])

(doseq [cls classes-to-find]
  (println (str "\n" cls ":"))
  (let [found (atom false)]
    (doseq [pkg packages-to-try]
      (try
        (let [full-class (str pkg "." cls)]
          (Class/forName full-class)
          (println (str "  ✅ " full-class))
          (reset! found true))
        (catch ClassNotFoundException e
          nil)))
    (when-not @found
      (println "  ❌ NOT FOUND in any package"))))

(println "\n=== Discovery Complete ===")
