#!/usr/bin/env clojure

(println "=== LangChain4Clj Test Runner ===\n")

;; Try to load core namespaces
(println "Loading core namespace...")
(try
  (require '[nandoolle.langchain4clj.core :as core])
  (println "✅ core.clj loaded successfully")
  (catch Exception e
    (println "❌ core.clj failed:" (.getMessage e))))

(println "\nLoading tools namespace...")
(try
  (require '[nandoolle.langchain4clj.tools :as tools])
  (println "✅ tools.clj loaded successfully")
  (catch Exception e
    (println "❌ tools.clj failed:" (.getMessage e))))

(println "\nLoading agents namespace...")
(try
  (require '[nandoolle.langchain4clj.agents :as agents])
  (println "✅ agents.clj loaded successfully")
  (catch Exception e
    (println "❌ agents.clj failed:" (.getMessage e))))

(println "\nLoading assistant namespace...")
(try
  (require '[nandoolle.langchain4clj.assistant :as assistant])
  (println "✅ assistant.clj loaded successfully")
  (catch Exception e
    (println "❌ assistant.clj failed:" (.getMessage e))))

(println "\nLoading structured namespace...")
(try
  (require '[nandoolle.langchain4clj.structured :as structured])
  (println "✅ structured.clj loaded successfully")
  (catch Exception e
    (println "❌ structured.clj failed:" (.getMessage e))))

;; Try to load test namespaces
(println "\n=== Loading Test Namespaces ===\n")

(println "Loading core-test...")
(try
  (require '[nandoolle.langchain4clj.core-test])
  (println "✅ core-test.clj loaded successfully")
  (catch Exception e
    (println "❌ core-test.clj failed:" (.getMessage e))))

(println "\nLoading tools-test...")
(try
  (require '[nandoolle.langchain4clj.tools-test])
  (println "✅ tools-test.clj loaded successfully")
  (catch Exception e
    (println "❌ tools-test.clj failed:" (.getMessage e))))

(println "\nLoading agents-test...")
(try
  (require '[nandoolle.langchain4clj.agents-test])
  (println "✅ agents-test.clj loaded successfully")
  (catch Exception e
    (println "❌ agents-test.clj failed:" (.getMessage e))))

(println "\nLoading assistant-test...")
(try
  (require '[nandoolle.langchain4clj.assistant-test])
  (println "✅ assistant-test.clj loaded successfully")
  (catch Exception e
    (println "❌ assistant-test.clj failed:" (.getMessage e))))

(println "\nLoading structured-test...")
(try
  (require '[nandoolle.langchain4clj.structured-test])
  (println "✅ structured-test.clj loaded successfully")
  (catch Exception e
    (println "❌ structured-test.clj failed:" (.getMessage e))))

(println "\n=== Test Runner Complete ===")
