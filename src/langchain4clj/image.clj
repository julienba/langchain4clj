(ns langchain4clj.image
  "Image generation support for LangChain4j models.

  Provides simple API for generating images from text prompts using
  various image models like DALL-E 3, Stable Diffusion, etc.

  Example:
    (require '[langchain4clj.image :as image])

    (def model (image/create-image-model {:provider :openai
                                          :api-key \"sk-...\"
                                          :model \"dall-e-3\"}))

    (def result (image/generate model \"A sunset over mountains\"))
    (:url result)  ;; => \"https://...\""
  (:require [langchain4clj.macros :as macros])
  (:import [dev.langchain4j.model.image ImageModel]
           [dev.langchain4j.model.openai OpenAiImageModel]
           [dev.langchain4j.data.image Image]))

;; =============================================================================
;; Private Helpers
;; =============================================================================

(defn- image->map
  "Converts a LangChain4j Image to a Clojure map."
  [^Image image]
  (when image
    {:url (str (.url image))
     :base64 (.base64Data image)
     :revised-prompt (.revisedPrompt image)}))

;; =============================================================================
;; Builders for Image Models
;; =============================================================================

(macros/defbuilder build-openai-image-model
  (OpenAiImageModel/builder)
  {:api-key :apiKey
   :model :modelName
   :size :size
   :quality :quality
   :style :style
   :user :user
   :response-format :responseFormat
   :log-requests :logRequests
   :log-responses :logResponses})

;; =============================================================================
;; Public API - Model Creation
;; =============================================================================

(defmulti create-image-model
  "Creates an image generation model.

  Config keys:
  - :provider (required) - :openai (more providers coming soon)
  - :api-key (required) - Provider API key
  - :model (optional) - Model name (defaults to \"dall-e-3\")
  - :size (optional) - Image size (\"1024x1024\", \"1792x1024\", \"1024x1792\")
  - :quality (optional) - \"standard\" or \"hd\" (DALL-E 3 only)
  - :style (optional) - \"vivid\" or \"natural\" (DALL-E 3 only)

  Returns ImageModel instance.

  Examples:
    ;; OpenAI DALL-E 3 (default)
    (create-image-model {:provider :openai
                         :api-key \"sk-...\"})

    ;; DALL-E 3 with HD quality
    (create-image-model {:provider :openai
                         :api-key \"sk-...\"
                         :model \"dall-e-3\"
                         :quality \"hd\"
                         :size \"1792x1024\"})

    ;; DALL-E 2
    (create-image-model {:provider :openai
                         :api-key \"sk-...\"
                         :model \"dall-e-2\"
                         :size \"512x512\"})"
  :provider)

(defmethod create-image-model :openai
  [{:keys [api-key model size quality style user response-format log-requests log-responses]
    :or {model "dall-e-3"}}]
  (build-openai-image-model
   (cond-> {:api-key api-key
            :model model}
     size (assoc :size size)
     quality (assoc :quality quality)
     style (assoc :style style)
     user (assoc :user user)
     response-format (assoc :response-format response-format)
     log-requests (assoc :log-requests log-requests)
     log-responses (assoc :log-responses log-responses))))

;; =============================================================================
;; Public API - Image Generation
;; =============================================================================

(defn generate
  "Generates an image from a text prompt.

  Parameters:
  - model: ImageModel instance
  - prompt: String text description

  Returns a map with:
  - :url - URL to the generated image
  - :base64 - Base64 encoded image data (if requested)
  - :revised-prompt - Revised prompt used by model (DALL-E 3)

  Examples:
    (def model (create-image-model {:provider :openai
                                    :api-key \"sk-...\"}))

    (def result (generate model \"A sunset over mountains\"))

    (:url result)
    ;; => \"https://oaidalleapiprodscus.blob.core.windows.net/...\""
  [^ImageModel model prompt]
  {:pre [(some? model)
         (string? prompt)]}
  (let [response (.generate model prompt)
        image (.content response)]
    (image->map image)))

;; =============================================================================
;; Convenience Functions
;; =============================================================================

(defn openai-image-model
  "Convenience function for creating OpenAI image models.
  
  Equivalent to (create-image-model (assoc config :provider :openai))
  
  Config keys:
  - :api-key (required) - OpenAI API key
  - :model (optional) - Model name (defaults to \"dall-e-3\")
  - :size (optional) - Image size (\"1024x1024\", \"1792x1024\", \"1024x1792\")
  - :quality (optional) - \"standard\" or \"hd\" (DALL-E 3 only)
  - :style (optional) - \"vivid\" or \"natural\" (DALL-E 3 only)
  
  Examples:
    ;; Basic usage with defaults
    (def model (openai-image-model {:api-key \"sk-...\"}))
    
    ;; HD quality landscape
    (def hd-model (openai-image-model {:api-key \"sk-...\"
                                       :quality \"hd\"
                                       :size \"1792x1024\"}))
    
    ;; DALL-E 2
    (def dalle2 (openai-image-model {:api-key \"sk-...\"
                                     :model \"dall-e-2\"
                                     :size \"512x512\"}))"
  [config]
  (create-image-model (assoc config :provider :openai)))

(comment
  ;; Example usage
  (require '[langchain4clj.image :as image])

  ;; Create image model
  (def model (image/create-image-model
              {:provider :openai
               :api-key (System/getenv "OPENAI_API_KEY")
               :model "dall-e-3"}))

  ;; Generate image
  (def result (image/generate model "A beautiful sunset over the ocean"))

  (:url result)
  ;; => "https://..."

  (:revised-prompt result)
  ;; => "A picturesque view of a vibrant sunset..."

  ;; HD quality, landscape orientation
  (def hd-model (image/create-image-model
                 {:provider :openai
                  :api-key (System/getenv "OPENAI_API_KEY")
                  :model "dall-e-3"
                  :quality "hd"
                  :size "1792x1024"}))

  (def hd-result (image/generate hd-model "A futuristic cityscape at night"))
  (:url hd-result))
