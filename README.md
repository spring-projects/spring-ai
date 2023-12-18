# Spring AI [![build status](https://github.com/spring-projects/spring-ai/actions/workflows/continuous-integration.yml/badge.svg)](https://github.com/spring-projects/spring-ai/actions/workflows/continuous-integration.yml)

Welcome to the Spring AI project!

The Spring AI project provides a Spring-friendly API and abstractions for developing AI applications.

Let's make your `@Beans` intelligent!

## Project Update

:partying_face: The Spring AI project has graduated out of the repository!

:warning:

We are transitioning the project's Group ID:

* **FROM**: `org.springframework.experimental.ai`
* **TO**: `org.springframework.ai`

Artifacts will still be hosted in the snapshot repository as shown below.

The main branch will move to the version `0.8.0-SNAPSHOT`.
It will be unstable for a week or two.
Please use the 0.7.1-SNAPSHOT if you don't want to be on the bleeding edge.

You can access `0.7.1-SNAPSHOT` artifacts as before and still access [0.7.1-SNAPSHOT Documentation](https://markpollack.github.io/spring-ai-0.7.1/).


## Cloning the repo

This repository contains [large model files](https://github.com/spring-projects/spring-ai/tree/main/embedding-clients/transformers-embedding/src/main/resources/onnx/all-MiniLM-L6-v2).
To clone it you have to either:

- Ignore the large files (won't affect the spring-ai behaviour) :  `GIT_LFS_SKIP_SMUDGE=1 git clone git@github.com:spring-projects/spring-ai.git`.
- Or install the [Git Large File Storage](https://git-lfs.com/) before cloning the repo.

## Project Links

* [Documentation](https://docs.spring.io/spring-ai/reference/)
* [Issues](https://github.com/spring-projects/spring-ai/issues)
* [Discussions](https://github.com/spring-projects/spring-ai/discussions) - Go here if you have a question, suggestion, or feedback!
* [JavaDocs](https://docs.spring.io/spring-ai/docs/current-SNAPSHOT/)

## Educational Resources

* Follow the [Workshop](#workshop)
* Overview of Spring AI @ Devoxx 2023
<br>[![Watch the Devoxx 2023 video](https://img.youtube.com/vi/7OY9fKVxAFQ/default.jpg)](https://www.youtube.com/watch?v=7OY9fKVxAFQ)
* Introducing Spring AI - Add Generative AI to your Spring Applications
<br>[![Watch the video](https://img.youtube.com/vi/1g_wuincUdU/default.jpg)](https://www.youtube.com/watch?v=1g_wuincUdU)

## Dependencies

The Spring AI project provides artifacts in the Spring Milestone Repository.
You will need to add configuration to add a reference to the Spring Milestone repository in your build file.
For example, in maven, add the following repository definition.


```xml
  <repositories>
    <repository>
      <id>spring-snapshots</id>
      <name>Spring Snapshots</name>
      <url>https://repo.spring.io/snapshot</url>
      <releases>
        <enabled>false</enabled>
      </releases>
    </repository>
  </repositories>
```

And the Spring Boot Starter depending on if you are using Azure Open AI or Open AI.

### Main Branch

The main branch has move to version 0.8.0-SNAPSHOT.
It will be unstable for a week or two.
Please use the 0.7.1-SNAPSHOT if you don't want to be on the bleeding edge.

* Azure OpenAI
```xml
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-azure-openai-spring-boot-starter</artifactId>
        <version>0.8.0-SNAPSHOT</version>
    </dependency>
```

* OpenAI

```xml
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
        <version>0.8.0-SNAPSHOT</version>
    </dependency>
```

### 0.7.1-SNAPSHOT

* Azure OpenAI
```xml
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-azure-openai-spring-boot-starter</artifactId>
        <version>0.7.1-SNAPSHOT</version>
    </dependency>
```

* OpenAI

```xml
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
        <version>0.7.1-SNAPSHOT</version>
    </dependency>
```
### Vector Stores

Following vector stores are supported:

* Azure Vector Store

```xml
    <dependency>
        <groupId>org.springframework.ai</groupId> 
        <artifactId>spring-ai-azure-vector-store-spring-boot-starter</artifactId>
        <version>0.8.0-SNAPSHOT</version>
    </dependency>
```

* Chroma

```xml
    <dependency>
        <groupId>org.springframework.ai</groupId> 
        <artifactId>spring-ai-chroma-store-spring-boot-starter</artifactId>
        <version>0.8.0-SNAPSHOT</version>
    </dependency>
```
* Milvus

```xml
    <dependency>
        <groupId>org.springframework.ai</groupId> 
        <artifactId>spring-ai-milvus-store-spring-boot-starter</artifactId>
        <version>0.8.0-SNAPSHOT</version>
    </dependency>
```

* PGVector

```xml
    <dependency>
        <groupId>org.springframework.ai</groupId> 
        <artifactId>spring-ai-pgvector-store-spring-boot-starter</artifactId>
        <version>0.8.0-SNAPSHOT</version>
    </dependency>
```
* Pinecone

```xml
    <dependency>
        <groupId>org.springframework.ai</groupId> 
        <artifactId>spring-ai-pinecone-store-spring-boot-starter</artifactId>
        <version>0.8.0-SNAPSHOT</version>
    </dependency>
```
* Weaviate

```xml
    <dependency>
        <groupId>org.springframework.ai</groupId> 
        <artifactId>spring-ai-weaviate-store-spring-boot-starter</artifactId>
        <version>0.8.0-SNAPSHOT</version>
    </dependency>
```
* Neo4j

```xml
    <dependency>
        <groupId>org.springframework.ai</groupId> 
        <artifactId>spring-ai-neo4j-store-spring-boot-starter</artifactId>
        <version>0.8.0-SNAPSHOT</version>
    </dependency>
```

## Workshop

* You can try out the features of Spring AI by following the [workshop material for Azure OpenAI](https://github.com/Azure-Samples/spring-ai-azure-workshop)
* To use the workshop material with OpenAI (not Azure's offering) you will need to *replace* the Azure Open AI Boot Starter in the `pom.xml` with the Open AI Boot Starter.
```xml
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
        <version>0.7.1-SNAPSHOT</version>
    </dependency>
```

## Overview

Despite the extensive history of AI, Java's role in this domain has been relatively minor.
This is mainly due to the historical reliance on efficient algorithms developed in languages such as C/C++, with Python serving as a bridge to access these libraries.
The majority of ML/AI tools were built around the Python ecosystem.
However, recent progress in Generative AI, spurred by innovations like OpenAI's ChatGPT, has popularized the interaction with pre-trained models via HTTP.
This eliminates much of the dependency on C/C++/Python libraries and opens the door to the use of programming languages such as Java.


The Python libraries [LangChain](https://docs.langchain.com/docs/) and [LlamaIndex](https://gpt-index.readthedocs.io/en/latest/getting_started/concepts.html) have become popular to implement Generative AI solutions and can be implemented in other programming languages.
These Python libraries share foundational themes with Spring projects, such as:

* Portable Service Abstractions
* Modularity
* Extensibility
* Reduction of boilerplate code
* Integration with diverse data sources
* Prebuilt solutions for common use cases

Taking inspiration from these libraries, the Spring AI project aims to provide a similar experience for Spring developers in the AI domain.

Note, that the Spring AI API is not a direct port of either LangChain or LlamaIndex.  You will see significant differences in the API if you are familiar with those two projects, though concepts and ideas are fairly portable.

## Feature Overview

This is a high level feature overview.
The features that are implemented lay the foundation, with subsequent more complex features building upon them.

You can find more details in the [Reference Documentation](https://docs.spring.io/spring-ai/reference/)

### Interacting with AI Models

**AI Client:** A foundational feature of Spring AI is a standardized client API for interfacing with generative AI models. With this common API, you can initially target g [OpenAI's Chat endpoint](https://platform.openai.com/docs/api-reference/chat) and easily swap about the implementation to use other platforms, such as [HuggingFace's Inference Endpoints](https://huggingface.co/inference-endpoints)

Dive deeper into [Models](https://docs.spring.io/spring-ai/reference/concepts.html#_models). in our concept guide.
For usage details, consult the  [AiClient API guide](https://docs.spring.io/spring-ai/reference/api/aiclient.html)


**Prompts:** Central to AI model interaction is the Prompt, which provides specific instructions for the AI to act upon.
Crafting an effective Prompt is both an art and science, giving rise to the discipline of "Prompt Engineering".
These prompts often leverage a templating engine for easy data substitution within predefined text using placeholders.

Explore more on [Prompts](https://docs.spring.io/spring-ai/reference/concepts.html#_prompts) in our concept guide.
To learn about the Prompt class, refer to the [Prompt API guide](https://docs.spring.io/spring-ai/reference/api/prompt.html).

**Prompt Templates:** Prompt Templates support the creation of prompts, particularly when a Template Engine is employed.

Delve into PromptTemplates in our [concept guide](https://docs.spring.io/spring-ai/reference/concepts.html#_prompt_templates).
For a hands-on guide to PromptTemplate, see the [PromptTemplate API guide](https://docs.spring.io/spring-ai/reference/api/prompt-template.html).

**Output Parsers:**  AI model outputs often come as raw `java.lang.String` values. Output Parsers restructure these raw strings into more programmer-friendly formats, such as CSV or JSON.

Get insights on Output Parsers in our [concept guide](https://docs.spring.io/spring-ai/reference/concepts.html#_output_parsing)..
For implementation details, visit the [OutputParser API guide](https://docs.spring.io/spring-ai/reference/api/output-parser.html).

### Incorporating your data

Incorporating proprietary data into Generative AI without retraining the model has been a breakthrough.
Retraining models, especially those with billions of parameters, is challenging due to the specialized hardware required.
The 'In-context' learning technique provides a simpler method to infuse your pre-trained model with data, whether from text files, HTML, or database results.
The right techniques are critical for developing successful solutions.


#### Retrieval Augmented Generation

Retrieval Augmented Generation, or RAG for short, is a pattern that enables you to bring your data to pre-trained models.
RAG excels in the 'query over your docs' use-case.

Learn more about [Retrieval Augmented Generation](https://docs.spring.io/spring-ai/reference/concepts.html#_retrieval_augmented_generation).

Bringing your data to the model follows an Extract, Transform, and Load (ETL) pattern.
The subsequent classes and interfaces support RAG's data preparation.

**Documents:**

The `Document` class encapsulates your data, including text and metadata, for the AI model.
While a Document can represent extensive content, such as an entire file, the RAG approach
segments content into smaller pieces for inclusion in the prompt.
The ETL process uses the interfaces `DocumentReader`, `DocumentTransformer`, and `DocumentWriter`, ending with data storage in a Vector Database.
This database later discerns the pieces of data that are pertinent to a user's query.


**Document Readers:**

Document Readers produce a `List<Document>` from diverse sources like PDFs, Markdown files, and Word documents.
Given that many sources are unstructured, Document Readers often segment based on content semantics, avoiding splits within tables or code sections.
After the initial creation of the `List<Document>`, the data flows through transformers for further refinement.

**Document Transformers:**

Transformers further modify the `List<Document>` by eliminating superfluous data, like PDF margins, or appending metadata (e.g., primary keywords or summaries).
Another critical transformation is subdividing documents to fit within the AI model's token constraints.
Each model has a context-window indicating its input and output data limits. Typically, one token equates to about 0.75 words. For instance, in model names like gpt-4-32k, "32K" signifies the token count.

**Document Writers:**

The final ETL step within RAG involves committing the data segments to a Vector Database.
Though the `DocumentWriter` interface isn't exclusively for Vector Database writing, it the main type of implementation.

**Vector Stores:**  Vector Databases are instrumental in incorporating your data with AI models.
They ascertain which document sections the AI should use for generating responses.
Examples of Vector Databases include Chroma, Postgres, Pinecone, Weaviate, Mongo Atlas, and Redis. Spring AI's `VectorStore` abstraction permits effortless transitions between database implementations.

## Building

To build with only unit tests

```shell
./mvnw clean package
```

To build including integration tests.
Set API key environment variables for OpenAI and Azure OpenAI before running.

```shell
./mvnw clean verify -Pintegration-tests
```

To run a specific integration test allowing for up to two attempts to succeed.  This is useful when a hosted service is not reliable or times out.
```shell
./mvnw -pl vector-stores/spring-ai-pgvector-store -Pintegration-tests -Dfailsafe.rerunFailingTestsCount=2 -Dit.test=PgVectorStoreIT verify

```
To build the docs
```shell
./mvnw -pl spring-ai-docs antora
```

The docs are then in the directory `spring-ai-docs/target/antora/site/index.html`
