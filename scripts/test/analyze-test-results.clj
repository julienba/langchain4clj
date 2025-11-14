#!/usr/bin/env bb
;; analyze-test-results.clj - Analyze test output and create report
;; Usage: bb analyze-test-results.clj test-results/test-run-TIMESTAMP.txt

(require '[clojure.string :as str]
         '[clojure.java.io :as io])

(defn parse-test-line [line]
  "Parse a test result line to extract test info"
  (cond
    ;; FAIL in (test-name) (namespace)
    (str/starts-with? line "FAIL in")
    (let [[_ test-name namespace] (re-find #"FAIL in \(([^)]+)\) \(([^)]+)\)" line)]
      {:type :fail :test test-name :namespace namespace})

    ;; ERROR in (test-name) (namespace)
    (str/starts-with? line "ERROR in")
    (let [[_ test-name namespace] (re-find #"ERROR in \(([^)]+)\) \(([^)]+)\)" line)]
      {:type :error :test test-name :namespace namespace})

    ;; Ran X tests containing Y assertions
    (str/includes? line "Ran")
    (let [[_ tests assertions] (re-find #"Ran (\d+) tests containing (\d+) assertions" line)]
      {:type :summary :tests (parse-long tests) :assertions (parse-long assertions)})

    ;; X failures, Y errors
    (re-find #"\d+ failures" line)
    (let [[_ failures] (re-find #"(\d+) failures" line)
          [_ errors] (re-find #"(\d+) errors" line)]
      {:type :stats :failures (parse-long failures) :errors (parse-long (or errors "0"))})

    :else nil))

(defn analyze-file [file-path]
  "Analyze a test output file"
  (let [lines (line-seq (io/reader file-path))
        parsed (keep parse-test-line lines)
        failures (filter #(= :fail (:type %)) parsed)
        errors (filter #(= :error (:type %)) parsed)
        summary (first (filter #(= :summary (:type %)) parsed))
        stats (first (filter #(= :stats (:type %)) parsed))]

    {:summary summary
     :stats stats
     :failures failures
     :errors errors
     :total-issues (+ (count failures) (count errors))}))

(defn print-report [analysis]
  "Print analysis report"
  (println "\n========================================")
  (println "Test Results Analysis")
  (println "========================================\n")

  (when-let [summary (:summary analysis)]
    (println "📊 Summary:")
    (println (format "  Tests: %d" (:tests summary)))
    (println (format "  Assertions: %d" (:assertions summary)))
    (println))

  (when-let [stats (:stats analysis)]
    (println "📈 Statistics:")
    (println (format "  Failures: %d" (:failures stats)))
    (println (format "  Errors: %d" (:errors stats)))
    (println))

  (let [total (:total-issues analysis)]
    (if (zero? total)
      (println "✅ All tests passed!")
      (do
        (println (format "❌ Total Issues: %d\n" total))

        (when (seq (:failures analysis))
          (println "🔴 Failures:")
          (doseq [fail (:failures analysis)]
            (println (format "  - %s in %s" (:test fail) (:namespace fail))))
          (println))

        (when (seq (:errors analysis))
          (println "⚠️  Errors:")
          (doseq [err (:errors analysis)]
            (println (format "  - %s in %s" (:test err) (:namespace err))))
          (println)))))

  (println "========================================\n"))

(defn -main [& args]
  (if (empty? args)
    (do
      (println "Usage: bb analyze-test-results.clj <test-output-file>")
      (println "Example: bb analyze-test-results.clj test-results/test-run-20250109_120000.txt")
      (System/exit 1))
    (let [file-path (first args)]
      (if (.exists (io/file file-path))
        (let [analysis (analyze-file file-path)]
          (print-report analysis)
          (System/exit (if (zero? (:total-issues analysis)) 0 1)))
        (do
          (println (format "File not found: %s" file-path))
          (System/exit 1))))))

;; Run if called as script
(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
