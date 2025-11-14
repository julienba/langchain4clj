(ns nandoolle.langchain4clj.image-test
  (:require [clojure.test :refer [deftest is testing]]
            [nandoolle.langchain4clj.image :as image])
  (:import [dev.langchain4j.model.image ImageModel]
           [dev.langchain4j.data.image Image]
           [dev.langchain4j.model.output Response]
           [java.net URI]))

;; =============================================================================
;; Test Utilities
;; =============================================================================

(defn- create-test-image
  "Creates an Image object for testing using the builder"
  [url revised-prompt]
  (-> (Image/builder)
      (.url (URI. url))
      (.revisedPrompt revised-prompt)
      (.build)))

(defn- create-test-image-with-base64
  "Creates an Image with base64 data for testing"
  [url base64-data revised-prompt]
  (-> (Image/builder)
      (.url (URI. url))
      (.base64Data base64-data)
      (.revisedPrompt revised-prompt)
      (.build)))

(defn- create-mock-response
  "Creates a Response<Image> object for testing using Response.from"
  [image]
  (Response/from image))

(defn- create-mock-image-model
  "Creates a mock ImageModel for testing"
  [response-fn]
  (reify ImageModel
    (generate [_ prompt]
      (response-fn prompt))))

;; =============================================================================
;; Model Creation Tests
;; =============================================================================

(deftest test-create-image-model-openai
  (testing "OpenAI image model creation via create-image-model"
    (with-redefs [image/build-openai-image-model
                  (fn [config]
                    (when (and (:api-key config) (:model config))
                      (create-mock-image-model
                       (fn [prompt]
                         (create-mock-response
                          (create-test-image
                           "https://example.com/image.png"
                           prompt))))))]

      (let [model (image/create-image-model {:provider :openai
                                             :api-key "test-key"
                                             :model "dall-e-3"})]
        (is (instance? ImageModel model))))))

(deftest test-openai-image-model-defaults
  (testing "OpenAI applies correct defaults"
    (let [config-received (atom nil)]
      (with-redefs [image/build-openai-image-model
                    (fn [config]
                      (reset! config-received config)
                      (create-mock-image-model
                       (fn [_] (create-mock-response
                                (create-test-image
                                 "https://example.com/test.png"
                                 "test")))))]

        (image/create-image-model {:provider :openai
                                   :api-key "test-key"})

        (is (= "dall-e-3" (:model @config-received)))
        (is (= "test-key" (:api-key @config-received)))))))

(deftest test-openai-image-model-custom-config
  (testing "OpenAI accepts custom configuration"
    (let [config-received (atom nil)]
      (with-redefs [image/build-openai-image-model
                    (fn [config]
                      (reset! config-received config)
                      (create-mock-image-model
                       (fn [_] (create-mock-response
                                (create-test-image
                                 "https://example.com/test.png"
                                 "test")))))]

        (image/create-image-model {:provider :openai
                                   :api-key "custom-key"
                                   :model "dall-e-2"
                                   :size "512x512"
                                   :quality "standard"
                                   :style "natural"})

        (is (= "custom-key" (:api-key @config-received)))
        (is (= "dall-e-2" (:model @config-received)))
        (is (= "512x512" (:size @config-received)))
        (is (= "standard" (:quality @config-received)))
        (is (= "natural" (:style @config-received)))))))

(deftest test-openai-convenience-function
  (testing "openai-image-model convenience function"
    (with-redefs [image/build-openai-image-model
                  (fn [config]
                    (when (and (:api-key config) (:model config))
                      (create-mock-image-model
                       (fn [_] (create-mock-response
                                (create-test-image
                                 "https://example.com/test.png"
                                 "test"))))))]

      (let [model (image/openai-image-model {:api-key "test-key"})]
        (is (instance? ImageModel model))))))

(deftest test-openai-convenience-function-defaults
  (testing "openai-image-model applies defaults through create-image-model"
    (let [config-received (atom nil)]
      (with-redefs [image/build-openai-image-model
                    (fn [config]
                      (reset! config-received config)
                      (create-mock-image-model
                       (fn [_] (create-mock-response
                                (create-test-image
                                 "https://example.com/test.png"
                                 "test")))))]

        (image/openai-image-model {:api-key "test-key"})

        ;; Should have dall-e-3 default
        (is (= "dall-e-3" (:model @config-received)))
        (is (= "test-key" (:api-key @config-received)))))))

;; =============================================================================
;; Image Generation Tests
;; =============================================================================

(deftest test-generate-basic
  (testing "Basic image generation"
    (let [mock-model (create-mock-image-model
                      (fn [prompt]
                        (create-mock-response
                         (create-test-image
                          "https://example.com/sunset.png"
                          (str "Enhanced: " prompt)))))
          result (image/generate mock-model "A beautiful sunset")]

      (is (map? result))
      (is (= "https://example.com/sunset.png" (:url result)))
      (is (= "Enhanced: A beautiful sunset" (:revised-prompt result)))
      (is (nil? (:base64 result))))))

(deftest test-generate-with-base64
  (testing "Image generation with base64 data"
    (let [mock-model (create-mock-image-model
                      (fn [_]
                        (create-mock-response
                         (create-test-image-with-base64
                          "https://example.com/image.png"
                          "iVBORw0KGgoAAAANS..."
                          "Test prompt"))))
          result (image/generate mock-model "Test")]

      (is (some? (:base64 result)))
      (is (= "iVBORw0KGgoAAAANS..." (:base64 result))))))

(deftest test-generate-with-nil-image
  (testing "Generate handles nil image gracefully - image->map returns nil"
    ;; Note: Response.from(null) would throw, so we test the image->map behavior directly
    ;; In real usage, the API would return a valid Response with an Image
    (is (nil? (#'image/image->map nil)))))

(deftest test-generate-preconditions
  (testing "Generate validates preconditions"
    (is (thrown? AssertionError
                 (image/generate nil "Test prompt")))

    (let [mock-model (create-mock-image-model
                      (fn [_] (create-mock-response
                               (create-test-image
                                "https://example.com/test.png"
                                "test"))))]
      (is (thrown? AssertionError
                   (image/generate mock-model nil)))
      (is (thrown? AssertionError
                   (image/generate mock-model 123))))))

;; =============================================================================
;; Integration Tests
;; =============================================================================

(deftest test-full-workflow
  (testing "Complete workflow: create model -> generate image"
    (with-redefs [image/build-openai-image-model
                  (fn [_]
                    (create-mock-image-model
                     (fn [prompt]
                       (create-mock-response
                        (create-test-image
                         (str "https://example.com/" (hash prompt) ".png")
                         (str "Generated: " prompt))))))]

      (let [model (image/create-image-model {:provider :openai
                                             :api-key "test-key"})
            result (image/generate model "A cat playing piano")]

        (is (string? (:url result)))
        (is (.contains (:url result) "https://example.com/"))
        (is (= "Generated: A cat playing piano" (:revised-prompt result)))))))

(deftest test-multiple-sizes
  (testing "Different image sizes configuration"
    (let [configs [{:size "1024x1024"}
                   {:size "1792x1024"}
                   {:size "1024x1792"}]
          received-configs (atom [])]

      (with-redefs [image/build-openai-image-model
                    (fn [config]
                      (swap! received-configs conj config)
                      (create-mock-image-model
                       (fn [_] (create-mock-response
                                (create-test-image
                                 "https://example.com/test.png"
                                 "test")))))]

        (doseq [config configs]
          (image/create-image-model (merge {:provider :openai
                                            :api-key "test-key"}
                                           config)))

        (is (= 3 (count @received-configs)))
        (is (= ["1024x1024" "1792x1024" "1024x1792"]
               (map :size @received-configs)))))))

(deftest test-quality-settings
  (testing "Quality settings for DALL-E 3"
    (let [qualities ["standard" "hd"]
          received-configs (atom [])]

      (with-redefs [image/build-openai-image-model
                    (fn [config]
                      (swap! received-configs conj config)
                      (create-mock-image-model
                       (fn [_] (create-mock-response
                                (create-test-image
                                 "https://example.com/test.png"
                                 "test")))))]

        (doseq [quality qualities]
          (image/create-image-model {:provider :openai
                                     :api-key "test-key"
                                     :quality quality}))

        (is (= ["standard" "hd"] (map :quality @received-configs)))))))

(deftest test-style-settings
  (testing "Style settings for DALL-E 3"
    (let [styles ["vivid" "natural"]
          received-configs (atom [])]

      (with-redefs [image/build-openai-image-model
                    (fn [config]
                      (swap! received-configs conj config)
                      (create-mock-image-model
                       (fn [_] (create-mock-response
                                (create-test-image
                                 "https://example.com/test.png"
                                 "test")))))]

        (doseq [style styles]
          (image/create-image-model {:provider :openai
                                     :api-key "test-key"
                                     :style style}))

        (is (= ["vivid" "natural"] (map :style @received-configs)))))))
