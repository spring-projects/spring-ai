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
- **Observability** - Micrometer-based metrics and tracing

### Planned Features

- **Amazon Bedrock** - Access Claude through AWS Bedrock
- **Google Vertex AI** - Access Claude through Google Cloud
- **Multi-Modal** - Images and PDF documents
- **Extended Thinking** - Claude's thinking/reasoning feature
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
