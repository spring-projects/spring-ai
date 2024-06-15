# Spring AI [![build status](https://github.com/spring-projects/spring-ai/actions/workflows/continuous-integration.yml/badge.svg)](https://github.com/spring-projects/spring-ai/actions/workflows/continuous-integration.yml)

Welcome to the Spring AI project!

The Spring AI project provides a Spring-friendly API and abstractions for developing AI applications.

Let's make your `@Beans` intelligent!

For further information go to our [Spring AI reference documentation](https://docs.spring.io/spring-ai/reference/).

## Breaking changes

* Refer to the [upgrade notes](https://docs.spring.io/spring-ai/reference/upgrade-notes.html) to see how to upgrade to 1.0.0.M1 or higher.

## Project Links

* [Documentation](https://docs.spring.io/spring-ai/reference/)
* [Issues](https://github.com/spring-projects/spring-ai/issues)
* [Discussions](https://github.com/spring-projects/spring-ai/discussions) - Go here if you have a question, suggestion, or feedback!


## Educational Resources

- Follow the [Workshop material for Azure OpenAI](https://github.com/Azure-Samples/spring-ai-azure-workshop)
  - The workshop contains step-by-step examples from 'hello world' to 'retrieval augmented generation'

Some selected videos.  Search YouTube! for more.

- Spring Tips: Spring AI
<br>[![Watch Spring Tips video](https://img.youtube.com/vi/aNKDoiOUo9M/default.jpg)](https://www.youtube.com/watch?v=aNKDoiOUo9M)
* Overview of Spring AI @ Devoxx 2023
<br>[![Watch the Devoxx 2023 video](https://img.youtube.com/vi/7OY9fKVxAFQ/default.jpg)](https://www.youtube.com/watch?v=7OY9fKVxAFQ)
* Introducing Spring AI - Add Generative AI to your Spring Applications
<br>[![Watch the video](https://img.youtube.com/vi/1g_wuincUdU/default.jpg)](https://www.youtube.com/watch?v=1g_wuincUdU)

## Getting Started

Please refer to the [Getting Started Guide](https://docs.spring.io/spring-ai/reference/getting-started.html) for instruction on adding your dependencies.

Note, the new Spring CLI project lets you get up and running in two simple steps, [described in detail here](https://docs.spring.io/spring-ai/reference/getting-started.html#spring-cli).
1. Install Spring CLI
2. Type `spring boot new --from ai --name myai` in your terminal


### Adding Dependencies manually

Note that are two main steps.

1. [Add the Spring Milestone and Snapshot repositories to your build system](https://docs.spring.io/spring-ai/reference/getting-started.html#repositories).
2. Add the [Spring AI BOM](https://docs.spring.io/spring-ai/reference/getting-started.html#dependency-management)
3. [Add dependencies](https://docs.spring.io/spring-ai/reference/getting-started.html#add-dependencies) for the specific AI model, Vector Database or other component dependencies you require.


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

**ChatClient:** A foundational feature of Spring AI is a portable client API for interacting with generative AI models.  With this portable API, you can initially target one AI chat model, for example OpenAI and then easily swap out the implementation to another AI chat model, for example Amazon Bedrock's Anthropic Model.  When necessary, you can also drop down to use non-portable model options.

Spring AI supports many AI models.  For an overview see here.  Specific models currently supported are
* OpenAI
* Azure OpenAI
* Amazon Bedrock (Anthropic, Llama, Cohere, Titan, Jurassic2)
* Hugging Face
* Google VertexAI (PaLM2, Gemini)
* Mistral AI
* Stability AI
* Ollama
* PostgresML
* Transformers (ONNX)
* Anthropic Claude3
* MiniMax


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
For implementation details, visit the [StructuredOutputConverter API guide](https://docs.spring.io/spring-ai/reference/api/output-parser.html).

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
Examples of Vector Databases include Chroma, Oracle, Postgres, Pinecone, Qdrant, Weaviate, Mongo Atlas, and Redis. Spring AI's `VectorStore` abstraction permits effortless transitions between database implementations.



## Cloning the repo

This repository contains [large model files](https://github.com/spring-projects/spring-ai/tree/main/models/spring-ai-transformers/src/main/resources/onnx/all-MiniLM-L6-v2).
To clone it you have to either:

- Ignore the large files (won't affect the spring-ai behaviour) :  `GIT_LFS_SKIP_SMUDGE=1 git clone git@github.com:spring-projects/spring-ai.git`.
- Or install the [Git Large File Storage](https://git-lfs.com/) before cloning the repo.


## Building

To build with running unit tests

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

To reformat using the [java-format plugin](https://github.com/spring-io/spring-javaformat)
```shell
./mvnw spring-javaformat:apply
```

To update the year on license headers using the [license-maven-plugin](https://oss.carbou.me/license-maven-plugin/#goals)
```shell
./mvnw license:update-file-header -Plicense
```

To check javadocs using the [javadoc:javadoc](https://maven.apache.org/plugins/maven-javadoc-plugin/)
```shell
./mvnw javadoc:javadoc -Pjavadoc
```
