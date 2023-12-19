# 1. Bedrock Titan Chat

## 1.1 TitanChatBedrockApi

[TitanChatBedrockApi](./src/main/java/org/springframework/ai/bedrock/titan/api/TitanChatBedrockApi.java) provides is lightweight Java client on top of AWS Bedrock [Titan text models](https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-titan-text.html).

Following class diagram illustrates the Llama2ChatBedrockApi interface and building blocks:

![TitanChatBedrockApi Class Diagram](./src/test/resources/doc/Bedrock%20Titan%20Chat%20API.jpg)

The TitanChatBedrockApi supports the `amazon.titan-text-lite-v1` and `amazon.titan-text-express-v1` models for bot synchronous (e.g. `chatCompletion()`) and streaming (e.g. `chatCompletionStream()`) responses.

Here is a simple snippet how to use the api programmatically:

```java
TitanChatBedrockApi titanBedrockApi = new TitanChatBedrockApi(TitanChatCompletionModel.TITAN_TEXT_EXPRESS_V1.id(),
		Region.EU_CENTRAL_1.id());

TitanChatRequest titanChatRequest = TitanChatRequest.builder("Give me the names of 3 famous pirates?")
	.withTemperature(0.5f)
	.withTopP(0.9f)
	.withMaxTokenCount(100)
	.withStopSequences(List.of("|"))
	.build();

TitanChatResponse response = titanBedrockApi.chatCompletion(titanChatRequest);

assertThat(response.results()).hasSize(1);
assertThat(response.results().get(0).outputText()).contains("Blackbeard");

Flux<TitanChatResponseChunk> response = titanBedrockApi.chatCompletionStream(titanChatRequest);

List<TitanChatResponseChunk> results = response.collectList().block();
assertThat(results.stream().map(TitanChatResponseChunk::outputText).collect(Collectors.joining("\n")))
	.contains("Blackbeard");
```

## 1.2 BedrockTitanChatClient

[BedrockTitanChatClient](./src/main/java/org/springframework/ai/bedrock/titan/BedrockTitanChatClient.java) implements the Spring-Ai `AiClient` and `AiStreamingClient` on top of the `TitanChatBedrockApi`.

You can use like this:

```java
@Bean
public TitanChatBedrockApi titanApi() {
	return new TitanChatBedrockApi(TitanChatModel.TITAN_TEXT_EXPRESS_V1.id(),
			EnvironmentVariableCredentialsProvider.create(), Region.US_EAST_1.id(), new ObjectMapper());
}

@Bean
public BedrockTitanChatClient titanChatClient(TitanChatBedrockApi titanApi) {
	return new BedrockTitanChatClient(titanApi);
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

**NOTE:** You have to enable the Bedrock Titan chat client with `spring.ai.bedrock.titan.chat.enabled=true`.
By default the client is disabled.

Use the `BedrockTitanChatProperties` to configure the Bedrock Titan Chat client:

| Property  | Description | Default |
| ------------- | ------------- | ------------- |
| spring.ai.bedrock.aws.region  | AWS region to use.  | us-east-1 |
| spring.ai.bedrock.aws.accessKey  | AWS credentials access key.  |  |
| spring.ai.bedrock.aws.secretKey  | AWS credentials secret key.  |  |
| spring.ai.bedrock.titan.chat.enable | Enable Bedrock Titan chat client. Disabled by default | false |
| spring.ai.bedrock.titan.chat.model  | The model id to use. See the `TitanChatModel` for the supported models.  | amazon.titan-text-express-v1 |
| spring.ai.bedrock.titan.chat.temperature  | Controls the randomness of the output. Values can range over [0.0,1.0]  | 0.7 |
| spring.ai.bedrock.titan.chat.topP  | The maximum cumulative probability of tokens to consider when sampling.  | AWS Bedrock default |
| spring.ai.bedrock.titan.chat.maxTokenCount  | Specify the maximum number of tokens to use in the generated response. | AWS Bedrock default |
| spring.ai.bedrock.titan.chat.stopSequences  | Configure up to four sequences that the model recognizes. | AWS Bedrock default |
