# Spring AI [![build status](https://github.com/spring-projects/spring-ai/actions/workflows/continuous-integration.yml/badge.svg)](https://github.com/spring-projects/spring-ai/actions/workflows/continuous-integration.yml) [![Maven Central](https://img.shields.io/maven-central/v/org.springframework.ai/spring-ai-model?label=Maven%20Central&versionPrefix=2.0)](https://central.sonatype.com/artifact/org.springframework.ai/spring-ai-model)

The Spring AI project provides a Spring-friendly API and abstractions for developing AI applications.

Its goal is to apply to the AI domain Spring ecosystem design principles such as portability and modular design and promote using POJOs as the building blocks of an application to the AI domain.

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
* [Awesome Spring AI](https://github.com/spring-ai-community/awesome-spring-ai) - A curated list of awesome resources, tools, tutorials, and projects for building generative AI applications using Spring AI
* [Spring AI Examples](https://github.com/spring-projects/spring-ai-examples) contains example projects that explain specific features in more detail.
* [Spring AI Community](https://github.com/spring-ai-community) - A community-driven organization for building Spring-based integrations with AI models, agents, vector databases, and more.

## Features

This is a high level feature overview.

* Support for all major [AI Model providers](https://docs.spring.io/spring-ai/reference/api/index.html) such as Anthropic, OpenAI, Microsoft, Amazon, Google, and Ollama. Supported model types include:
  - [Chat Completion](https://docs.spring.io/spring-ai/reference/api/chatmodel.html)
  - [Embedding](https://docs.spring.io/spring-ai/reference/api/embeddings.html)
  - [Text to Image](https://docs.spring.io/spring-ai/reference/api/imageclient.html)
  - [Audio Transcription](https://docs.spring.io/spring-ai/reference/api/audio/transcriptions.html)
  - [Text to Speech](https://docs.spring.io/spring-ai/reference/api/audio/speech.html)
  - [Moderation](https://docs.spring.io/spring-ai/reference/api/index.html#api/moderation)
* Portable API support across AI providers for both synchronous and streaming options. Access to [model-specific features](https://docs.spring.io/spring-ai/reference/api/chatmodel.html#_chat_options) is also available.
* [Structured Outputs](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html) - Mapping of AI Model output to POJOs.
* Support for all major [Vector Database providers](https://docs.spring.io/spring-ai/reference/api/vectordbs.html) such as *Apache Cassandra, Azure Vector Search, Chroma, Elasticsearch, Milvus, MongoDB Atlas, MariaDB, Neo4j, Oracle, PostgreSQL/PGVector, Pinecone, Qdrant, Redis, and Weaviate*.
* Portable API across Vector Store providers, including a novel SQL-like [metadata filter API](https://docs.spring.io/spring-ai/reference/api/vectordbs.html#metadata-filters).
* [Tools/Function Calling](https://docs.spring.io/spring-ai/reference/api/tools.html) - permits the model to request the execution of client-side tools and functions, thereby accessing necessary real-time information as required.
* [Observability](https://docs.spring.io/spring-ai/reference/observability/index.html) - Provides insights into AI-related operations.
* Document injection [ETL framework](https://docs.spring.io/spring-ai/reference/api/etl-pipeline.html) for Data Engineering.
* [AI Model Evaluation](https://docs.spring.io/spring-ai/reference/api/testing.html) - Utilities to help evaluate generated content and protect against hallucinated response.
* [ChatClient API](https://docs.spring.io/spring-ai/reference/api/chatclient.html) - Fluent API for communicating with AI Chat Models, idiomatically similar to the WebClient and RestClient APIs.
* [Advisors API](https://docs.spring.io/spring-ai/reference/api/advisors.html) - Encapsulates recurring Generative AI patterns, transforms data sent to and from Language Models (LLMs), and provides portability across various models and use cases.
* Support for [Chat Conversation Memory](https://docs.spring.io/spring-ai/reference/api/chatclient.html#_chat_memory) and [Retrieval Augmented Generation (RAG)](https://docs.spring.io/spring-ai/reference/api/chatclient.html#_retrieval_augmented_generation).
* Spring Boot Auto Configuration and Starters for all AI Models and Vector Stores - use the [start.spring.io](https://start.spring.io/) to select the Model or Vector-store of choice. 

## Breaking changes

Refer to the [upgrade notes](https://docs.spring.io/spring-ai/reference/upgrade-notes.html) to see how to upgrade.

## Contributing

Your contributions are always welcome! Please read the [contribution guidelines](CONTRIBUTING.adoc) first.

## Building

The project targets and builds artifacts compatible with Java 17+, and requires a JDK with support for the [`-XDaddTypeAnnotationsToSymbol` javac argument](https://bugs.openjdk.org/browse/JDK-8373586), like Liberica 17.0.19+, for nullability checks.

The recommended JDK is specified in the `.sdkmanrc` file, which can be installed and configured with the [SDKMAN!](https://sdkman.io/) tool:
 - `sdk env install` to install the related JDK locally
 - `sdk env` to use the related JDK

**NOTE:** Make sure to use a JDK with the same architecture than your processor, not an emulated one (for exampel with Rosetta on Mac) as Spring AI requires components that depend on your specific CPU architecture (PyTorch for example). If you are unsure if you have the correct JDK distribution for your CPU, run the command `java -XshowSettings:properties -version 2>&1 | grep os.arch` to validate that it matches your machine.

To build with running unit tests:
```shell
./mvnw clean package
```
### Maven build-cache extension

The Maven build-cache extension is enabled by default to speedup builds.

To build the project while disabling the build cache: 
```shell
./mvnw -Dmaven.build.cache.enabled=false clean package
```

If you suspect the build cache is currupted, you can remove it with:
```shell
rm -rf ~/.m2/build-cache/
```

### Integration Tests
There are many integration tests, so it often isn't realistic to run them all at once.
Note that you should set API key environment variables for model providers before running. If the API key isn't set for a specific model provider, the integration test is skipped.

To run the integration test for a specific module:
```shell
./mvnw -am -pl spring-ai-spring-boot-testcontainers -Pintegration-tests verify
```

To run a specific integration test allowing for up to two attempts to succeed (this is useful when a hosted service is not reliable or times out):
```shell
./mvnw -am -pl vector-stores/spring-ai-pgvector-store -Pintegration-tests -Dfailsafe.failIfNoSpecifiedTests=false -Dfailsafe.rerunFailingTestsCount=2 -Dit.test=PgVectorStoreIT verify
```

A quick pass through the most important pathways that runs integration tests can be done with the profile `-Pci-fast-integration-tests` and is used in the main CI build of this project.
Full integration tests are done regularly in the [Spring AI Integration Tests](https://github.com/spring-projects/spring-ai-integration-tests) repository.

### Documentation

To build the docs:
```shell
./mvnw -pl spring-ai-docs antora
```

The docs are then in the directory `spring-ai-docs/target/antora/site/index.html`

### Formatting the Source Code

Spring AI source code checkstyle tries to follow the checkstyle guidelines used by the core Spring Framework project with some exceptions.

The wiki pages [Code Style](https://github.com/spring-projects/spring-framework/wiki/Code-Style) and [IntelliJ IDEA Editor Settings](https://github.com/spring-projects/spring-framework/wiki/IntelliJ-IDEA-Editor-Settings) define the source file coding standards we use along with some IDEA editor settings we customize.

The code is formatted using the [java-format plugin](https://github.com/spring-io/spring-javaformat) as part of the build. Correct
formatting is enforced by CI.

To format the code specifically:
```shell
./mvnw process-sources
```
Note that will not format the import order.

### Javadocs

To check javadocs using the [javadoc:javadoc](https://maven.apache.org/plugins/maven-javadoc-plugin/):
```shell
./mvnw javadoc:javadoc
```
