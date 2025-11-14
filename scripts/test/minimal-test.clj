(println "=== Minimal Test ===")

;; Test 1: Can we load Clojure core libraries?
(println "\n1. Testing Clojure core...")
(try
  (require '[clojure.string :as str])
  (println "✅ clojure.string works")
  (catch Exception e
    (println "❌ Failed:" (.getMessage e))))

;; Test 2: Can we access LangChain4j classes?
(println "\n2. Testing LangChain4j class loading...")
(try
  (import 'dev.langchain4j.model.chat.ChatLanguageModel)
  (println "✅ ChatLanguageModel found in classpath")
  (catch Exception e
    (println "❌ ChatLanguageModel not found:" (.getMessage e))))

;; Test 3: Can we list what's in the classpath?
(println "\n3. Checking classpath for langchain4j...")
(let [cp (System/getProperty "java.class.path")
      entries (clojure.string/split cp #":")
      langchain-entries (filter #(re-find #"langchain" %) entries)]
  (if (empty? langchain-entries)
    (println "❌ No langchain4j jars found in classpath!")
    (do
      (println "✅ Found langchain4j jars:")
      (doseq [entry langchain-entries]
        (println "  -" entry)))))

;; Test 4: Try to check what classes are in langchain4j-core
(println "\n4. Attempting to load simple namespace without Java imports...")
(try
  (ns test.simple
    "Simple test namespace"
    (:require [clojure.string :as str]))

  (defn simple-fn []
    "A simple function")

  (println "✅ Simple namespace without Java imports works")
  (catch Exception e
    (println "❌ Even simple namespace failed:" (.getMessage e))))

(println "\n=== Test Complete ===")
