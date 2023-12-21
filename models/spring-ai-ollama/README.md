# 1. Ollama Chat and Embedding

## 1.1 OllamaApi

[OllamaApi](./src/main/java/org/springframework/ai/ollama/api/OllamaApi.java) provides is lightweight Java client for [Ollama models](https://ollama.ai/).

The OllamaApi provides the Chat completion as well as Embedding endpoints.

Following class diagram illustrates the OllamaApi interface and building blocks for chat completion:

![OllamaApi Class Diagram](./src/test/resources/doc/Ollama%20Chat%20API.jpg)

The OllamaApi can supports all [Ollama Models](https://ollama.ai/library) providing synchronous chat completion, streaming chat completion and embedding:

```java
ChatResponse chat(ChatRequest chatRequest)

Flux<ChatResponse> streamingChat(ChatRequest chatRequest)

EmbeddingResponse embeddings(EmbeddingRequest embeddingRequest)
```

> NOTE: OllamaApi expose also the Ollama `generation` endpoint but later if inferior compared to the Ollama `chat` endpoint.

The `OllamaOptions` is helper class used as type-safe option builder.
It provides `toMap` to convert the content into `Map<String, Object>`.

Here is a simple snippet how to use the OllamaApi programmatically:

```java
var request = ChatRequest.builder("orca-mini")
	.withStream(false)
	.withMessages(List.of(Message.builder(Role.user)
		.withContent("What is the capital of Bulgaria and what is the size? " + "What it the national anthem?")
		.build()))
	.withOptions(Options.create().withTemperature(0.9f).withTopK(10))
	.build();

ChatResponse response = ollamaApi.chat(request);
```

```java
var request = ChatRequest.builder("orca-mini")
	.withStream(true)
	.withMessages(List.of(Message.builder(Role.user)
		.withContent("What is the capital of Bulgaria and what is the size? " + "What it the national anthem?")
		.build()))
	.withOptions(Options.create().withTemperature(0.9f))
	.build();

Flux<ChatResponse> response = ollamaApi.streamingChat(request);

List<ChatResponse> responses = response.collectList().block();
```

```java
EmbeddingRequest request = new EmbeddingRequest("orca-mini", "I like to eat apples");

EmbeddingResponse response = ollamaApi.embeddings(request);
```

## 1.2 OllamaChatClient and OllamaEmbeddingClient

The [OllamaChatClient](./src/main/java/org/springframework/ai/ollama/OllamaChatClient.java) implements the Spring-Ai `ChatClient` and `StreamingChatClient` interfaces.

The [OllamaEmbeddingClient](./src/main/java/org/springframework/ai/ollama/OllamaEmbeddingClient.java) implements the Spring AI `EmbeddingClient` interface.

Both the OllamaChatClient and the OllamaEmbeddingClient leverage the `OllamaApi`.

You can configure the clients like this:

```java
@Bean
public OllamaApi ollamaApi() {
	return new OllamaApi(baseUrl);
}

@Bean
public OllamaChatClient ollamaChat(OllamaApi ollamaApi) {
	return new OllamaChatClient(ollamaApi)
		.withModel("llama2")
		.withOptions(OllamaOptions.create()
			.withTemperature(0.9f)
			.withTopK(12));
}

@Bean
public OllamaEmbeddingClient ollamaEmbedding(OllamaApi ollamaApi) {
	return new OllamaEmbeddingClient(ollamaApi)
		.withModel("orca-mini");
}
```

or you can leverage the `spring-ai-ollama-spring-boot-starter` Spring Boot starter.
For this add the following dependency:

```xml
<dependency>
	<artifactId>spring-ai-ollama-spring-boot-starter</artifactId>
	<groupId>org.springframework.ai</groupId>
    <version>0.8.0-SNAPSHOT</version>
</dependency>
```

Use the `OllamaConnectionProperties` to configure the Ollama clients (both Chat and Embedding) connections:

| Property  | Description | Default |
| ------------- | ------------- | ------------- |
| spring.ai.ollama.base-url  | The base url of the Ollama server. | http://localhost:11434 |

Use the `OllamaChatProperties` to configure the Ollama Chat client:

| Property  | Description | Default |
| ------------- | ------------- | ------------- |
| spring.ai.ollama.chat.model  | Model to use.  | llama2 |
| spring.ai.ollama.chat.enabled  | Allows you to disable the Ollama Chat autoconfiguration.  | true  |
| spring.ai.ollama.chat.temperature  | Controls the randomness of the output. Values can range over [0.0,1.0]  | 0.8 |
| spring.ai.ollama.chat.topP  | The maximum cumulative probability of tokens to consider when sampling.  | - |
| spring.ai.ollama.chat.topK  | Max number or responses to generate. | - |
| spring.ai.options.chat.options | A `OllamaOptions` used to configure the Chat client. | - |

and `OllamaEmbeddingProperties` to configure the Ollama Embedding client:

| Property  | Description | Default |
| ------------- | ------------- | ------------- |
| spring.ai.ollama.embedding.model  | Model to use.  | llama2 |
| spring.ai.ollama.embedding.enabled  | Allows you to disable the Ollama embedding autoconfiguration.  | true  |
| spring.ai.options.embedding.options | `OllamaOptions` used to configure the embedding client. | - |
