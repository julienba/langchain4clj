# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]

### Added - Image Generation Support (Jan 2025) 🎨

**DALL-E 3 and DALL-E 2 Image Generation**

Added comprehensive image generation support using OpenAI's DALL-E models, following the established langchain4clj architecture patterns.

**Changes:**
- **Created `langchain4clj.image` namespace** with full DALL-E integration
- **Added `create-image-model` multimethod** - Dispatches on `:provider` keyword (currently supports `:openai`)
- **Added `openai-image-model` convenience function** - Matches pattern from `core.clj` (e.g., `openai-model`, `anthropic-model`)
- **Added `generate` function** - Generates images from text prompts
- **Added `build-openai-image-model` builder** - Uses `defbuilder` macro for consistent API
- **Private `image->map` helper** - Converts Java `Image` objects to Clojure maps

**API Design:**
```clojure
(require '[langchain4clj.image :as image])

;; Using multimethod with provider dispatch
(def model (image/create-image-model
            {:provider :openai
             :api-key "sk-..."
             :model "dall-e-3"
             :quality "hd"
             :size "1024x1024"}))

;; Using convenience function (auto-sets provider)
(def model (image/openai-image-model
            {:api-key "sk-..."
             :quality "hd"}))

;; Generate image
(def result (image/generate model "A sunset over mountains"))
;; => {:url "https://..." 
;;     :revised-prompt "A picturesque view..." 
;;     :base64 nil}
```

**Features:**
- ✅ **DALL-E 3 support** - HD quality, multiple sizes (1024x1024, 1792x1024, 1024x1792)
- ✅ **DALL-E 2 support** - Faster, cheaper alternative (512x512, 256x256, 1024x1024)
- ✅ **Style control** - "vivid" (hyper-real) or "natural" (subtle) styles
- ✅ **Quality options** - "standard" or "hd" quality for DALL-E 3
- ✅ **Revised prompts** - DALL-E 3 returns enhanced/safety-filtered prompts
- ✅ **Base64 support** - Optional base64 encoding via `:response-format`
- ✅ **Consistent patterns** - Follows `core.clj` architecture (multimethod + convenience functions)
- ✅ **Comprehensive docs** - Full docstrings with examples

**Testing:** ✅ 13 tests with 28 assertions covering:
- Model creation with defaults
- Custom configuration (sizes, quality, styles)
- Convenience function usage
- Image generation with various options
- Error handling and preconditions
- Full workflow integration

**Examples:** Created `examples/image_generation_demo.clj` with 9 examples:
1. Basic image generation
2. Convenience function usage
3. HD quality images
4. Different sizes and orientations
5. Style variations (vivid vs natural)
6. DALL-E 2 usage
7. Batch generation
8. Error handling
9. Threaded API usage

**Architecture Alignment:**
- Uses `defbuilder` macro (same as `build-openai-model`)
- Uses multimethod dispatch on `:provider` (same as `build-model`)
- Provides convenience function (same as `openai-model`)
- Sensible defaults with `:or` destructuring
- Map-based configuration throughout
- Type hints for Java interop (`^ImageModel`, `^Image`)

**Java Interop:**
```clojure
(:import [dev.langchain4j.model.image ImageModel]
         [dev.langchain4j.model.openai OpenAiImageModel]
         [dev.langchain4j.data.image Image])
```

**Files added:**
- `src/langchain4clj/image.clj` - 160 lines (implementation)
- `test/langchain4clj/image_test.clj` - 280 lines (comprehensive tests)
- `examples/image_generation_demo.clj` - 320 lines (9 examples)

**Future extensibility:** The multimethod design allows easy addition of other image generation providers (Stability AI, Midjourney, etc.) by simply adding new `defmethod` implementations.

---

### Added - Automatic Parameter Normalization (Jan 2025) 🔧

**Seamless kebab-case to camelCase Conversion for OpenAI Compatibility**

Added automatic parameter normalization to bridge the gap between Clojure's idiomatic kebab-case naming and OpenAI's camelCase parameter convention. Tool functions can now use kebab-case keys while maintaining compatibility with OpenAI's API.

**Changes:**
- **Added `kebab->camel` function** in `tools.clj` - Converts kebab-case keywords/strings to camelCase strings
- **Added `normalize-tool-params` function** in `tools.clj` - Recursively adds camelCase versions of all parameter keys while preserving originals
- **Integrated normalization into `create-tool`** - Applies normalization to all 4 execution paths (with/without params/result validation)
- **Integrated normalization into `with-params-schema`** - Ensures schema validation works with kebab-case while OpenAI receives camelCase
- **Comprehensive test suite** - Added 7 new test cases (70 assertions total) covering normalization edge cases

**How it works:**
```clojure
;; Input parameters (from OpenAI or user code)
{:pokemon-name "pikachu"}

;; After normalization (both versions available)
{:pokemon-name "pikachu"    ; Original (for spec validation)
 "pokemonName" "pikachu"}   ; camelCase (for OpenAI compatibility)
```

**Benefits:**
- ✅ **Idiomatic Clojure** - Developers use kebab-case as per Clojure conventions
- ✅ **OpenAI Compatible** - Automatic camelCase conversion for AI model calls
- ✅ **Spec Validation Works** - Original keys preserved for schema validation
- ✅ **Zero Breaking Changes** - Fully backward compatible with existing code
- ✅ **Minimal Performance Impact** - Single-pass recursive walk (< 1ms overhead)
- ✅ **Handles Deep Nesting** - Works with nested maps, arrays, and mixed structures

**Example:**
```clojure
;; Tool definition using kebab-case (idiomatic Clojure)
(s/def ::pokemon-name string?)
(s/def ::pokemon-params (s/keys :req-un [::pokemon-name]))

(def get-pokemon-tool
  (tools/tool
   "get_pokemon"
   "Fetches Pokemon information by name"
   (fn [{:keys [pokemon-name]}]  ; kebab-case access
     (fetch-pokemon pokemon-name))))

;; Works seamlessly - normalization happens automatically
(tools/execute-tool get-pokemon-tool {:pokemon-name "pikachu"})  ; Clojure style
(tools/execute-tool get-pokemon-tool {"pokemonName" "pikachu"})  ; OpenAI style
```

**Testing:** ✅ All 182 tests passing (496 assertions, 0 failures, 0 errors)

**Files changed:**
- `src/langchain4clj/tools.clj` - Added normalization functions and integration (52 new lines)
- `test/langchain4clj/tools_test.clj` - Added comprehensive normalization tests (137 new lines)

---

### Changed - Documentation Standardization (Nov 2025) 📚

**English-Only Docstrings for Consistency**

Standardized all docstrings to English for better international accessibility and consistency.

**Changes:**
- **Translated Portuguese docstrings to English** in `langchain4clj.clj`:
  - `build-openai-model` - "Cria uma instância..." → "Creates an OpenAI chat model instance"
  - `build-anthropic-model` - "Cria uma instância..." → "Creates an Anthropic chat model instance"
  - `create-model` - Complete docstring translated including parameters and examples
  - `chat` - Complete docstring translated including parameters, return value, and examples
- **Updated error messages** to English:
  - "Provider não suportado" → "Unsupported provider"
- **Updated test descriptions** to English:
  - Test `test-create-model-unsupported-provider` now uses English assertions

**Benefits:**
- ✅ **International accessibility** - English is the standard for open source libraries
- ✅ **Consistency** - All code, docs, and messages now in single language
- ✅ **Professional standard** - Follows Clojure community conventions
- ✅ **Better IDE support** - English text works better with code completion

**Testing:** ✅ All 175 tests passing (451 assertions, 0 failures, 0 errors)

**Files changed:**
- `src/langchain4clj.clj` - 4 docstrings + 1 error message
- `test/langchain4clj_test.clj` - 1 test description

---

### Added - Operational Logging (Nov 2025) 📊

**Professional Observability for Resilience System**

Added structured logging to the resilience namespace for production monitoring and debugging.

**Changes:**
- **Dependency added**: `org.clojure/tools.logging {:mvn/version "1.3.0"}`
- **Logging integration**: All operational events in `resilience.clj` now logged appropriately
- **Log levels used**:
  - `ERROR`: All providers failed, non-recoverable errors
  - `WARN`: Provider failover, retry attempts, circuit breaker opened/reopened
  - `INFO`: Circuit breaker state transitions to closed, successful retries
  - `DEBUG`: Recoverable errors, exhausted retries (internal state tracking)

**What is logged:**
- ✅ Provider failover events (which fallback provider being tried)
- ✅ Retry attempts and success after retries
- ✅ Circuit breaker state transitions (Closed ↔ Open ↔ Half-Open)
- ✅ All provider failures
- ✅ Non-recoverable and unknown errors

**What is NOT logged (security/privacy):**
- ❌ API keys or tokens
- ❌ Full message content (prompts/responses)
- ❌ Personally Identifiable Information (PII)
- ❌ Function/tool parameters (may contain sensitive data)

**Implementation details:**
- Uses `clojure.tools.logging` (Clojure community standard)
- Delegates to SLF4J backend (users configure their preferred logger)
- All log messages in English
- Zero performance overhead when logging disabled
- Replaced previous `println` statements with proper logging

**Example log output:**
```
WARN  - Retry attempt 1 / 2 after retryable error: 429 Too Many Requests
INFO  - Provider call succeeded after 1 retries
WARN  - Failing over to fallback provider 1
WARN  - Circuit breaker opened due to failure threshold
INFO  - Circuit breaker transitioned to Half-Open state for testing
INFO  - Circuit breaker transitioned to Closed state
ERROR - All 3 providers failed or unavailable
```

**Benefits:**
- ✅ **Production visibility** - Monitor resilience behavior in real-time
- ✅ **Debugging support** - Trace provider failures and failover paths
- ✅ **Configurable** - Users control log levels via SLF4J configuration
- ✅ **Best practices** - Uses Clojure community standard (tools.logging)
- ✅ **Privacy-safe** - Never logs sensitive data

**Testing:** ✅ All 175 tests passing (451 assertions, 0 failures, 0 errors)

**Documentation:** See `docs/development/LOGGING_PROPOSAL.md` for full design rationale

---

### Changed - Code Quality Improvements (Nov 2025) 🧹

**Code Professionalization - Phase 1 (Hybrid Approach)**

Comprehensive code cleanup to improve maintainability and establish quality standards for the project.

**What was done:**

**✅ Phase 1 - Linting Infrastructure:**
- Added `.clj-kondo/config.edn` with custom macro configuration
- Configured `defbuilder` macro to be recognized by clj-kondo
- Established linting standards for the codebase

**✅ Phase 2 - Import Cleanup:**
- Removed **27 unused imports** across 9 source files:
  - `core.clj`: 10 unused imports removed
  - `tools.clj`: 4 unused imports removed
  - `assistant.clj`: 4 unused imports removed
  - `structured.clj`: 3 unused imports removed
  - `agents.clj`: 2 unused imports removed
  - `resilience.clj`: 1 unused import removed
  - `langchain4clj.clj`: 1 unused import removed
  - `tools/malli.clj`: 1 unused import removed
  - `tools/spec.clj`: 1 unused import removed

**✅ Phase 7 - Documentation:**
- Created `CONTRIBUTING.md` with comprehensive contribution guidelines:
  - Code quality standards
  - Import and binding guidelines
  - Testing requirements
  - PR process
  - Project architecture principles
- Updated `docs/development/CODE_CLEANUP_PLAN.md` with complete cleanup roadmap

**Testing:**
- ✅ All 175 tests passing (451 assertions)
- ✅ Zero regressions introduced
- ✅ Full backward compatibility maintained

**Impact:**
- Cleaner, more maintainable codebase
- Established quality standards for contributors
- Reduced code smell and technical debt
- Better IDE autocomplete (fewer false matches)

**Next Steps (Phase 3-6 - Planned):**
- Fix unused bindings (17+ warnings)
- Improve code style consistency
- Remove dead code
- Validate all dependencies are necessary

---

### Changed - Dependencies Upgrade (Feb 2025) 🆕

**📦 LangChain4j 1.0.0 → 1.8.0** - Major dependency upgrade
- **Updated dependencies**:
  - `langchain4j-core`: 1.0.0 → 1.8.0
  - `langchain4j`: 1.0.0 → 1.8.0
  - `langchain4j-open-ai`: 1.0.0 → 1.8.0
  - `langchain4j-anthropic`: 1.0.0-beta5 → 1.8.0
  - `langchain4j-ollama`: NEW → 1.8.0 (local models support)
  - Gemini providers remain at 1.0.0-beta5 (streaming support awaits 1.9.0+)

**Benefits of 1.8.0**:
- ✅ 6 months of bug fixes and performance improvements
- ✅ Virtual thread pool as default executor (better async performance)
- ✅ OpenAI SDK upgraded to v4.0.0 (better API compatibility)
- ✅ MCP (Model Context Protocol) improvements
- ✅ Streaming cancellation support foundation

**Testing**:
- ✅ 105 unit tests passed (281 assertions)
- ✅ All core features verified:
  - Tool system with Spec/Schema/Malli (25 assertions)
  - Assistant system with memory (54 assertions)
  - Streaming with OpenAI/Anthropic (21 assertions)
  - Multi-agent orchestration (23 assertions)
  - Structured output (84 assertions)
- ✅ Zero breaking changes detected
- ✅ Full backward compatibility maintained

**Impact**: Zero breaking changes for langchain4clj users

---

### Added - Provider Failover & Resilience (Phase 1) 🆕 (Feb 2025)

**🛡️ Automatic Retry and Fallback Between Providers** - Production-ready high availability

**Phase 1 Complete - Basic Failover:**
- **New namespace**: `langchain4clj.resilience`
- **Core function**: `create-resilient-model` - Wraps ChatModel with automatic failover
- **Intelligent error classification**:
  - Retryable errors (429, 503, timeout) → Retry on same provider
  - Recoverable errors (401, 404, connection) → Try next provider
  - Non-recoverable errors (400, quota) → Throw immediately
- **Configurable retry logic**:
  - `:max-retries` - Max attempts per provider (default: 2)
  - `:retry-delay-ms` - Delay between retries (default: 1000ms)
- **Provider chain**: Define primary + fallback providers
- **Full feature support**: Works with tools, JSON mode, streaming, ChatRequest
- **Zero breaking changes**: Existing code unchanged

**Implementation Details:**
- ~240 lines of production code
- 16 unit tests + 1 integration test (17 tests, 19 assertions)
- Stateless design (no shared state)
- Thread-safe with proper Java interop

**Comprehensive test suite:**
- Basic failover scenarios (6 tests)
- Retry logic with rate limits (4 tests)
- Configuration validation (4 tests)
- ChatRequest support (1 test)
- Real provider integration (1 test, tagged `^:integration`)

**Example usage:**
```clojure
(require '[langchain4clj.resilience :as resilience])

(def model
  (resilience/create-resilient-model
    {:primary (core/create-model {:provider :openai :api-key "..."})
     :fallbacks [(core/create-model {:provider :anthropic :api-key "..."})
                 (core/create-model {:provider :ollama})]
     :max-retries 2
     :retry-delay-ms 1000}))

(core/chat model "Hello!")
;; Tries: OpenAI (with retries) → Anthropic → Ollama
```

**Why use failover?**
- ✅ **High availability** - Never down due to single provider failure
- ✅ **Cost optimization** - Use cheaper fallbacks when primary fails
- ✅ **Zero vendor lock-in** - Switch providers seamlessly
- ✅ **Production-ready** - Handle rate limits and outages gracefully

**Test results:** 142 tests, 363 assertions, 0 failures, 0 errors

---

### Added - Circuit Breaker (Phase 2) 🆕 (Feb 2025)

**🛡️ Production-Ready Failover with Automatic Recovery**

**Phase 2 Complete - Circuit Breaker:**
- **State machine** with 3 states: Closed, Open, Half-Open
- **Intelligent failure tracking**:
  - Closed → Open: After failure-threshold consecutive failures
  - Open → Half-Open: After timeout-ms elapsed
  - Half-Open → Closed: After success-threshold consecutive successes
  - Half-Open → Open: On any failure during testing
- **Per-provider circuit breakers**: Each provider has independent state
- **Automatic recovery**: Self-healing after timeout period
- **Configurable thresholds**:
  - `:failure-threshold` - Failures before opening (default: 5)
  - `:success-threshold` - Successes before closing (default: 2)
  - `:timeout-ms` - Time in open before half-open (default: 60000)
- **Logging**: State transitions logged to stdout
- **100% backward compatible**: Circuit breaker disabled by default

**Implementation Details:**
- ~120 lines of circuit breaker code (on top of Phase 1)
- 10 new unit tests (27 tests total, 47 assertions total for resilience)
- Stateful design using Clojure atoms for thread-safe state management
- Zero performance overhead when disabled

**Comprehensive test suite:**
- Circuit breaker state transitions (7 tests)
- Integration with retry logic (1 test)
- Per-provider isolation (1 test)
- Backward compatibility (1 test)
- ChatRequest support (1 test)

**Example usage with circuit breaker:**
```clojure
(require '[langchain4clj.resilience :as resilience])

(def model
  (resilience/create-resilient-model
    {:primary (core/create-model {:provider :openai :api-key "..."})
     :fallbacks [(core/create-model {:provider :anthropic :api-key "..."})
                 (core/create-model {:provider :ollama})]
     :max-retries 2
     :retry-delay-ms 1000
     ;; Circuit breaker configuration (Phase 2)
     :circuit-breaker? true
     :failure-threshold 5
     :success-threshold 2
     :timeout-ms 60000}))

(core/chat model "Hello!")
;; Automatic failover with circuit breaker protection
;; Logs: [CircuitBreaker] Closed → Open (threshold reached)
;;       [CircuitBreaker] Open → Half-Open (timeout elapsed)
;;       [CircuitBreaker] Half-Open → Closed
```

**Why use circuit breaker?**
- ✅ **Prevent cascading failures** - Stop calling failing providers
- ✅ **Automatic recovery** - Self-healing after timeout
- ✅ **Resource protection** - Don't waste time on known-failing providers
- ✅ **Fast failover** - Skip open providers immediately
- ✅ **Production patterns** - Industry-standard reliability pattern

**Test results:** 151 tests, 391 assertions, 0 failures, 0 errors

**Resilience system complete:** Phase 1 (Retry + Fallback) + Phase 2 (Circuit Breaker)

---

### Added - API Idiomatic Improvements (v0.2.0)

**🎨 New Idiomatic Macros (Phase 1.1)**
- **`defbuilder` macro** - Eliminates Java builder boilerplate, creates declarative wrappers around Java Builder patterns
- **`build-with` function** - Helper for inline builder usage with automatic reflection
- **Threading helpers**:
  - `apply-if` - Conditional transformations in threading pipelines
  - `apply-when-some` - Safe nil handling in threading pipelines
- **Composition helpers**:
  - `deep-merge` - Recursive map merging for nested configs
  - `with-defaults` - Config with fallback values
  - `kebab->camel` - Automatic case conversion
  - `build-field-map` - Automatic field map generation
- **New namespace**: `langchain4clj.macros` with comprehensive test suite (9 tests, 30 assertions)
- **Example**: `examples/macros_demo.clj` demonstrating all new macros

**🚀 New Idiomatic Core API (Phase 1.2)**
- **New model creation functions** (no :provider key needed):
  - `openai-model` - Create OpenAI models with threading support
  - `anthropic-model` - Create Anthropic models with threading support
- **Threading-first helpers** for composing model configs:
  - `with-model` - Set model name in pipeline
  - `with-temperature` - Set temperature in pipeline
  - `with-timeout` - Set timeout in pipeline
  - `with-logging` - Enable request/response logging
- **Internal builders** using `defbuilder`:
  - `build-openai-model` - Declarative OpenAI builder
  - `build-anthropic-model` - Declarative Anthropic builder
  - `build-chat-request-idiomatic` - Declarative ChatRequest builder
- **Example**: `examples/idiomatic_core_demo.clj` showing 6 usage patterns

**Benefits**:
- 20-30% less code for common use cases
- Full support for threading-first (`->`) macro
- Composable with standard Clojure functions (merge, assoc, etc)
- Pure data until the last moment
- More idiomatic Clojure code
- Better REPL experience

**🎯 Native JSON Mode Support (Phase 1.6)** 🆕
- **New helper functions** for forcing JSON responses:
  - `with-json-mode` - Convenience helper to enable JSON mode
  - `with-response-format` - Set custom ResponseFormat
- **Full integration** with `chat` function:
  - `:response-format` option passes ResponseFormat to LangChain4j
  - Works with ResponseFormat/JSON to guarantee valid JSON output
  - Composable with other options (temperature, tools, etc)
- **Threading-first support**:
  - `(-> config with-json-mode (chat model prompt))`
  - Fully composable with other helpers
- **Comprehensive test suite** (5 new tests, 12 assertions):
  - Tests for helper functions
  - Tests for integration with chat
  - Tests for threading patterns
  - Tests for combining with other options
- **Documentation**:
  - New section in README with 3 usage patterns
  - Examples showing JSON parsing workflow
  - Benefits and use cases explained

**Why native JSON mode?**
- ✅ 100% reliable - Provider guarantees valid JSON
- ✅ No parsing errors - No need for retry logic
- ✅ Faster - No post-processing validation
- ✅ Simple - Just parse and use

**⚡ Streaming Responses Support (Phase 2.1)** 🆕 (Feb 2025)
- **New namespace**: `langchain4clj.streaming`
- **Core functions**:
  - `create-streaming-model` - Create streaming models (multimethod for all providers)
  - `stream-chat` - Stream responses with callbacks
- **Callback-based API**:
  - `:on-token` (required) - Called for each token as it arrives
  - `:on-complete` (optional) - Called when streaming completes
  - `:on-error` (optional) - Called on error
- **Provider support**:
  - ✅ OpenAI (`OpenAiStreamingChatModel`)
  - ✅ Anthropic (`AnthropicStreamingChatModel`)
  - ✅ Google AI Gemini (`GoogleAiGeminiStreamingChatModel`)
  - ✅ Vertex AI Gemini (`VertexAiGeminiStreamingChatModel`)
- **Builders** using `defbuilder`:
  - `build-openai-streaming-model`
  - `build-anthropic-streaming-model`
  - `build-google-ai-gemini-streaming-model`
  - `build-vertex-ai-gemini-streaming-model`
- **Comprehensive test suite** (12 unit tests, 2 integration tests):
  - Mock utilities for testing without API calls
  - Tests for callback order, error handling, partial failures
  - Tests for all 4 providers
  - Integration tests with real APIs (tagged with `^:integration`)
- **Examples**: `examples/streaming_demo.clj` with 5 interactive examples
- **Documentation**:
  - README updated with streaming section
  - STREAMING_PROPOSAL.md with design rationale
  - User-side core.async integration patterns

**Why streaming?**
- ✅ Better UX - Users see progress immediately
- ✅ Feels faster - Perceived latency is lower
- ✅ Real-time feedback - Process tokens as they arrive
- ✅ Simple API - Just callbacks, no complexity
- ✅ Extensible - Users can add core.async if needed

**Implementation details**:
- ~300 lines of production code
- Simple callback-based design (no core.async dependency)
- Zero breaking changes
- Follows project philosophy: "pure translation, unopinionated"

**✅ Phase 1 Complete - API Idiomatic Improvements** 🎉

All Phase 1 goals achieved in January 2025:
- ✅ Phase 1.1: Base macros (`defbuilder`, threading helpers, composition helpers)
- ✅ Phase 1.2: Core API (4 providers with idiomatic helpers)
- ✅ Phase 1.3: Tools API (threading, composable middleware)
- ✅ Phase 1.4: Assistant API (composition with with-* helpers)
- ✅ Phase 1.5: Tests and migration guide (comprehensive documentation)

**Impact**: 30-40% code reduction, 100% threading-friendly, fully backward compatible

---

**🌟 Google Gemini Support (Phase 1.7)** 🆕
- **Two Gemini provider implementations**:
  - `:google-ai-gemini` - Direct Google AI API (simpler, for personal use)
  - `:vertex-ai-gemini` - Google Cloud Vertex AI (enterprise, more features)
- **New helper functions**:
  - `google-ai-gemini-model` - Create Google AI Gemini models
  - `vertex-ai-gemini-model` - Create Vertex AI Gemini models
- **Internal builders** using `defbuilder`:
  - `build-google-ai-gemini-model` - Declarative Google AI builder
  - `build-vertex-ai-gemini-model` - Declarative Vertex AI builder
- **Multimethod support**:
  - `build-model` now supports `:google-ai-gemini` dispatch
  - `build-model` now supports `:vertex-ai-gemini` dispatch
- **Comprehensive test suite** (11 new tests):
  - Tests for model creation via multimethod
  - Tests for defaults configuration
  - Tests for custom configuration
  - Tests for threading patterns
  - Cross-provider comparison tests
- **Dependencies added**:
  - `dev.langchain4j/langchain4j-google-ai-gemini {:mvn/version "1.0.0-beta5"}`
  - `dev.langchain4j/langchain4j-vertex-ai-gemini {:mvn/version "1.0.0-beta5"}`
- **Documentation**:
  - README updated with provider examples
  - Usage examples for both Gemini variants

**Gemini configuration examples**:
```clojure
;; Google AI Gemini (Direct API)
(def gemini (create-model {:provider :google-ai-gemini
                           :api-key "AIza..."
                           :model "gemini-1.5-flash"}))

;; Vertex AI Gemini (Google Cloud)
(def vertex-gemini (create-model {:provider :vertex-ai-gemini
                                  :project "my-gcp-project"
                                  :location "us-central1"
                                  :model "gemini-1.5-pro"}))

;; Or using helper functions
(def gemini (google-ai-gemini-model {:api-key "AIza..."}))
(def vertex (vertex-ai-gemini-model {:project "my-project"}))
```

**🦙 Ollama Support (Phase 1.8)** 🆕
- **Local LLM execution without API costs**:
  - `:ollama` - Run models locally (Llama, Mistral, Gemma, CodeLlama, etc)
  - No API keys required - just install Ollama and pull models
  - Default model: `llama3.1`, default URL: `http://localhost:11434`
- **New helper function**:
  - `ollama-model` - Create Ollama models with comprehensive docstring
- **Internal builders** using `defbuilder`:
  - `build-ollama-model` - Declarative Ollama builder
  - `build-ollama-streaming-model` - Declarative Ollama streaming builder
- **Multimethod support**:
  - `build-model` now supports `:ollama` dispatch
  - `create-streaming-model` now supports `:ollama` dispatch
- **Comprehensive test suite** (8 new tests):
  - Tests for model creation via multimethod
  - Tests for defaults configuration (no API key needed)
  - Tests for custom configuration (remote servers, custom models)
  - Tests for threading patterns
  - Tests for streaming support
  - Integration test with `^:integration` metadata
  - Cross-provider comparison tests updated
- **Dependencies added**:
  - `dev.langchain4j/langchain4j-ollama {:mvn/version "1.8.0"}`
- **Documentation**:
  - README updated with Ollama examples
  - Popular models listed (llama3.1, mistral, gemma, codellama, phi)
  - Installation instructions included

**Ollama configuration examples**:
```clojure
;; Simple usage (default: llama3.1 on localhost:11434)
(def ollama (create-model {:provider :ollama}))

;; With specific model
(def mistral (create-model {:provider :ollama
                            :model "mistral"}))

;; Remote Ollama server
(def remote-ollama (create-model {:provider :ollama
                                  :base-url "http://192.168.1.100:11434"
                                  :model "codellama"}))

;; Or using helper function
(def ollama (ollama-model {:model "llama3.1:70b"
                          :temperature 0.9}))

;; Streaming also works
(def ollama-streaming (streaming/create-streaming-model {:provider :ollama}))
```

**Why Ollama?**
- ✅ **Zero API costs** - Run models locally on your machine
- ✅ **Privacy** - Your data never leaves your computer
- ✅ **No rate limits** - Use as much as you want
- ✅ **Fast** - No network latency, just local inference
- ✅ **Easy setup** - Install Ollama, pull a model, start chatting

---

### Added - Previous Features
- **Enhanced `chat` function** with arity overloading for advanced features:
  - New arity `(chat model message options)` supporting ChatRequest parameters
  - Support for tools/function calling via `:tools` option
  - Support for JSON mode via `:response-format` option
  - Support for system messages via `:system-message` option
  - Support for temperature, max-tokens, top-p, top-k, penalties, stop sequences
  - Fully compatible with LangChain4j ChatRequest API
- New internal helper `build-chat-request` for converting Clojure maps to Java ChatRequest
- **Comprehensive test suite** for new chat options functionality:
  - Tests for arity selection logic
  - Tests for options passing to ChatRequest
  - Tests for empty options optimization
  - Integration-style tests for various option types

### Fixed
- **CRITICAL:** `chat-with-output-tool` in structured.clj now works correctly
  - Fixed to extract `aiMessage` from `ChatResponse` 
  - Now properly uses the new chat signature with tools
- **CRITICAL:** `chat-with-tools` in assistant.clj now works correctly
  - Fixed to extract `aiMessage` from `ChatResponse`
  - Fixed tool execution loop to work with new response type
- **CRITICAL:** `execute-tool-calls` in assistant.clj fixed major bug
  - Was calling non-existent `core/parse-json` function
  - Now correctly uses `json/read-str` to parse tool arguments
  - Added missing `clojure.data.json` require

### Changed - API Idiomatic Improvements (v0.2.0)

**✅ 100% Backward Compatible** - All existing code continues to work!

**Core API Refactored**:
- `build-model` multimethod now uses `defbuilder` internally
  - :openai method uses `build-openai-model`
  - :anthropic method uses `build-anthropic-model`
  - External API unchanged - `create-model` still works identically
- `core.clj` now requires `langchain4clj.macros`
- All existing tests pass without modification (7 tests, 25 assertions)

**Migration Path**:
- Old API: `(create-model {:provider :openai :api-key "..."})` - **Still works!**
- New API: `(openai-model {:api-key "..."})` - **Simpler, no :provider needed**
- Both APIs can be used side-by-side

### Changed - Previous Changes
- `chat` function now has two arities:
  - `(chat model message)` - simple, returns String (unchanged, backwards compatible)
  - `(chat model message options)` - advanced, returns ChatResponse with metadata
- Updated imports in core.clj to include ChatRequest, ChatResponse, UserMessage, SystemMessage
- Structured output functions now correctly work with the new chat API
- **Test suite consolidated** from 15 tests to 7 focused tests:
  - Removed 8 redundant tests
  - Removed 5 mock-only tests that didn't add value
  - Improved test organization and documentation
  - Better coverage with fewer, higher-quality tests

## [0.1.1] - 2025-11-04
### Changed
- Documentation on how to make the widgets.

### Removed
- `make-widget-sync` - we're all async, all the time.

### Fixed
- Fixed widget maker to keep working when daylight savings switches over.

## 0.1.0 - 2025-11-04
### Added
- Files from the new template.
- Widget maker public API - `make-widget-sync`.

[Unreleased]: https://github.com/langchain4clj/compare/0.1.1...HEAD
[0.1.1]: https://github.com/langchain4clj/compare/0.1.0...0.1.1
