
# Vertex AI API client for the Generative Language model

The [Generative Language](https://developers.generativeai.google/api/rest/generativelanguage) PaLM API allows developers to build generative AI applications using the PaLM model. Large Language Models (LLMs) are a powerful, versatile type of machine learning model that enables computers to comprehend and generate natural language through a series of prompts. The PaLM API is based on Google's next generation LLM, PaLM. It excels at a variety of different tasks like code generation, reasoning, and writing. You can use the PaLM API to build generative AI applications for use cases like content generation, dialogue agents, summarization and classification systems, and more.

Based on the [Models REST API](https://developers.generativeai.google/api/rest/generativelanguage/models).

## Prerequisite

To access the PaLM2 REST API you need to obtain an access API KEY form [makersuite](https://makersuite.google.com/app/apikey).
Note: Currently it is not available outside US, but you can use VPN for testing.

## PaLM API

The VertexAI, ChatClient and EmbeddingClient are built on top the [VertexAiApi.java](./src/main/java/org/springframework/ai/vertex/api/VertexAiApi.java) client library:

![PaLM API](./src/test/resources/Google%20Generative%20AI%20-%20PaLM2%20REST%20API.jpg)

Following snippets show how to use the `VertexAiApi` client directly:

```java

VertexAiApi vertexAiApi = new VertexAiApi(< YOUR PALM_API_KEY>);

// Generate
var prompt = new MessagePrompt(List.of(new Message("0", "Hello, how are you?")));

GenerateMessageRequest request = new GenerateMessageRequest(prompt);

GenerateMessageResponse response = vertexAiApi.generateMessage(request);

// Embed text
Embedding embedding = vertexAiApi.embedText("Hello, how are you?");

// Batch embedding
List<Embedding> embeddings = vertexAiApi.batchEmbedText(List.of("Hello, how are you?", "I am fine, thank you!"));
```
