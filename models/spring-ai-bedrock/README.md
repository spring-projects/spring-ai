# Bedrock AI Chat and Embedding Clients

[Amazon Bedrock](https://docs.aws.amazon.com/bedrock/latest/userguide/what-is-bedrock.html) is a managed service that provides foundation models from various AI providers, available through a unified API.

Spring AI implements `API` clients for the [Bedrock models](https://docs.aws.amazon.com/bedrock/latest/userguide/model-ids-arns.html) along with implementations for the `ChatClient`, `StreamingChatClient` and the `EmbeddingClient`.

The API clients provide structured, type-safe implementation for the Bedrock models, while the `ChatClient`, `StreamingChatClient` and the `EmbeddingClient` implementations provide Chat and Embedding clients compliant with the Spring-AI API. Later can be used interchangeably with the other (e.g. OpenAI, Azure OpenAI,
Ollama) model clients.

Also Spring-AI provides Spring Auto-Configurations and Boot Starters for all clients, making it easy to bootstrap and configure for the Bedrocks models.

## Prerequisite

* AWS credentials.

  If you dont have AWS account and AWS Cli configured yet then this video guide can help you to configure it: [AWS CLI & SDK Setup in Less Than 4 Minutes!](https://youtu.be/gswVHTrRX8I?si=buaY7aeI0l3-bBVb).
  You should be able to obtain your access and security keys.

* Enable Bedrock models to use

  Go to [Amazon Bedrock](https://us-east-1.console.aws.amazon.com/bedrock/home) and from the [Model Access](https://us-east-1.console.aws.amazon.com/bedrock/home?region=us-east-1#/modelaccess) menu on the left configure the access to the models you are going to use.

## User guides

[Amazon Bedrock Overview](https://docs.spring.io/spring-ai/reference/api/bedrock.html)

- [Anthropic Chat Documentation](https://docs.spring.io/spring-ai/reference/api/clients/bedrock/bedrock-anthropic.html).
- [Cohere Chat Documentation](https://docs.spring.io/spring-ai/reference/api/clients/bedrock/bedrock-cohere.html).
- [Cohere Embedding Documentation](https://docs.spring.io/spring-ai/reference/api/embeddings/bedrock-cohere-embedding.html).
- [Llama2 Chat Documentation](https://docs.spring.io/spring-ai/reference/api/clients/bedrock/bedrock-llama2.html).
- [Titan Chat Documentation](https://docs.spring.io/spring-ai/reference/api/clients/bedrock/bedrock-titan.html).
- [Titan Embedding Documentation](https://docs.spring.io/spring-ai/reference/api/embeddings/bedrock-titan-embedding.html).
