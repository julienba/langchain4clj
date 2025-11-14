(defn test-fn [{:keys [fn]}]
  (let [tool-fn fn
        executor (fn [params]
                   (tool-fn params))]
    executor))

(println "Syntax test passed!")
((test-fn {:fn inc}) 5)
