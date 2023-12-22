# 1. Azure OpenAI

Provides Azure OpenAI Chat and Embedding clients.
Leverages the native [OpenAIClient](https://learn.microsoft.com/en-us/java/api/overview/azure/ai-openai-readme?view=azure-java-preview#streaming-chat-completions) to interact with the [Amazon AI Studio models and deployment](https://oai.azure.com/).

## 1.1 Prerequisites

1. Azure Subscription: You will need an [Azure subscription](https://azure.microsoft.com/en-us/free/) to use any Azure service.
2. Azure AI, Azure OpenAI Service: Create [Azure OpenAI](https://portal.azure.com/#create/Microsoft.CognitiveServicesOpenAI).
Once the service is created, obtain the endpoint and apiKey from the `Keys and Endpoint` section under `Resource Management`.
3. Use the [Azure Ai Studio](https://oai.azure.com/portal) to deploy the models you are going to use.

## 1.2 AzureOpenAiChatClient

[AzureOpenAiChatClient](./src/main/java/org/springframework/ai/azure/openai/AzureOpenAiChatClient.java) implements the Spring-Ai `ChatClient` and `StreamingChatClient` on top of the `OpenAIClient`.

[AzureOpenAiEmbeddingClient](./src/main/java/org/springframework/ai/azure/openai/AzureOpenAiEmbeddingClient.java) implements the Spring-Ai `EmbeddingClient` on top of the `OpenAIClient`.


You can configure the AzureOpenAiChatClient and AzureOpenAiEmbeddingClientlike this:

```java
@Bean
public OpenAIClient openAIClient() {
	return new OpenAIClientBuilder()
			.credential(new AzureKeyCredential({YOUR_AZURE_OPENAI_API_KEY}))
			.endpoint({YOUR_AZURE_OPENAI_ENDPOINT})
			.buildClient();
}

@Bean
public AzureOpenAiChatClient cohereChatClient(OpenAIClient openAIClient) {
	return new AzureOpenAiChatClient(openAIClient)
				.withModel("gpt-35-turbo")
				.withMaxTokens(200)
				.withTemperature(0.8);
}

@Bean
public AzureOpenAiEmbeddingClient cohereEmbeddingClient(OpenAIClient openAIClient) {
	return new AzureOpenAiEmbeddingClient(openAIClient, "text-embedding-ada-002-v1");
}
```

## 1.3 Azure OpenAi Auto-Configuration and Spring Boot Starter

You can leverage the `spring-ai-azure-openai-spring-boot-starter` Boot starter.
For this add the following dependency:

```xml
<dependency>
	<artifactId>spring-ai-azure-openai-spring-boot-starter</artifactId>
	<groupId>org.springframework.ai</groupId>
    <version>0.8.0-SNAPSHOT</version>
</dependency>
```

Use the `AzureOpenAiConnectionProperties` to configure the Azure OpenAI access:

| Property  | Description | Default |
| ------------- | ------------- | ------------- |
| spring.ai.azure.openai.apiKey  | Azure AI Open AI credentials api key.  | From the Azure AI OpenAI `Keys and Endpoint` section under `Resource Management` |
| spring.ai.azure.openai.endpoint  | Azure AI Open AI endpoint.  | From the Azure AI OpenAI `Keys and Endpoint` section under `Resource Management` |

Use the `AzureOpenAiChatProperties` to configure the Chat client:

| Property  | Description | Default |
| ------------- | ------------- | ------------- |
| spring.ai.azure.openai.chat.model  | The model id to use.  | gpt-35-turbo |
| spring.ai.azure.openai.chat.temperature  | Controls the randomness of the output. Values can range over [0.0,1.0]  | 0.7 |
| spring.ai.azure.openai.chat.topP  | An alternative to sampling with temperature called nucleus sampling.  |  |
| spring.ai.azure.openai.chat.maxTokens  | The maximum number of tokens to generate.  |  |

Use the `AzureOpenAiEmbeddingProperties` to configure the Embedding client:

| Property  | Description | Default |
| ------------- | ------------- | ------------- |
| spring.ai.azure.openai.embedding.model  | The model id to use for embedding  | text-embedding-ada-002 |
