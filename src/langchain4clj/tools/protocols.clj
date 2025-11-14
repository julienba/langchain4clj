(ns langchain4clj.tools.protocols
  "Protocol for unified schema support across different validation libraries")

(defprotocol SchemaProvider
  "Protocol to abstract different schema/validation libraries"

  (validate [this data]
    "Validates data against the schema. Throws exception if invalid.")

  (coerce [this data]
    "Coerces data to match schema types. Returns coerced data or throws.")

  (to-json-schema [this]
    "Converts the schema to JSON Schema format for LangChain4j")

  (explain-error [this data]
    "Returns human-readable explanation of validation errors"))
