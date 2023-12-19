# 1. Bedrock Cohere Embedding

## 1.1 CohereEmbeddingBedrockApi

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

## 1.2 BedrockCohereEmbeddingClient

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

**NOTE:** You have to enable the Bedrock Cohere embedding client with `spring.ai.bedrock.cohere.embedding.enabled=true`.
By default the client is disabled.

Use the `BedrockCohereEmbeddingProperties` to configure the Bedrock Cohere Chat client:

| Property  | Description | Default |
| ------------- | ------------- | ------------- |
| spring.ai.bedrock.aws.region  | AWS region to use.  | us-east-1 |
| spring.ai.bedrock.aws.accessKey  | AWS credentials access key.  |  |
| spring.ai.bedrock.aws.secretKey  | AWS credentials secret key.  |  |
| spring.ai.bedrock.cohere.embedding.enable | Enable Bedrock Cohere embedding client. Disabled by default | false |
| spring.ai.bedrock.cohere.embedding.model  | The model id to use. See the `CohereEmbeddingModel` for the supported models.  | cohere.embed-multilingual-v3 |
| spring.ai.bedrock.cohere.embedding.inputType  | Prepends special tokens to differentiate each type from one another. You should not mix different types together, except when mixing types for for search and retrieval. In this case, embed your corpus with the search_document type and embedded queries with type search_query type.  | search_document |
| spring.ai.bedrock.cohere.embedding.truncate  | Specifies how the API handles inputs longer than the maximum token length. | NONE |
