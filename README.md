# Spring AI [![build status](https://github.com/spring-projects/spring-ai/actions/workflows/continuous-integration.yml/badge.svg)](https://github.com/spring-projects/spring-ai/actions/workflows/continuous-integration.yml) [![build status](https://github.com/spring-projects/spring-ai-integration-tests/actions/workflows/spring-ai-integration-tests.yml/badge.svg)](https://github.com/spring-projects/spring-ai-integration-tests/actions/workflows/spring-ai-integration-tests.yml) [![Maven Central](https://img.shields.io/maven-central/v/org.springframework.ai/spring-ai-model?label=Maven%20Central&versionPrefix=1.1)](https://central.sonatype.com/artifact/org.springframework.ai/spring-ai-model)

### Spring Boot Version Compatibility

> **Spring AI 2.x.x** ([main](https://github.com/spring-projects/spring-ai/tree/main) branch) - Spring Boot `4.x`
>
> **Spring AI 1.1.x** ([1.1.x](https://github.com/spring-projects/spring-ai/tree/1.1.x) branch) - Spring Boot `3.5.x`


The Spring AI project provides a Spring-friendly API and abstractions for developing AI applications.

Its goal is to apply to the AI domain Spring ecosystem design principles such as portability and modular design and promote using POJOs as the building blocks of an application to the AI domain.

![spring-ai-integration-diagram-3](https://docs.spring.io/spring-ai/reference/_images/spring-ai-integration-diagram-3.svg)

> At its core, Spring AI addresses the fundamental challenge of AI integration: Connecting your enterprise __Data__ and __APIs__ with the __AI Models__.

The project draws inspiration from notable Python projects, such as [LangChain](https://docs.langchain.com/docs/) and [LlamaIndex](https://gpt-index.readthedocs.io/en/latest/getting_started/concepts.html), but Spring AI is not a direct port of those projects. The project was founded with the belief that the next wave of Generative AI applications will not be only for Python developers but will be ubiquitous across many programming languages.

You can check out the blog post [Why Spring AI](https://spring.io/blog/2024/11/19/why-spring-ai) for additional motivations.

This is a high level feature overview.
You can find more details in the [Reference Documentation](https://docs.spring.io/spring-ai/reference/)

* Support for all major [AI Model providers](https://docs.spring.io/spring-ai/reference/api/index.html) such as Anthropic, OpenAI, Microsoft, Amazon, Google, and Ollama. Supported model types include:
  - [Chat Completion](https://docs.spring.io/spring-ai/reference/api/chatmodel.html)
  - [Embedding](https://docs.spring.io/spring-ai/reference/api/embeddings.html)
  - [Text to Image](https://docs.spring.io/spring-ai/reference/api/imageclient.html)
  - [Audio Transcription](https://docs.spring.io/spring-ai/reference/api/audio/transcriptions.html)
  - [Text to Speech](https://docs.spring.io/spring-ai/reference/api/audio/speech.html)
  - [Moderation](https://docs.spring.io/spring-ai/reference/api/index.html#api/moderation)
  - **Latest Models**: GPT-5, and other cutting-edge models for advanced AI applications.
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

## Getting Started

Please refer to the [Getting Started Guide](https://docs.spring.io/spring-ai/reference/getting-started.html) for instruction on adding your dependencies.

## Project Resources

* [Documentation](https://docs.spring.io/spring-ai/reference/)
* [Issues](https://github.com/spring-projects/spring-ai/issues)
<!-- * [Discussions](https://github.com/spring-projects/spring-ai/discussions) - Go here if you have a question, suggestion, or feedback! -->
* [Awesome Spring AI](https://github.com/spring-ai-community/awesome-spring-ai) - A curated list of awesome resources, tools, tutorials, and projects for building generative AI applications using Spring AI
* [Spring AI Examples](https://github.com/spring-projects/spring-ai-examples) contains example projects that explain specific features in more detail.
* [Spring AI Community](https://github.com/spring-ai-community) - A community-driven organization for building Spring-based integrations with AI models, agents, vector databases, and more.

## Breaking changes

* Refer to the [upgrade notes](https://docs.spring.io/spring-ai/reference/upgrade-notes.html) to see how to upgrade to 1.0.0.M1 or higher.

## Cloning the repo

This repository contains [large model files](https://github.com/spring-projects/spring-ai/tree/main/models/spring-ai-transformers/src/main/resources/onnx/all-MiniLM-L6-v2).
To clone it you have to either:

- Ignore the large files (won't affect the spring-ai behaviour) :  `GIT_LFS_SKIP_SMUDGE=1 git clone git@github.com:spring-projects/spring-ai.git`.
- Or install the [Git Large File Storage](https://git-lfs.com/) before cloning the repo.


## Building

The project targets and build artifacts compatible with Java 17+.
We encourage using Java 21 to build (especially if you switch back and forth between 
the main branch and the 1.1.x branch), but the project can be built with Java 17.

To build with running unit tests

```shell
./mvnw clean package
```

To build including integration tests.

```shell
./mvnw clean verify -Pintegration-tests
```

Note that you should set API key environment variables for OpenAI or other model providers before running.  If the API key isn't set for a specific model provider, the integration test is skipped.

To run a specific integration test allowing for up to two attempts to succeed.  This is useful when a hosted service is not reliable or times out.
```shell
./mvnw -pl vector-stores/spring-ai-pgvector-store -Pintegration-tests -Dfailsafe.rerunFailingTestsCount=2 -Dit.test=PgVectorStoreIT verify
```

### Integration Tests
There are many integration tests, so it often isn't realistic to run them all at once.

A quick pass through the most important pathways that runs integration tests for

* OpenAI models 
* OpenAI autoconfiguration
* PGVector
* Chroma

can be done with the profile `-Pci-fast-integration-tests` and is used in the main CI build of this project.

A full integration test is done twice a day in the [Spring AI Integration Test Repository](https://github.com/spring-projects/spring-ai-integration-tests)

One way to run integration tests on part of the code is to first do a quick compile and install of the project

```shell
./mvnw clean install -DskipTests -Dmaven.javadoc.skip=true
```
Then run the integration test for a specific module using the `-pl` option

```shell
./mvnw verify -Pintegration-tests -pl spring-ai-spring-boot-testcontainers
```

### Documentation

To build the docs
```shell
./mvnw -pl spring-ai-docs antora
```

The docs are then in the directory `spring-ai-docs/target/antora/site/index.html`

### Formatting the Source Code

The code is formatted using the [java-format plugin](https://github.com/spring-io/spring-javaformat) as part of the build. Correct
formatting is enforced by CI.

### Updating License Headers

To update the year on license headers using the [license-maven-plugin](https://oss.carbou.me/license-maven-plugin/#goals)
```shell
./mvnw license:update-file-header -Plicense
```
### Javadocs

To check javadocs using the [javadoc:javadoc](https://maven.apache.org/plugins/maven-javadoc-plugin/)
```shell
./mvnw javadoc:javadoc -Pjavadoc
```

#### Source Code Style

Spring AI source code checkstyle tries to follow the checkstyle guidelines used by the core Spring Framework project with some exceptions.
The wiki pages
[Code Style](https://github.com/spring-projects/spring-framework/wiki/Code-Style) and
[IntelliJ IDEA Editor Settings](https://github.com/spring-projects/spring-framework/wiki/IntelliJ-IDEA-Editor-Settings)
define the source file coding standards we use along with some IDEA editor settings we customize.

## Contributing

Your contributions are always welcome! Please read the [contribution guidelines](CONTRIBUTING.adoc) first.
