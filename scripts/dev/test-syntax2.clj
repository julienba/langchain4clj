(defn test-fn [{fn-key :fn}] ;; Use :fn key but bind to different name
  (let [tool-fn fn-key
        executor (fn [params]
                   (tool-fn params))]
    executor))

(println "Syntax test passed!")
(println ((test-fn {:fn inc}) 5))
