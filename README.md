# Spring AI [![build status](https://github.com/spring-projects/spring-ai/actions/workflows/continuous-integration.yml/badge.svg)](https://github.com/spring-projects/spring-ai/actions/workflows/continuous-integration.yml) [![Maven Central](https://img.shields.io/maven-central/v/org.springframework.ai/spring-ai-model?label=Maven%20Central&versionPrefix=2.0)](https://central.sonatype.com/artifact/org.springframework.ai/spring-ai-model)

The Spring AI project provides a Spring-friendly API and abstractions for developing AI applications.

Its goal is to apply Spring ecosystem design principles, such as portability and modular design, to the AI domain and promote using strongly-typed data structures and APIs as the building blocks of an application.

![spring-ai-integration-diagram-3](https://docs.spring.io/spring-ai/reference/_images/spring-ai-integration-diagram-3.svg)

At its core, Spring AI addresses the fundamental challenge of AI integration: connecting your enterprise __Data__ and __APIs__ with the __AI Models__.

## Getting Started

The [reference documentation](https://docs.spring.io/spring-ai/reference/) includes a [Getting Started](https://docs.spring.io/spring-ai/reference/getting-started.html) guide.

Spring Boot Version Compatibility:
* **Spring AI 2.x.x** ([main](https://github.com/spring-projects/spring-ai/tree/main) branch) - Spring Boot `4.x`
* **Spring AI 1.1.x** ([1.1.x](https://github.com/spring-projects/spring-ai/tree/1.1.x) branch) - Spring Boot `3.5.x`

## Project Resources

* [Reference documentation](https://docs.spring.io/spring-ai/reference/)
* [Javadoc](https://docs.spring.io/spring-ai/docs/current/api/)
* [Upgrade notes](https://docs.spring.io/spring-ai/reference/upgrade-notes.html)
* [Awesome Spring AI](https://github.com/spring-ai-community/awesome-spring-ai) - A curated list of awesome resources, tools, tutorials, and projects for building generative AI applications using Spring AI
* [Spring AI Examples](https://github.com/spring-projects/spring-ai-examples) - Contains example projects that explain specific features in more detail.
* [Spring AI Community](https://github.com/spring-ai-community) - A community-driven organization for building Spring-based integrations with AI models, agents, vector databases, and more.

## Contributing

We welcome contributions of all kinds!
Please read our [contribution guidelines](CONTRIBUTING.md) before submitting a pull request or an issue.

## Features

This is a high level feature overview.

* Support for all major [AI Model providers](https://docs.spring.io/spring-ai/reference/api/index.html) such as Anthropic, OpenAI, Amazon Bedrock, Google, Ollama, Mistral AI, DeepSeek, and more.
  Supported model types include:
  - [Chat](https://docs.spring.io/spring-ai/reference/api/chatmodel.html)
  - [Embedding](https://docs.spring.io/spring-ai/reference/api/embeddings.html)
  - [Text to Image](https://docs.spring.io/spring-ai/reference/api/imageclient.html)
  - [Audio Transcription](https://docs.spring.io/spring-ai/reference/api/audio/transcriptions.html)
  - [Text to Speech](https://docs.spring.io/spring-ai/reference/api/audio/speech.html)
  - [Moderation](https://docs.spring.io/spring-ai/reference/api/index.html#api/moderation)
* Portable API support across AI providers for both synchronous and streaming options.
  Access to [model-specific features](https://docs.spring.io/spring-ai/reference/api/chatmodel.html#_chat_options) is also available.
* [Structured Outputs](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html) - Mapping of AI Model output to POJOs.
* Support for all major [Vector Store providers](https://docs.spring.io/spring-ai/reference/api/vectordbs.html) such as *Amazon Bedrock Knowledge Base, Amazon S3, Apache Cassandra, Azure Vector Search, Chroma, Couchbase, Elasticsearch, GemFire, MariaDB, Milvus, MongoDB Atlas, Neo4j, OpenSearch, Oracle, PostgreSQL/PGVector, Pinecone, Qdrant, Redis, Typesense, and Weaviate*.
* Portable API across Vector Store providers, including a novel SQL-like [metadata filter API](https://docs.spring.io/spring-ai/reference/api/vectordbs.html#metadata-filters).
* [Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html) - Permits the model to request the execution of client-side tools and functions, thereby accessing necessary real-time information as required.
* [Observability](https://docs.spring.io/spring-ai/reference/observability/index.html) - Provides insights into AI-related operations.
* Document injection [ETL framework](https://docs.spring.io/spring-ai/reference/api/etl-pipeline.html) for Data Engineering.
* [AI Model Evaluation](https://docs.spring.io/spring-ai/reference/api/testing.html) - Utilities to help evaluate generated content and protect against hallucinated response.
* [ChatClient API](https://docs.spring.io/spring-ai/reference/api/chatclient.html) - Fluent API for communicating with AI Chat Models, idiomatically similar to the WebClient and RestClient APIs.
* [Advisors API](https://docs.spring.io/spring-ai/reference/api/advisors.html) - Encapsulates recurring Generative AI patterns, transforms data sent to and from Language Models (LLMs), and provides portability across various models and use cases.
* [MCP (Model Context Protocol)](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html) - First-class support via Boot Starters and MCP Java Annotations for building AI applications that consume MCP servers or expose Spring-based services to the AI ecosystem, with STDIO, SSE, and Streamable-HTTP transport support.
* Support for [Chat Conversation Memory](https://docs.spring.io/spring-ai/reference/api/chatclient.html#_chat_memory) with pluggable persistent backends (JDBC, Cassandra, MongoDB, Neo4j, Redis) and [Retrieval Augmented Generation (RAG)](https://docs.spring.io/spring-ai/reference/api/chatclient.html#_retrieval_augmented_generation).
* Spring Boot Auto Configuration and Starters for all AI Models and Vector Stores - use [start.spring.io](https://start.spring.io/) to select the Model or Vector Store of choice.

## Building from source

You don’t need to build from source to use Spring AI.
If you want to try out the latest and greatest, Spring AI can be built and published to your local Maven repository:
```shell
./mvnw clean install
```
This command builds all modules, runs unit tests, and publishes artifacts to your local Maven repository.

Please read our [contribution guidelines](CONTRIBUTING.md) for more details.
