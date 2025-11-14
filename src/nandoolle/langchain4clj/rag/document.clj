(ns nandoolle.langchain4clj.rag.document
  "Document loading and splitting for RAG (Retrieval-Augmented Generation).
  
  This namespace provides idiomatic Clojure wrappers for LangChain4j's document
  loading and processing capabilities. It supports loading documents from files,
  directories, and URLs, with automatic format detection via Apache Tika.
  
  Key features:
  - Load single documents or entire directories
  - Multiple parser support (text, PDF, Apache Tika auto-detection)
  - Document splitting with configurable overlap
  - Metadata preservation and extraction
  - Threading-first API for composition"
  (:require [nandoolle.langchain4clj.macros :as macros])
  (:import [dev.langchain4j.data.document Document DocumentParser DocumentSplitter Metadata]
           [dev.langchain4j.data.document.loader FileSystemDocumentLoader UrlDocumentLoader]
           [dev.langchain4j.data.document.parser TextDocumentParser]
           [dev.langchain4j.data.document.parser.apache.tika ApacheTikaDocumentParser]
           [dev.langchain4j.data.document.splitter DocumentSplitters]
           [dev.langchain4j.data.segment TextSegment]
           [java.nio.file Path Paths]
           [java.net URL]))

;; =============================================================================
;; Document Parser Multimethod
;; =============================================================================

(defmulti create-parser
  "Creates a DocumentParser based on the parser type keyword.
  
  Supported types:
  - :text - TextDocumentParser (default, basic text parsing)
  - :apache-tika - ApacheTikaDocumentParser (auto-detects format, supports PDF/DOCX/etc)
  
  Examples:
  
  (create-parser :text)
  (create-parser :apache-tika)"
  identity)

(defmethod create-parser :text
  [_]
  (TextDocumentParser.))

(defmethod create-parser :apache-tika
  [_]
  (ApacheTikaDocumentParser.))

(defmethod create-parser :default
  [parser-type]
  (if (instance? DocumentParser parser-type)
    parser-type
    (throw (IllegalArgumentException.
            (str "Unknown parser type: " parser-type
                 ". Supported types: :text, :apache-tika")))))

;; =============================================================================
;; Java → Clojure Conversion
;; =============================================================================

(defn- metadata->map
  "Converts a LangChain4j Metadata object to a Clojure map."
  [^Metadata metadata]
  (when metadata
    (into {} (.toMap metadata))))

(defn document->map
  "Converts a LangChain4j Document to a Clojure map.
  
  Returns a map with:
  - :text - The document text content
  - :metadata - Document metadata as a Clojure map
  - :java-object - The original Java Document object (for interop)
  
  Example:
  
  (document->map java-document)
  ;; => {:text \"Document content...\"
  ;;     :metadata {:source \"file.pdf\" :page-count 10}
  ;;     :java-object #<Document...>}"
  [^Document document]
  (when document
    {:text (.text document)
     :metadata (metadata->map (.metadata document))
     :java-object document}))

(defn segment->map
  "Converts a LangChain4j TextSegment to a Clojure map.
  
  Returns a map with:
  - :text - The segment text content
  - :metadata - Segment metadata as a Clojure map
  - :java-object - The original Java TextSegment object (for interop)
  
  Example:
  
  (segment->map java-segment)
  ;; => {:text \"Segment content...\"
  ;;     :metadata {:document-id \"doc-123\" :segment-index 0}
  ;;     :java-object #<TextSegment...>}"
  [^TextSegment segment]
  (when segment
    {:text (.text segment)
     :metadata (metadata->map (.metadata segment))
     :java-object segment}))

;; =============================================================================
;; Document Loading
;; =============================================================================

;; ============================================================================
;; Private Java Interop Wrappers (for testability)
;; ============================================================================

(defn- load-document-from-filesystem
  "Private wrapper for FileSystemDocumentLoader/loadDocument. Extracted for testability."
  ^Document [^Path path ^DocumentParser parser]
  (FileSystemDocumentLoader/loadDocument path parser))

(defn- load-documents-from-filesystem
  "Private wrapper for FileSystemDocumentLoader/loadDocuments. Extracted for testability."
  ^java.util.List [^Path path ^DocumentParser parser]
  (FileSystemDocumentLoader/loadDocuments path parser))

(defn- load-document-from-url
  "Private wrapper for UrlDocumentLoader/load. Extracted for testability."
  ^Document [^URL url ^DocumentParser parser]
  (UrlDocumentLoader/load url parser))

(defn- load-documents-recursively-from-filesystem
  "Private wrapper for FileSystemDocumentLoader/loadDocumentsRecursively. Extracted for testability."
  ^java.util.List [^Path path ^DocumentParser parser]
  (FileSystemDocumentLoader/loadDocumentsRecursively path parser))

(defn- load-documents-with-glob-from-filesystem
  "Private wrapper for FileSystemDocumentLoader/loadDocuments with glob. Extracted for testability."
  ^java.util.List [^Path path ^String glob ^DocumentParser parser]
  (FileSystemDocumentLoader/loadDocuments path glob parser))

(defn load-document
  "Loads a single document from a file path.
  
  Parameters:
  - path - String path to the file (e.g., \"/path/to/file.pdf\")
  - opts - Optional map with:
    - :parser - Parser type keyword (:text or :apache-tika) or DocumentParser instance
                Defaults to :text
  
  Returns a map with :text, :metadata, and :java-object keys.
  
  Examples:
  
  ;; Load with default text parser
  (load-document \"/path/to/file.txt\")
  
  ;; Load PDF with Apache Tika
  (load-document \"/path/to/file.pdf\" {:parser :apache-tika})
  
  ;; With custom parser instance
  (load-document \"/path/to/file.pdf\" {:parser (ApacheTikaDocumentParser.)})"
  ([path]
   (load-document path {}))
  ([path {:keys [parser] :or {parser :text}}]
   (let [parser-instance (create-parser parser)
         java-path (Paths/get path (into-array String []))
         ^Document document (load-document-from-filesystem java-path parser-instance)]
     (document->map document))))

(defn load-documents
  "Loads multiple documents from a directory.
  
  Parameters:
  - path - String path to the directory
  - opts - Optional map with:
    - :parser - Parser type keyword (:text or :apache-tika) or DocumentParser instance
                Defaults to :text
    - :recursive? - Boolean, whether to load files from subdirectories
                    Defaults to false
    - :glob - Optional glob pattern to filter files (e.g., \"*.pdf\")
  
  Returns a vector of document maps, each with :text, :metadata, and :java-object keys.
  
  Examples:
  
  ;; Load all files from directory (non-recursive)
  (load-documents \"/path/to/docs\")
  
  ;; Load all PDFs recursively with Apache Tika
  (load-documents \"/path/to/docs\" {:parser :apache-tika :recursive? true})
  
  ;; Load only text files
  (load-documents \"/path/to/docs\" {:glob \"*.txt\"})
  
  ;; Threading-first style
  (-> {:path \"/path/to/docs\"
       :parser :apache-tika
       :recursive? true}
      (get :path)
      (load-documents {:parser :apache-tika :recursive? true}))"
  ([path]
   (load-documents path {}))
  ([path {:keys [parser recursive? glob] :or {parser :text recursive? false}}]
   (let [parser-instance (create-parser parser)
         java-path (Paths/get path (into-array String []))
         documents (cond
                     ;; With glob pattern
                     glob
                     (load-documents-with-glob-from-filesystem java-path glob parser-instance)

                     ;; Recursive
                     recursive?
                     (load-documents-recursively-from-filesystem java-path parser-instance)

                     ;; Non-recursive (default)
                     :else
                     (load-documents-from-filesystem java-path parser-instance))]
     (mapv document->map documents))))

(defn load-from-url
  "Loads a document from a URL.
  
  Parameters:
  - url - String URL to the document (e.g., \"https://example.com/doc.pdf\")
  - opts - Optional map with:
    - :parser - Parser type keyword (:text or :apache-tika) or DocumentParser instance
                Defaults to :text
  
  Returns a map with :text, :metadata, and :java-object keys.
  
  Examples:
  
  ;; Load from URL with default text parser
  (load-from-url \"https://example.com/doc.txt\")
  
  ;; Load PDF from URL with Apache Tika
  (load-from-url \"https://example.com/doc.pdf\" {:parser :apache-tika})"
  ([url]
   (load-from-url url {}))
  ([url {:keys [parser] :or {parser :text}}]
   (let [parser-instance (create-parser parser)
         java-url (URL. url)
         ^Document document (load-document-from-url java-url parser-instance)]
     (document->map document))))

;; =============================================================================
;; Document Splitting
;; =============================================================================

(defn recursive-splitter
  "Creates a recursive document splitter.
  
  The recursive splitter tries to split documents hierarchically:
  Paragraph → Line → Sentence → Word → Character
  
  It fits as many paragraphs as possible into each segment, then falls back
  to smaller units if paragraphs don't fit.
  
  Parameters (map with):
  - :max-segment-size - Maximum size of each segment (required)
  - :max-overlap - Maximum overlap between segments (default: 0)
  - :unit - Size unit, either :tokens or :chars (default: :tokens)
  - :tokenizer - Optional TokenCountEstimator (only for :tokens unit)
  
  Examples:
  
  ;; Create splitter with token-based sizing
  (recursive-splitter {:max-segment-size 500 :max-overlap 50})
  
  ;; Create splitter with character-based sizing
  (recursive-splitter {:max-segment-size 1000 :max-overlap 100 :unit :chars})
  
  ;; Threading-first style
  (-> {:max-segment-size 500 :max-overlap 50}
      recursive-splitter)"
  [{:keys [max-segment-size max-overlap unit tokenizer]
    :or {max-overlap 0 unit :tokens}}]
  (when-not max-segment-size
    (throw (IllegalArgumentException. ":max-segment-size is required")))
  (case unit
    :tokens (if tokenizer
              (DocumentSplitters/recursive max-segment-size max-overlap tokenizer)
              (DocumentSplitters/recursive max-segment-size max-overlap nil))
    :chars (DocumentSplitters/recursive max-segment-size max-overlap)
    (throw (IllegalArgumentException.
            (str "Invalid :unit " unit ". Must be :tokens or :chars")))))

(defn split-document
  "Splits a single document into segments using the provided splitter.
  
  Parameters:
  - document - Document map (from load-document) or Java Document object
  - splitter - DocumentSplitter instance (from recursive-splitter)
  
  Returns a vector of segment maps, each with :text, :metadata, and :java-object keys.
  
  Examples:
  
  ;; Create a splitter and split document
  (def splitter (recursive-splitter {:max-segment-size 500 :max-overlap 50}))
  (def doc (load-document \"/path/to/file.txt\"))
  (split-document doc splitter)
  
  ;; Threading-first style
  (-> (load-document \"/path/to/file.txt\")
      (split-document splitter))"
  [document ^DocumentSplitter splitter]
  (let [^Document java-doc (if (map? document)
                             (:java-object document)
                             document)
        segments (.split splitter java-doc)]
    (mapv segment->map segments)))

(defn split-documents
  "Splits multiple documents into segments.
  
  Parameters:
  - documents - Vector of document maps or Java Document objects
  - opts - Optional map with:
    - :max-segment-size - Maximum size of each segment (default: 500)
    - :max-overlap - Maximum overlap between segments (default: 50)
    - :unit - Size unit, either :tokens or :chars (default: :tokens)
  
  If no splitter options provided, uses defaults (500 tokens, 50 overlap).
  
  Returns a flat vector of all segments from all documents.
  
  Examples:
  
  ;; Split with defaults
  (def docs (load-documents \"/path/to/docs\"))
  (split-documents docs)
  
  ;; Split with custom options
  (split-documents docs {:max-segment-size 1000 :max-overlap 100})
  
  ;; Character-based splitting
  (split-documents docs {:max-segment-size 2000 :max-overlap 200 :unit :chars})
  
  ;; Threading-first style
  (-> (load-documents \"/path/to/docs\" {:parser :apache-tika})
      (split-documents {:max-segment-size 500 :max-overlap 50}))"
  ([documents]
   (split-documents documents {}))
  ([documents opts]
   (let [config (macros/with-defaults opts
                  {:max-segment-size 500
                   :max-overlap 50
                   :unit :tokens})
         splitter (recursive-splitter config)]
     (vec (mapcat #(split-document % splitter) documents)))))
