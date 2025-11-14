(println "=== Testing Specific Imports ===\n")

;; Test each import individually
(defn test-import [class-name]
  (try
    (eval `(import '~(symbol class-name)))
    (println (str "✅ " class-name))
    (catch Exception e
      (println (str "❌ " class-name " - " (.getMessage e))))))

(println "1. Testing message classes:")
(test-import "dev.langchain4j.data.message.UserMessage")
(test-import "dev.langchain4j.data.message.SystemMessage")
(test-import "dev.langchain4j.data.message.AiMessage")
(test-import "dev.langchain4j.data.message.ChatMessage")
(test-import "dev.langchain4j.data.message.ToolExecutionResultMessage")

(println "\n2. Testing model classes:")
(test-import "dev.langchain4j.model.chat.ChatLanguageModel")
(test-import "dev.langchain4j.model.chat.ChatModel")
(test-import "dev.langchain4j.model.openai.OpenAiChatModel")
(test-import "dev.langchain4j.model.anthropic.AnthropicChatModel")

(println "\n3. Testing request/response classes:")
(test-import "dev.langchain4j.model.chat.request.ChatRequest")
(test-import "dev.langchain4j.model.chat.response.ChatResponse")

(println "\n4. Testing other classes:")
(test-import "dev.langchain4j.model.output.Response")
(test-import "dev.langchain4j.model.output.structured.ResponseFormat")
(test-import "dev.langchain4j.agent.tool.ToolExecutionRequest")
(test-import "dev.langchain4j.agent.tool.ToolSpecification")
(test-import "dev.langchain4j.memory.chat.MessageWindowChatMemory")

(println "\n=== Test Complete ===")
