# 1. Bedrock Anthropic

Provides Bedrock Anthropic Chat API and Spring-AI chat clients.

## 1.1 AnthropicChatBedrockApi

[AnthropicChatBedrockApi](./src/main/java/org/springframework/ai/bedrock/anthropic/api/AnthropicChatBedrockApi.java) provides is lightweight Java client on top of AWS Bedrock [Anthropic Claude models](https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-claude.html).

Following class diagram illustrates the Llama2ChatBedrockApi interface and building blocks:

![AnthropicChatBedrockApi Class Diagram](./src/test/resources/doc/Bedrock-Anthropic-Chat-API.jpg)

The AnthropicChatBedrockApi supports the `anthropic.claude-instant-v1` and `anthropic.claude-v2` models.

Also the AnthropicChatBedrockApi supports both synchronous (e.g. `chatCompletion()`) and streaming (e.g. `chatCompletionStream()`) responses.

Here is a simple snippet how to use the api programmatically:

```java
AnthropicChatBedrockApi anthropicChatApi = new AnthropicChatBedrockApi(
   AnthropicModel.CLAUDE_V2.id(),
   Region.EU_CENTRAL_1.id());

AnthropicChatRequest request = AnthropicChatRequest
  .builder(String.format(AnthropicChatBedrockApi.PROMPT_TEMPLATE, "Name 3 famous pirates"))
  .withTemperature(0.8f)
  .withMaxTokensToSample(300)
  .withTopK(10)
  // .withStopSequences(List.of("\n\nHuman:"))
  .build();

AnthropicChatResponse response = anthropicChatApi.chatCompletion(request);

System.out.println(response.completion());

// Streaming response
Flux<AnthropicChatResponse> responseStream = anthropicChatApi.chatCompletionStream(request);

List<AnthropicChatResponse> responses = responseStream.collectList().block();

System.out.println(responses);
```

Follow the [AnthropicChatBedrockApi.java](./src/main/java/org/springframework/ai/bedrock/anthropic/api/AnthropicChatBedrockApi.java)'s JavaDoc for further information.

## 1.2 BedrockAnthropicChatClient

[BedrockAnthropicChatClient](./src/main/java/org/springframework/ai/bedrock/anthropic/BedrockAnthropicChatClient.java) implements the Spring-Ai `ChatClient` and `StreamingChatClient` on top of the `AnthropicChatBedrockApi`.

You can use like this:

```java
@Bean
public AnthropicChatBedrockApi anthropicApi() {
 return new AnthropicChatBedrockApi(
  AnthropicChatBedrockApi.AnthropicModel.CLAUDE_V2.id(),
  EnvironmentVariableCredentialsProvider.create(),
  Region.EU_CENTRAL_1.id(),
  new ObjectMapper());
}

@Bean
public BedrockAnthropicChatClient anthropicChatClient(AnthropicChatBedrockApi anthropicApi) {
 return new BedrockAnthropicChatClient(anthropicApi);
}
```

or you can leverage the `spring-ai-bedrock-ai-spring-boot-starter` Spring Boot starter:

```xml
<dependency>
 <artifactId>spring-ai-bedrock-ai-spring-boot-starter</artifactId>
 <groupId>org.springframework.ai</groupId>
    <version>0.8.0-SNAPSHOT</version>
</dependency>
```

And set `spring.ai.bedrock.anthropic.chat.enabled=true`.
By default the client is disabled.

Use the `BedrockAnthropicChatProperties` to configure the Bedrock Llama2 Chat client:

| Property  | Description | Default |
| ------------- | ------------- | ------------- |
| spring.ai.bedrock.aws.region  | AWS region to use.  | us-east-1 |
| spring.ai.bedrock.aws.accessKey  | AWS credentials access key.  |  |
| spring.ai.bedrock.aws.secretKey  | AWS credentials secret key.  |  |
| spring.ai.bedrock.anthropic.chat.enable | Enable Bedrock Llama2 chat client. Disabled by default | false |
| spring.ai.bedrock.anthropic.chat.temperature  | Controls the randomness of the output. Values can range over [0.0,1.0]  | 0.8 |
| spring.ai.bedrock.anthropic.chat.topP  | The maximum cumulative probability of tokens to consider when sampling.  | AWS Bedrock default |
| spring.ai.bedrock.anthropic.chat.maxGenLen  | Specify the maximum number of tokens to use in the generated response. | 300 |
| spring.ai.bedrock.anthropic.chat.model  | The model id to use. See the `Llama2ChatCompletionModel` for the supported models.  | meta.llama2-70b-chat-v1 |
