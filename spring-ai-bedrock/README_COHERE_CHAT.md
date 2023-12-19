# 1. Bedrock Cohere Chat

Provides Bedrock Cohere Chat clients.

## 1.1 CohereChatBedrockApi

[CohereChatBedrockApi](./src/main/java/org/springframework/ai/bedrock/cohere/api/CohereChatBedrockApi.java) provides is lightweight Java client on top of AWS Bedrock [Cohere Command models](https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-cohere-command.html).

Following class diagram illustrates the Llama2ChatBedrockApi interface and building blocks:

![CohereChatBedrockApi Class Diagram](./src/test/resources/doc/Bedrock%20Cohere%20Chat%20API.jpg)

The CohereChatBedrockApi supports the `cohere.command-light-text-v14` and `cohere.command-text-v14` models for bot synchronous (e.g. `chatCompletion()`) and streaming (e.g. `chatCompletionStream()`) responses.

Here is a simple snippet how to use the api programmatically:

```java
CohereChatBedrockApi cohereChatApi = new CohereChatBedrockApi(
	CohereChatModel.COHERE_COMMAND_V14.id(),
	Region.US_EAST_1.id());

var request = CohereChatRequest
	.builder("What is the capital of Bulgaria and what is the size? What it the national anthem?")
	.withStream(false)
	.withTemperature(0.5f)
	.withTopP(0.8f)
	.withTopK(15)
	.withMaxTokens(100)
	.withStopSequences(List.of("END"))
	.withReturnLikelihoods(CohereChatRequest.ReturnLikelihoods.ALL)
	.withNumGenerations(3)
	.withLogitBias(null)
	.withTruncate(Truncate.NONE)
	.build();

CohereChatResponse response = cohereChatApi.chatCompletion(request);

var request = CohereChatRequest
	.builder("What is the capital of Bulgaria and what is the size? What it the national anthem?")
	.withStream(true)
	.withTemperature(0.5f)
	.withTopP(0.8f)
	.withTopK(15)
	.withMaxTokens(100)
	.withStopSequences(List.of("END"))
	.withReturnLikelihoods(CohereChatRequest.ReturnLikelihoods.ALL)
	.withNumGenerations(3)
	.withLogitBias(null)
	.withTruncate(Truncate.NONE)
	.build();

Flux<CohereChatResponse.Generation> responseStream = cohereChatApi.chatCompletionStream(request);
List<CohereChatResponse.Generation> responses = responseStream.collectList().block();
```

## 1.2 BedrockCohereChatClient

[BedrockCohereChatClient](./src/main/java/org/springframework/ai/bedrock/cohere/BedrockCohereChatClient.java) implements the Spring-Ai `AiClient` and `AiStreamingClient` on top of the `CohereChatBedrockApi`.

You can use like this:

```java
@Bean
public CohereChatBedrockApi cohereApi() {
	return new CohereChatBedrockApi(
			CohereChatModel.COHERE_COMMAND_V14.id(),
			EnvironmentVariableCredentialsProvider.create(),
			Region.US_EAST_1.id(),
			new ObjectMapper());
}

@Bean
public BedrockCohereChatClient cohereChatClient(CohereChatBedrockApi cohereApi) {
	return new BedrockCohereChatClient(cohereApi);
}
```

or you can leverage the `spring-ai-bedrock-ai-spring-boot-starter` Boot starter. For this add the following dependency:

```xml
<dependency>
	<artifactId>spring-ai-bedrock-ai-spring-boot-starter</artifactId>
	<groupId>org.springframework.ai</groupId>
    <version>0.8.0-SNAPSHOT</version>
</dependency>
```

**NOTE:** You have to enable the Bedrock Cohere chat client with `spring.ai.bedrock.cohere.chat.enabled=true`.
By default the client is disabled.

Use the `BedrockCohereChatProperties` to configure the Bedrock Cohere Chat client:

| Property  | Description | Default |
| ------------- | ------------- | ------------- |
| spring.ai.bedrock.embedding.aws.region  | AWS region to use.  | us-east-1 |
| spring.ai.bedrock.embedding.aws.accessKey  | AWS credentials access key.  |  |
| spring.ai.bedrock.embedding.aws.secretKey  | AWS credentials secret key.  |  |
| spring.ai.bedrock.cohere.chat.enable | Enable Bedrock Cohere chat client. Disabled by default | false |
| spring.ai.bedrock.cohere.chat.model  | The model id to use. See the `CohereChatModel` for the supported models.  | cohere.command-text-v14 |
| spring.ai.bedrock.cohere.chat.temperature  | Controls the randomness of the output. Values can range over [0.0,1.0]  | 0.7 |
| spring.ai.bedrock.cohere.chat.topP  | The maximum cumulative probability of tokens to consider when sampling.  | AWS Bedrock default |
| spring.ai.bedrock.cohere.chat.topK  | Specify the number of token choices the model uses to generate the next token  | AWS Bedrock default |
| spring.ai.bedrock.cohere.chat.maxTokens  | Specify the maximum number of tokens to use in the generated response. | AWS Bedrock default |
| spring.ai.bedrock.cohere.chat.stopSequences  | Configure up to four sequences that the model recognizes. | AWS Bedrock default |
| spring.ai.bedrock.cohere.chat.returnLikelihoods  | The token likelihoods are returned with the response. | AWS Bedrock default |
| spring.ai.bedrock.cohere.chat.numGenerations  | The maximum number of generations that the model should return. | AWS Bedrock default |
| spring.ai.bedrock.cohere.chat.logitBiasToken  | Prevents the model from generating unwanted tokens or incentivize the model to include desired tokens. | AWS Bedrock default |
| spring.ai.bedrock.cohere.chat.logitBiasBias  | Prevents the model from generating unwanted tokens or incentivize the model to include desired tokens. | AWS Bedrock default |
| spring.ai.bedrock.cohere.chat.truncate  |  Specifies how the API handles inputs longer than the maximum token length | AWS Bedrock default |

### 2.3 CohereEmbeddingBedrockApi

[CohereEmbeddingBedrockApi](./src/main/java/org/springframework/ai/bedrock/cohere/api/CohereEmbeddingBedrockApi.java) provides is lightweight Java client on top of AWS Bedrock [Cohere Embed models](https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-embed.html).

Following class diagram illustrates the Llama2ChatBedrockApi interface and building blocks:

![CohereEmbeddingBedrockApi Class Diagram](./src/test/resources/doc/Bedrock%20Cohere%20Embedding%20API.jpg)

The CohereEmbeddingBedrockApi supports the `cohere.embed-english-v3` and `cohere.embed-multilingual-v3` models for single and batch embedding computation.

Here is a simple snippet how to use the api programmatically:

```java
CohereEmbeddingBedrockApi api = new CohereEmbeddingBedrockApi(
		CohereEmbeddingModel.COHERE_EMBED_MULTILINGUAL_V1.id(),
		EnvironmentVariableCredentialsProvider.create(),
		Region.US_EAST_1.id(), new ObjectMapper());

CohereEmbeddingRequest request = new CohereEmbeddingRequest(
		List.of("I like to eat apples", "I like to eat oranges"),
		CohereEmbeddingRequest.InputType.search_document,
		CohereEmbeddingRequest.Truncate.NONE);

CohereEmbeddingResponse response = api.embedding(request);

assertThat(response.embeddings()).hasSize(2);
assertThat(response.embeddings().get(0)).hasSize(1024);
```

### 2.4 BedrockCohereEmbeddingClient

[BedrockCohereEmbeddingClient](./src/main/java/org/springframework/ai/bedrock/cohere/BedrockCohereEmbeddingClient.java) implements the Spring-Ai `EmbeddingClient` on top of the `CohereEmbeddingBedrockApi`.

You can use like this:

```java
@Bean
public CohereEmbeddingBedrockApi cohereEmbeddingApi() {
	return new CohereEmbeddingBedrockApi(CohereEmbeddingModel.COHERE_EMBED_MULTILINGUAL_V1.id(),
			EnvironmentVariableCredentialsProvider.create(), Region.US_EAST_1.id(), new ObjectMapper());
}

@Bean
public BedrockCohereEmbeddingClient cohereAiEmbedding(CohereEmbeddingBedrockApi cohereEmbeddingApi) {
	return new BedrockCohereEmbeddingClient(cohereEmbeddingApi);
}
```

or you can leverage the `spring-ai-bedrock-ai-spring-boot-starter` Boot starter. For this add the following dependency:

```xml
<dependency>
	<artifactId>spring-ai-bedrock-ai-spring-boot-starter</artifactId>
	<groupId>org.springframework.ai</groupId>
    <version>0.8.0-SNAPSHOT</version>
</dependency>
```

**NOTE:** You have to enable the Bedrock Cohere chat client with `spring.ai.bedrock.cohere.embedding.enabled=true`.
By default the client is disabled.

Use the `BedrockCohereEmbeddingProperties` to configure the Bedrock Cohere Chat client:

| Property  | Description | Default |
| ------------- | ------------- | ------------- |
| spring.ai.bedrock.cohere.embedding.enable | Enable Bedrock Cohere chat client. Disabled by default | false |
| spring.ai.bedrock.cohere.embedding.awsRegion  | AWS region to use.  | us-east-1 |
| spring.ai.bedrock.cohere.embedding.model  | The model id to use. See the `CohereEmbeddingModel` for the supported models.  | cohere.embed-multilingual-v3 |
| spring.ai.bedrock.cohere.embedding.inputType  | Prepends special tokens to differentiate each type from one another. You should not mix different types together, except when mixing types for for search and retrieval. In this case, embed your corpus with the search_document type and embedded queries with type search_query type.  | search_document |
| spring.ai.bedrock.cohere.embedding.truncate  | Specifies how the API handles inputs longer than the maximum token length. | NONE |
