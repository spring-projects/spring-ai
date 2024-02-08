# Bedrock Anthropic

Provides Bedrock Anthropic Chat API and Spring-AI chat clients.

## BedrockAnthropicChatClient

The [BedrockAnthropicChatClient](./src/main/java/org/springframework/ai/bedrock/anthropic/BedrockAnthropicChatClient.java) implements the `ChatClient` and `StreamingChatClient` and uses the `AnthropicChatBedrockApi` library to connect to the Bedrock Anthropic service.

Add the `spring-ai-` dependency to your project's Maven `pom.xml` file:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-bedrock</artifactId>
    <version>0.8.0-SNAPSHOT</version>
</dependency>
```

or to your Gradle `build.gradle` build file.

```gradle
dependencies {
    implementation 'org.springframework.ai:spring-ai-bedrock:0.8.0-SNAPSHOT'
}
```

Next, create an `BedrockAnthropicChatClient` instance and use it to text generations requests:

```java
 AnthropicChatBedrockApi anthropicApi =  new AnthropicChatBedrockApi(
    AnthropicChatBedrockApi.AnthropicModel.CLAUDE_V2.id(),
    EnvironmentVariableCredentialsProvider.create(),
    Region.EU_CENTRAL_1.id(),
    new ObjectMapper());

 BedrockAnthropicChatClient chatClient = new BedrockAnthropicChatClient(anthropicApi,
    AnthropicChatOptions.builder()
        .withTemperature(0.6f)
        .withTopK(10)
        .withTopP(0.8f)
        .withMaxTokensToSample(100)
        .withAnthropicVersion(AnthropicChatBedrockApi.DEFAULT_ANTHROPIC_VERSION)
        .build());

ChatResponse response = chatClient.call(
    new Prompt("Generate the names of 5 famous pirates."));

// Or with streaming responses
Flux<ChatResponse> response = chatClient.stream(
    new Prompt("Generate the names of 5 famous pirates."));
```

or you can leverage the `spring-ai-bedrock-ai-spring-boot-starter` Spring Boot starter:

```xml
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-bedrock-ai-spring-boot-starter</artifactId>
  <version>0.8.0-SNAPSHOT</version>
</dependency>
```

And set `spring.ai.bedrock.anthropic.chat.enabled=true`.
By default the client is disabled.

Use the `BedrockAnthropicChatProperties` to configure the Bedrock Anthropic Chat client:

| Property  | Description | Default |
| ------------- | ------------- | ------------- |
| spring.ai.bedrock.aws.region  | AWS region to use.  | us-east-1 |
| spring.ai.bedrock.aws.accessKey  | AWS credentials access key.  |  |
| spring.ai.bedrock.aws.secretKey  | AWS credentials secret key.  |  |
| spring.ai.bedrock.anthropic.chat.enable | Enable Bedrock Anthropic chat client. Disabled by default | false |
| spring.ai.bedrock.anthropic.chat.model  | The model id to use. See the `AnthropicChatModel` for the supported models.  | anthropic.claude-v2 |
| spring.ai.bedrock.anthropic.chat.options.temperature  | Controls the randomness of the output. Values can range over [0.0,1.0]  | 0.8 |
| spring.ai.bedrock.anthropic.chat.options.topP  | The maximum cumulative probability of tokens to consider when sampling.  | AWS Bedrock default |
| spring.ai.bedrock.anthropic.chat.options.topK  | Specify the number of token choices the generative uses to generate the next token.  | AWS Bedrock default |
| spring.ai.bedrock.anthropic.chat.options.stopSequences  | Configure up to four sequences that the generative recognizes. After a stop sequence, the generative stops generating further tokens. The returned text doesn't contain the stop sequence.  | 10 |
| spring.ai.bedrock.anthropic.chat.options.anthropicVersion  | The version of the generative to use. | bedrock-2023-05-31 |
| spring.ai.bedrock.anthropic.chat.options.maxTokensToSample  | Specify the maximum number of tokens to use in the generated response. Note that the models may stop before reaching this maximum. This parameter only specifies the absolute maximum number of tokens to generate. We recommend a limit of 4,000 tokens for optimal performance. | 500 |

## Appendices

## Using low-level AnthropicChatBedrockApi Library

[AnthropicChatBedrockApi](./src/main/java/org/springframework/ai/bedrock/anthropic/api/AnthropicChatBedrockApi.java) provides is lightweight Java client on top of AWS Bedrock [Anthropic Claude models](https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-claude.html).

Following class diagram illustrates the AnthropicChatBedrockApi interface and building blocks:

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
