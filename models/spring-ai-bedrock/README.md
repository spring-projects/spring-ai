# Bedrock AI Chat and Embedding Clients

[Amazon Bedrock](https://docs.aws.amazon.com/bedrock/latest/userguide/what-is-bedrock.html) is a managed service that provides foundation models from various AI providers, available through a unified API.

Spring AI implements `API` clients for the [Bedrock models](https://docs.aws.amazon.com/bedrock/latest/userguide/model-ids-arns.html) along with implementations for the `AiClient`, `AiStreamingClient` and the `EmbeddingClient`.

The API clients provide structured, type-safe implementation for the Bedrock models, while the `AiClient`, `AiStreamingClient` and the `EmbeddingClient` implementations provide Chat and Embedding clients compliant with the Spring-AI API. Later can be used interchangeably with the other (e.g. OpenAI, Azure OpenAI,
Ollama) model clients.

Also Spring-AI provides Spring Auto-Configurations and Boot Starters for all clients, making it easy to bootstrap and configure for the Bedrocks models.

## Prerequisite

* AWS credentials.

  If you dont have AWS account and AWS Cli configured yet then this video guide can help you to configure it: [AWS CLI & SDK Setup in Less Than 4 Minutes!](https://youtu.be/gswVHTrRX8I?si=buaY7aeI0l3-bBVb).
  You should be able to obtain your access and security keys.

* Enable Bedrock models to use

  Go to [Amazon Bedrock](https://us-east-1.console.aws.amazon.com/bedrock/home) and from the [Model Access](https://us-east-1.console.aws.amazon.com/bedrock/home?region=us-east-1#/modelaccess) menu on the left configure the access to the models you are going to use.

## Quick start

Add the `spring-ai-bedrock-ai-spring-boot-starter` dependency to your project POM:

```xml
<dependency>
 <artifactId>spring-ai-bedrock-ai-spring-boot-starter</artifactId>
 <groupId>org.springframework.ai</groupId>
    <version>0.8.0-SNAPSHOT</version>
</dependency>
```

### Connect to AWS Bedrock

Use the `BedrockAwsConnectionProperties` to configure the AWS credentials and region:

```shell
spring.ai.bedrock.aws.region=us-east-1

spring.ai.bedrock.aws.access-key=YOUR_ACCESS_KEY
spring.ai.bedrock.aws.secret-key=YOUR_SECRET_KEY
```

The `region` property is compulsory.

The AWS credentials are resolved in the following this order:

* Spring-AI Bedrock `spring.ai.bedrock.aws.access-key` and `spring.ai.bedrock.aws.secret-key` properties.
* Java System Properties - `aws.accessKeyId` and `aws.secretAccessKey`
* Environment Variables - `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`
* Web Identity Token credentials from system properties or environment variables
* Credential profiles file at the default location (`~/.aws/credentials`) shared by all AWS SDKs and the AWS CLI
* Credentials delivered through the Amazon EC2 container service if `AWS_CONTAINER_CREDENTIALS_RELATIVE_URI`" environment variable is set and security manager has permission to access the variable,
* Instance profile credentials delivered through the Amazon EC2 metadata service or set the `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` environment variables.

### Enable selected Bedrock model

> **NOTE**: By default all models are disabled. You have to enable the chosen Bedrock models explicitly, using the `spring.ai.bedrock.<model>.<chat|embedding>.enabled=true` property.

Here are the supported `<model>` and `<chat|embedding>` combinations:

| Model | Chat | Chat Streaming | Embedding |
| ------------- | ------------- | ------------- | ------------- |
| llama2 | Yes | Yes | No |
| cohere | Yes | Yes | Yes |
| anthropic | Yes | Yes | No |
| jurassic2 | Yes | No | No |
| titan | Yes | Yes | Yes (no batch mode!) |

For example to enable the bedrock Llama2 Chat client you need to set the
`spring.ai.bedrock.llama2.chat.enabled=true`.

Next you can use the `spring.ai.bedrock.<model>.<chat|embedding>.*` properties to configure each model as provided in its documentation:

* [Spring AI Bedrock Llama2 Chat](./README_LLAMA2_CHAT.md) - `spring.ai.bedrock.llama2.chat.enabled=true`
* [Spring AI Bedrock Cohere Chat](./README_COHERE_CHAT.md) - `spring.ai.bedrock.cohere.chat.enabled=true`
* [Spring AI Bedrock Cohere Embedding](./README_COHERE_EMBEDDING.md) - `spring.ai.bedrock.cohere.embedding.enabled=true`
* [Spring AI Bedrock Anthropic Chat](./README_ANTHROPIC_CHAT.md) - `spring.ai.bedrock.anthropic.chat.enabled=true`
* [Spring AI Bedrock Titan Chat](./README_TITAN_CHAT.md) - `spring.ai.bedrock.titan.chat.enabled=true`
* [Spring AI Bedrock Titan Embedding](./README_TITAN_EMBEDDING.md) - `spring.ai.bedrock.titan.embedding.enabled=true`
* (WIP) [Spring AI Bedrock Ai21 Jurassic2 Chat](./README_JURASSIC2_CHAT.md) - `spring.ai.bedrock.jurassic2.chat.enabled=true`
