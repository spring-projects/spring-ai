# Anthropic Java SDK Integration

This module integrates the official Anthropic Java SDK with Spring AI, providing access to Claude models through Anthropic's API.

[Anthropic Java SDK GitHub repository](https://github.com/anthropics/anthropic-sdk-java)

## Authentication

Configure your Anthropic API key either programmatically or via environment variable:

```java
AnthropicSdkChatOptions options = AnthropicSdkChatOptions.builder()
    .apiKey("<your-api-key>")
    .build();
```

Or using the environment variable (automatically detected):

```bash
export ANTHROPIC_API_KEY=<your-api-key>
```

## Features

This module supports:

- **Chat Completions** - Synchronous and streaming responses
- **Tool Calling** - Function calling with automatic tool execution
- **Streaming Tool Calling** - Tool calls in streaming mode with partial JSON accumulation
- **Multi-Modal** - Images and PDF documents
- **Extended Thinking** - Claude's thinking/reasoning feature with full streaming support
- **Citations** - Document-grounded responses with source attribution
- **Prompt Caching** - Reduce costs for repeated context with configurable strategies
- **Structured Output** - JSON schema-constrained responses with effort control
- **Observability** - Micrometer-based metrics and tracing

### Planned Features

- **Amazon Bedrock** - Access Claude through AWS Bedrock
- **Google Vertex AI** - Access Claude through Google Cloud
- **Spring Boot Auto-Configuration** - Starter with configuration properties

## Basic Usage

```java
// Create chat model with default options
AnthropicSdkChatModel chatModel = new AnthropicSdkChatModel(
    AnthropicSdkChatOptions.builder()
        .model("claude-sonnet-4-20250514")
        .maxTokens(1024)
        .build()
);

// Synchronous call
ChatResponse response = chatModel.call(new Prompt("Hello, Claude!"));

// Streaming call
Flux<ChatResponse> stream = chatModel.stream(new Prompt("Tell me a story"));
```

## Tool Calling

```java
var options = AnthropicSdkChatOptions.builder()
    .model("claude-sonnet-4-20250514")
    .toolCallbacks(FunctionToolCallback.builder("getWeather", new WeatherService())
        .description("Get the current weather for a location")
        .inputType(WeatherRequest.class)
        .build())
    .build();

ChatResponse response = chatModel.call(new Prompt("What's the weather in Paris?", options));
```

## Extended Thinking

Enable Claude's reasoning feature to see step-by-step thinking before the final answer:

```java
var options = AnthropicSdkChatOptions.builder()
    .model("claude-sonnet-4-20250514")
    .temperature(1.0) // required when thinking is enabled
    .maxTokens(16000)
    .thinkingEnabled(10000L) // budget must be >= 1024 and < maxTokens
    .build();

ChatResponse response = chatModel.call(new Prompt("Solve this step by step...", options));
```

Three thinking modes are available via convenience builders:
- `thinkingEnabled(budgetTokens)` - Enable with a specific token budget
- `thinkingAdaptive()` - Let Claude decide whether to think
- `thinkingDisabled()` - Explicitly disable thinking

Thinking is fully supported in both synchronous and streaming modes, including signature capture for thinking block verification.

## Citations

Anthropic's Citations API allows Claude to reference specific parts of provided documents when generating responses. Three document types are supported: plain text, PDF, and custom content blocks.

```java
// Create a citation document
AnthropicSdkCitationDocument document = AnthropicSdkCitationDocument.builder()
    .plainText("The Eiffel Tower was completed in 1889 in Paris, France. " +
               "It stands 330 meters tall and was designed by Gustave Eiffel.")
    .title("Eiffel Tower Facts")
    .citationsEnabled(true)
    .build();

// Call the model with the document
ChatResponse response = chatModel.call(
    new Prompt(
        "When was the Eiffel Tower built?",
        AnthropicSdkChatOptions.builder()
            .model("claude-sonnet-4-20250514")
            .maxTokens(1024)
            .citationDocuments(document)
            .build()
    )
);

// Access citations from response metadata
List<Citation> citations = (List<Citation>) response.getMetadata().get("citations");
for (Citation citation : citations) {
    System.out.println("Document: " + citation.getDocumentTitle());
    System.out.println("Cited text: " + citation.getCitedText());
}
```

PDF and custom content block documents are also supported via `pdfFile()`, `pdf()`, and `customContent()` builders.

## Prompt Caching

Prompt caching reduces costs and latency by caching repeated context (system prompts, tool definitions, conversation history) across API calls. Five caching strategies are available:

| Strategy | Description |
|----------|-------------|
| `NONE` | No caching (default) |
| `SYSTEM_ONLY` | Cache system message content |
| `TOOLS_ONLY` | Cache tool definitions |
| `SYSTEM_AND_TOOLS` | Cache both system messages and tool definitions |
| `CONVERSATION_HISTORY` | Cache system messages, tools, and conversation messages |

```java
// Cache system messages to reduce costs for repeated prompts
var options = AnthropicSdkChatOptions.builder()
    .model("claude-sonnet-4-20250514")
    .maxTokens(1024)
    .cacheOptions(AnthropicSdkCacheOptions.builder()
        .strategy(AnthropicSdkCacheStrategy.SYSTEM_AND_TOOLS)
        .build())
    .build();

ChatResponse response = chatModel.call(
    new Prompt(List.of(
        new SystemMessage("You are an expert assistant with deep domain knowledge..."),
        new UserMessage("What is the capital of France?")),
        options));

// Access cache token usage via native SDK usage
com.anthropic.models.messages.Usage sdkUsage =
    (com.anthropic.models.messages.Usage) response.getMetadata().getUsage().getNativeUsage();
long cacheCreation = sdkUsage.cacheCreationInputTokens().orElse(0L);
long cacheRead = sdkUsage.cacheReadInputTokens().orElse(0L);
```

You can also configure TTL (5 minutes or 1 hour), minimum content length thresholds, and multi-block system caching for static vs. dynamic system message segments:

```java
var options = AnthropicSdkCacheOptions.builder()
    .strategy(AnthropicSdkCacheStrategy.SYSTEM_ONLY)
    .messageTypeTtl(MessageType.SYSTEM, AnthropicSdkCacheTtl.ONE_HOUR)
    .messageTypeMinContentLength(MessageType.SYSTEM, 100)
    .multiBlockSystemCaching(true)
    .build();
```

## Structured Output

Structured output constrains Claude to produce responses conforming to a JSON schema. The SDK module also supports Anthropic's effort control for tuning response quality vs speed.

> **Model Requirement:** Structured output and effort control require `claude-sonnet-4-6` or newer. Older models like `claude-sonnet-4-20250514` do not support these features.

### JSON Schema Output

```java
var options = AnthropicSdkChatOptions.builder()
    .model("claude-sonnet-4-6")
    .outputSchema("""
        {
            "type": "object",
            "properties": {
                "name": {"type": "string"},
                "capital": {"type": "string"},
                "population": {"type": "integer"}
            },
            "required": ["name", "capital"],
            "additionalProperties": false
        }
        """)
    .build();

ChatResponse response = chatModel.call(new Prompt("Tell me about France.", options));
// Response text will be valid JSON conforming to the schema
```

### Effort Control

Control how much compute Claude spends on its response. Lower effort means faster, cheaper responses; higher effort means more thorough reasoning.

```java
var options = AnthropicSdkChatOptions.builder()
    .model("claude-sonnet-4-6")
    .effort(OutputConfig.Effort.LOW) // LOW, MEDIUM, HIGH, or MAX
    .build();
```

### Combined Schema + Effort

```java
var options = AnthropicSdkChatOptions.builder()
    .model("claude-sonnet-4-6")
    .outputSchema("{\"type\":\"object\",\"properties\":{\"answer\":{\"type\":\"integer\"}},\"required\":[\"answer\"],\"additionalProperties\":false}")
    .effort(OutputConfig.Effort.HIGH)
    .build();
```

### Direct OutputConfig

For full control, use the SDK's `OutputConfig` directly:

```java
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.core.JsonValue;

var outputConfig = OutputConfig.builder()
    .effort(OutputConfig.Effort.HIGH)
    .format(JsonOutputFormat.builder()
        .schema(JsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty("properties", JsonValue.from(Map.of(
                "name", Map.of("type", "string"))))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build())
        .build())
    .build();

var options = AnthropicSdkChatOptions.builder()
    .model("claude-sonnet-4-6")
    .outputConfig(outputConfig)
    .build();
```

## Logging

Enable SDK logging by setting the environment variable:

```bash
export ANTHROPIC_LOG=debug
```

## Documentation

For comprehensive documentation, see:
- [Spring AI Anthropic SDK Reference Documentation](https://docs.spring.io/spring-ai/reference/api/chat/anthropic-sdk-chat.html)
- [Anthropic API Documentation](https://docs.anthropic.com/)
- [Anthropic Java SDK Documentation](https://github.com/anthropics/anthropic-sdk-java)
