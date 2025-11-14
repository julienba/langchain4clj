(ns rag-document-demo
  "Demonstrates document loading and splitting for RAG.
  
  This example shows how to:
  1. Load documents from files and directories
  2. Parse different file formats (text, PDF)
  3. Split documents into segments
  4. Use threading-first patterns
  
  Run with: clojure -M:dev -m rag-document-demo"
  (:require [nandoolle.langchain4clj.rag.document :as doc]))

;; =============================================================================
;; Example 1: Load Single Document
;; =============================================================================

(defn example-1-load-single-document []
  (println "\n=== Example 1: Load Single Document ===\n")

  ;; Load a text file (default parser)
  (comment
    (let [doc (doc/load-document "path/to/your/file.txt")]
      (println "Document loaded:")
      (println "  Text length:" (count (:text doc)))
      (println "  Metadata:" (:metadata doc))))

  ;; Load a PDF file with Apache Tika
  (comment
    (let [doc (doc/load-document "path/to/your/file.pdf"
                                 {:parser :apache-tika})]
      (println "PDF loaded:")
      (println "  Text length:" (count (:text doc)))
      (println "  Metadata:" (:metadata doc))))

  (println "✓ Load single document example (see code in comment blocks)"))

;; =============================================================================
;; Example 2: Load Multiple Documents
;; =============================================================================

(defn example-2-load-multiple-documents []
  (println "\n=== Example 2: Load Multiple Documents ===\n")

  ;; Load all files from a directory
  (comment
    (let [docs (doc/load-documents "path/to/docs")]
      (println "Loaded" (count docs) "documents")))

  ;; Load recursively with Apache Tika
  (comment
    (let [docs (doc/load-documents "path/to/docs"
                                   {:parser :apache-tika
                                    :recursive? true})]
      (println "Loaded" (count docs) "documents (recursive)")))

  ;; Load only specific files using glob
  (comment
    (let [docs (doc/load-documents "path/to/docs"
                                   {:glob "*.pdf"
                                    :parser :apache-tika})]
      (println "Loaded" (count docs) "PDF files")))

  (println "✓ Load multiple documents example (see code in comment blocks)"))

;; =============================================================================
;; Example 3: Load from URL
;; =============================================================================

(defn example-3-load-from-url []
  (println "\n=== Example 3: Load from URL ===\n")

  ;; Load document from URL
  (comment
    (let [doc (doc/load-from-url "https://example.com/document.txt")]
      (println "Document from URL:")
      (println "  Text length:" (count (:text doc)))))

  ;; Load PDF from URL with Apache Tika
  (comment
    (let [doc (doc/load-from-url "https://example.com/whitepaper.pdf"
                                 {:parser :apache-tika})]
      (println "PDF from URL:")
      (println "  Text length:" (count (:text doc)))))

  (println "✓ Load from URL example (see code in comment blocks)"))

;; =============================================================================
;; Example 4: Document Splitting
;; =============================================================================

(defn example-4-document-splitting []
  (println "\n=== Example 4: Document Splitting ===\n")

  ;; Split with token-based sizing
  (comment
    (let [doc (doc/load-document "path/to/large-file.txt")
          splitter (doc/recursive-splitter {:max-segment-size 500
                                            :max-overlap 50})
          segments (doc/split-document doc splitter)]
      (println "Split document into" (count segments) "segments")
      (println "First segment:" (subs (:text (first segments)) 0 100) "...")))

  ;; Split with character-based sizing
  (comment
    (let [doc (doc/load-document "path/to/file.txt")
          splitter (doc/recursive-splitter {:max-segment-size 1000
                                            :max-overlap 100
                                            :unit :chars})
          segments (doc/split-document doc splitter)]
      (println "Split into" (count segments) "segments (char-based)")))

  (println "✓ Document splitting example (see code in comment blocks)"))

;; =============================================================================
;; Example 5: Split Multiple Documents
;; =============================================================================

(defn example-5-split-multiple-documents []
  (println "\n=== Example 5: Split Multiple Documents ===\n")

  ;; Load and split multiple documents with defaults
  (comment
    (let [docs (doc/load-documents "path/to/docs")
          segments (doc/split-documents docs)]
      (println "Split" (count docs) "documents into" (count segments) "total segments")))

  ;; Custom split settings
  (comment
    (let [docs (doc/load-documents "path/to/docs" {:recursive? true})
          segments (doc/split-documents docs {:max-segment-size 1000
                                              :max-overlap 100})]
      (println "Total segments:" (count segments))))

  (println "✓ Split multiple documents example (see code in comment blocks)"))

;; =============================================================================
;; Example 6: Threading-First Pattern
;; =============================================================================

(defn example-6-threading-first []
  (println "\n=== Example 6: Threading-First Pattern ===\n")

  ;; Complete pipeline using threading-first
  (comment
    (let [segments (-> "path/to/docs"
                       (doc/load-documents {:parser :apache-tika
                                            :recursive? true})
                       (doc/split-documents {:max-segment-size 500
                                             :max-overlap 50}))]
      (println "Pipeline complete!")
      (println "Total segments:" (count segments))
      (println "Sample segment:")
      (println "  Text:" (subs (:text (first segments)) 0 100) "...")
      (println "  Metadata:" (:metadata (first segments)))))

  (println "✓ Threading-first pattern example (see code in comment blocks)"))

;; =============================================================================
;; Example 7: Complete RAG Document Preparation
;; =============================================================================

(defn example-7-complete-rag-prep []
  (println "\n=== Example 7: Complete RAG Document Preparation ===\n")

  ;; Complete workflow: load → split → prepare for embedding
  (comment
    (defn prepare-documents-for-rag [docs-path]
      "Loads and splits documents, ready for embedding and storage."
      (-> docs-path
          (doc/load-documents {:parser :apache-tika
                               :recursive? true})
          (doc/split-documents {:max-segment-size 500
                                :max-overlap 50})))

    ;; Use it
    (let [segments (prepare-documents-for-rag "path/to/knowledge-base")]
      (println "RAG preparation complete!")
      (println "Documents ready for embedding:" (count segments))

      ;; Each segment has:
      (let [segment (first segments)]
        (println "\nSegment structure:")
        (println "  :text ->" (subs (:text segment) 0 50) "...")
        (println "  :metadata ->" (:metadata segment))
        (println "  :java-object -> (for interop)"))))

  (println "✓ Complete RAG preparation example (see code in comment blocks)"))

;; =============================================================================
;; Example 8: Error Handling
;; =============================================================================

(defn example-8-error-handling []
  (println "\n=== Example 8: Error Handling ===\n")

  ;; Handle missing files gracefully
  (comment
    (try
      (doc/load-document "nonexistent.txt")
      (catch Exception e
        (println "Error loading document:" (.getMessage e)))))

  ;; Validate splitter configuration
  (comment
    (try
      (doc/recursive-splitter {:max-overlap 50}) ; Missing :max-segment-size
      (catch IllegalArgumentException e
        (println "Invalid configuration:" (.getMessage e)))))

  ;; Invalid parser type
  (comment
    (try
      (doc/load-document "file.txt" {:parser :invalid-parser})
      (catch IllegalArgumentException e
        (println "Invalid parser:" (.getMessage e)))))

  (println "✓ Error handling example (see code in comment blocks)"))

;; =============================================================================
;; Main Function
;; =============================================================================

(defn -main []
  (println "╔════════════════════════════════════════════════════════════╗")
  (println "║  LangChain4Clj - RAG Document Loading & Splitting Demo    ║")
  (println "╚════════════════════════════════════════════════════════════╝")

  (example-1-load-single-document)
  (example-2-load-multiple-documents)
  (example-3-load-from-url)
  (example-4-document-splitting)
  (example-5-split-multiple-documents)
  (example-6-threading-first)
  (example-7-complete-rag-prep)
  (example-8-error-handling)

  (println "\n" (str (char 0x2705)) "All examples completed!")
  (println "\nNote: Examples are in comment blocks. Uncomment and adjust paths to run.")
  (println "\nNext steps:")
  (println "  1. Load your documents using doc/load-documents")
  (println "  2. Split them using doc/split-documents")
  (println "  3. Ready for Phase 3: Embedding Models!")
  (println))

;; Run examples
(comment
  ;; Run all examples
  (-main)

  ;; Or run individual examples
  (example-6-threading-first))
