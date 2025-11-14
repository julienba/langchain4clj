# Test Helpers Guide - LangChain4Clj

## Usando `create-java-mock-chat-model`

### Problema Resolvido

No LangChain4j 1.0.0, `ChatModel` tem **4 overloads** do método `chat`:

1. `chat(String message) -> String`
2. `chat(List<ChatMessage> messages) -> ChatResponse`
3. `chat(ChatRequest request) -> ChatResponse`
4. `chat(ChatMessage... messages) -> ChatResponse`

**Antes:** Mocks incompletos causavam erros `RuntimeException: Not implemented at ChatModel.chat`

**Agora:** Use `create-java-mock-chat-model` que implementa todos os overloads!

---

## ✅ Como Usar

### Resposta Simples (String)

```clojure
(require '[nandoolle.langchain4clj.test-utils :as test-utils])

(deftest meu-teste
  (let [model (test-utils/create-java-mock-chat-model "Resposta fixa")]
    ;; Funciona com todos os overloads!
    (is (= "Resposta fixa" (core/chat model "Oi")))
    (is (= "Resposta fixa" (.chat model "Oi")))
    (is (instance? ChatResponse (.chat model (ArrayList. [(UserMessage. "Oi")]))))))
```

### Resposta Dinâmica (Função)

```clojure
(deftest teste-eco
  (let [model (test-utils/create-java-mock-chat-model
                (fn [msg] (str "Eco: " msg)))]
    (is (= "Eco: Oi" (core/chat model "Oi")))
    (is (= "Eco: Olá" (core/chat model "Olá")))))
```

### Lógica Condicional

```clojure
(deftest teste-condicional
  (let [model (test-utils/create-java-mock-chat-model
                (fn [msg]
                  (cond
                    (.contains msg "clima") "Está ensolarado"
                    (.contains msg "hora") "14:30"
                    :else "Não entendi")))]
    (is (= "Está ensolarado" (core/chat model "Qual o clima?")))
    (is (= "14:30" (core/chat model "Que horas são?")))
    (is (= "Não entendi" (core/chat model "xyz")))))
```

### Com Tools

```clojure
(deftest teste-com-tools
  (let [calculator-tool (tools/create-tool
                          {:name "calculator"
                           :description "Calcula"
                           :params-schema {:x :int :y :int}
                           :fn (fn [params] (+ (:x params) (:y params)))})
        
        model (test-utils/create-java-mock-chat-model
                (fn [msg]
                  (if (.contains msg "soma")
                    "Usando calculator tool..."
                    "Resposta normal")))]
    
    (is (= "Usando calculator tool..."
           (core/chat model "Faça a soma" {:tools [calculator-tool]})))))
```

### Com Assistant

```clojure
(deftest teste-assistant
  (let [model (test-utils/create-java-mock-chat-model "Resposta do assistente")
        assistant-fn (assistant/create-assistant {:model model})]
    
    (is (= "Resposta do assistente"
           (assistant-fn "Pergunta")))))
```

### Simulando Conversação

```clojure
(deftest teste-conversa
  (let [respostas (atom ["Primeira resposta"
                         "Segunda resposta"
                         "Terceira resposta"])
        model (test-utils/create-java-mock-chat-model
                (fn [msg]
                  (let [resp (first @respostas)]
                    (swap! respostas rest)
                    resp)))]
    
    (is (= "Primeira resposta" (core/chat model "Msg 1")))
    (is (= "Segunda resposta" (core/chat model "Msg 2")))
    (is (= "Terceira resposta" (core/chat model "Msg 3")))))
```

### Capturando Histórico

```clojure
(deftest teste-historico
  (let [historico (atom [])
        model (test-utils/create-java-mock-chat-model
                (fn [msg]
                  (swap! historico conj msg)
                  "OK"))]
    
    (core/chat model "Primeira mensagem")
    (core/chat model "Segunda mensagem")
    
    (is (= ["Primeira mensagem" "Segunda mensagem"]
           @historico))))
```

---

## 🔧 Migrando Testes Antigos

### Antes (quebrado em 1.0.0)

```clojure
(deftest teste-antigo
  (let [model (reify ChatModel
                (^String chat [_ ^String message]
                  "Resposta"))]
    ;; ❌ Falta implementar outros overloads!
    ;; Vai quebrar quando usar com tools, assistant, etc
    (is (= "Resposta" (.chat model "Oi")))))
```

### Depois (funciona perfeitamente)

```clojure
(deftest teste-novo
  (let [model (test-utils/create-java-mock-chat-model "Resposta")]
    ;; ✅ Todos os overloads implementados!
    (is (= "Resposta" (.chat model "Oi")))
    (is (= "Resposta" (core/chat model "Oi")))
    (is (= "Resposta" (core/chat model "Oi" {:tools []})))))
```

---

## 📚 Exemplos Completos

### Teste de Core

```clojure
(ns nandoolle.langchain4clj.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [nandoolle.langchain4clj.core :as core]
            [nandoolle.langchain4clj.test-utils :as test-utils]))

(deftest test-chat-basic
  (testing "Chat básico funciona"
    (let [model (test-utils/create-java-mock-chat-model "Hello World")]
      (is (= "Hello World" (core/chat model "Hi"))))))

(deftest test-chat-with-options
  (testing "Chat com opções funciona"
    (let [model (test-utils/create-java-mock-chat-model "Response")]
      (is (= "Response" (core/chat model "Hi" {:temperature 0.8}))))))
```

### Teste de Tools

```clojure
(ns nandoolle.langchain4clj.tools-test
  (:require [clojure.test :refer [deftest is testing]]
            [nandoolle.langchain4clj.tools :as tools]
            [nandoolle.langchain4clj.test-utils :as test-utils]))

(deftest test-tool-execution
  (testing "Tool execution works"
    (let [calculator (tools/create-tool
                       {:name "add"
                        :description "Adds two numbers"
                        :params-schema {:a :int :b :int}
                        :fn (fn [params] (+ (:a params) (:b params)))})
          result (tools/execute-tool calculator {:a 2 :b 3})]
      (is (= 5 result)))))
```

---

## 🎯 Checklist de Migração

Ao migrar seus testes para usar o novo helper:

- [ ] Substitua `reify ChatModel` por `test-utils/create-java-mock-chat-model`
- [ ] Remova implementações parciais de `chat`
- [ ] Simplifique lógica de mock quando possível
- [ ] Teste com diferentes overloads (String, List, ChatRequest)
- [ ] Valide com tools, assistant e structured output

---

**Resultado:** Testes mais simples, mais robustos e compatíveis com LangChain4j 1.0.0! ✅
