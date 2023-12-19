# 1. Bedrock Titan Embedding

Use the [TitanEmbeddingBedrockApi.java](src/main/java/org/springframework/ai/bedrock/titan/api/TitanEmbeddingBedrockApi.java) Bedrock Embedding client to implement `EmbeddingClient`.

Consult the the existing Cohere embedding client implementation.
Mind that Titan doesn't support batch embedding. You have to either emulate it (could be very expensive) or throw a not supported exception.

## 1.1 TitanEmbeddingBedrockApi

[TitanEmbeddingBedrockApi](./src/main/java/org/springframework/ai/bedrock/titan/api/TitanEmbeddingBedrockApi.java) provides is lightweight Java client on top of AWS Bedrock [Titan Embedding models](https://docs.aws.amazon.com/bedrock/latest/userguide/titan-embedding-models.html).

> TitanEmbeddingBedrockApi supports Text and Image embedding.


> **NOTE:** TitanEmbeddingBedrockApi does NOT support batch embedding.

Following class diagram illustrates the Llama2ChatBedrockApi interface and building blocks:

![TitanEmbeddingBedrockApi Class Diagram](./src/test/resources/doc/Bedrock%20Titan%20Embedding%20API.jpg)

The CohereEmbeddingBedrockApi supports the `amazon.titan-embed-image-v1` and `amazon.titan-embed-image-v1` models for single and batch embedding computation.

Here is a simple snippet how to use the api programmatically:

```java
TitanEmbeddingBedrockApi titanEmbedApi = new TitanEmbeddingBedrockApi(
		TitanEmbeddingModel.TITAN_EMBED_TEXT_V1.id(), Region.US_EAST_1.id());

TitanEmbeddingRequest request = TitanEmbeddingRequest.builder()
	.withInputText("I like to eat apples.")
	.build();

TitanEmbeddingResponse response = titanEmbedApi.embedding(request);
```

To embed an image you need to convert it into base64 format:

```java
TitanEmbeddingBedrockApi titanEmbedApi = new TitanEmbeddingBedrockApi(
		TitanEmbeddingModel.TITAN_EMBED_IMAGE_V1.id(), Region.US_EAST_1.id());

byte[] image = new DefaultResourceLoader()
	.getResource("classpath:/spring_framework.png")
	.getContentAsByteArray();


TitanEmbeddingRequest request = TitanEmbeddingRequest.builder()
	.withInputImage(Base64.getEncoder().encodeToString(image))
	.build();

TitanEmbeddingResponse response = titanEmbedApi.embedding(request);
```

## 1.2 BedrockTitanEmbeddingClient

[BedrockTitanEmbeddingClient](./src/main/java/org/springframework/ai/bedrock/titan/BedrockTitanEmbeddingClient.java) implements the Spring-Ai `EmbeddingClient` on top of the `TitanEmbeddingBedrockApi`.

You can use like this:

```java
@Bean
public TitanEmbeddingBedrockApi titanEmbeddingApi() {
	return new TitanEmbeddingBedrockApi(
			TitanEmbeddingModel.TITAN_EMBED_IMAGE_V1.id(), Region.US_EAST_1.id());
}

@Bean
public BedrockTitanEmbeddingClient titanEmbedding(TitanEmbeddingBedrockApi titanEmbeddingApi) {
	return new BedrockTitanEmbeddingClient(titanEmbeddingApi);
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

**NOTE:** You have to enable the Bedrock Titan embedding client with `spring.ai.bedrock.titan.embedding.enabled=true`.
By default the client is disabled.

Use the `BedrockTitanEmbeddingProperties` to configure the Bedrock Titan embedding client:

| Property  | Description | Default |
| ------------- | ------------- | ------------- |
| spring.ai.bedrock.aws.region  | AWS region to use.  | us-east-1 |
| spring.ai.bedrock.aws.accessKey  | AWS credentials access key.  |  |
| spring.ai.bedrock.aws.secretKey  | AWS credentials secret key.  |  |
| spring.ai.bedrock.titan.embedding.enable | Enable Bedrock Titan embedding client. Disabled by default | false |
| spring.ai.bedrock.titan.embedding.model  | The model id to use. See the `TitanEmbeddingModel` for the supported models.  | amazon.titan-embed-image-v1 |
| spring.ai.bedrock.titan.embedding.inputType  | Titan Embedding API input types. Could be either text or image (encoded in base64).  | TEXT |
