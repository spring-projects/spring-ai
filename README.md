# Spring AI [![build status](https://github.com/spring-projects-experimental/spring-ai/actions/workflows/continuous-integration.yml/badge.svg)](https://github.com/spring-projects-experimental/spring-ai/actions/workflows/continuous-integration.yml)

Welcome to the Spring AI project!

The Spring AI project provides a Spring-friendly API and abstractions for developing AI applications.

Let's make your `@Beans` intelligent!


## Project Links

* [Issues](https://github.com/spring-projects-experimental/spring-ai/issues)
* [Documentation](https://docs.spring.io/spring-ai/reference/)
* [JavaDocs](https://docs.spring.io/spring-ai/docs/current-SNAPSHOT/)

## Dependencies

Check out the workshop below but if you want to add the necessary goodies by hand, you will need to add the snapshot repository

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

* Azure OpenAI
```xml
    <dependency>
        <groupId>org.springframework.experimental.ai</groupId>
        <artifactId>spring-ai-azure-openai-spring-boot-starter</artifactId>
        <version>0.2.0-SNAPSHOT</version>
    </dependency>
```

* OpenAI

```xml
    <dependency>
        <groupId>org.springframework.experimental.ai</groupId>
        <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
        <version>0.2.0-SNAPSHOT</version>
    </dependency>
```

## Workshop

* You can try out the features of Spring AI by following the [workshop material for Azure OpenAI](https://github.com/markpollack/spring-ai-azure-workshop)
* To use the workshop material with OpenAI (not Azure's offering) you will need to *replace* the Azure Open AI Boot Starter in the `pom.xml` with the Open AI Boot Starter.
```xml
    <dependency>
        <groupId>org.springframework.experimental.ai</groupId>
        <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
        <version>0.2.0-SNAPSHOT</version>
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

## Feature Overview

The following is a feature list resembling those found in the LangChain documentation.
The initial features lay the foundation, with subsequent, more complex features building upon them.

You can find more details in the [Reference Documentation](https://docs.spring.io/spring-ai/reference/)

Not all features listed here are implemented yet, but a good amount are :)


### Model I/O

**AI Models:** A foundational feature is a common client API for interacting with generative AI Models.
A common API enables you to develop an application targeting OpenAI's ChatGPT HTTP interface and easily switch to Azure's OpenAI service, as an example.

**Prompts:** At the center of the AI model interaction is the Prompt - a set of instructions for the AI model to respond to.
Creating an effective Prompt is part art and part science, giving rise to the discipline of Prompt Engineering.
Prompts utilize a templating engine, enabling easy replacement of data within prompt text placeholders.

**Output Parsers:**  The AI responses are typically a raw `java.lang.String`. Output Parsers transform the raw String into structured formats like CSV or JSON, to make the output usable in a programming environment.
Output Parsers may also do additional post-processing on the response String.

### Incorporating your data

**Data Management:** A significant innovation in Generative AI involves enabling the model to understand your proprietary data without having to retrain the model's weights.  Retraining a model is a complex and compute-intensive task.
Recent Generative AI models have billions of parameters that require specialized hard-to-find hardware making it practically impossible to retrain the largest of models.
Instead, the 'In-context' learning technique lets you more easily incorporate your data into the pre-trained model.
This data can be from text files, HTML, database results, etc.
Effectively incorporating your data in an AI model requires specific techniques critical for developing successful solutions.

**Vector Stores:**  A widely used technique to incorporate your data in an AI model is using Vector Databases.
Vector Databases help to classify which part of your documents are most relevant for the AI model to use in creating a response.
Examples of Vector Databases are Chroma, Pinecone, Weaviate, Mongo Atlas, and RediSearch.
Spring IO abstracts these databases, allowing easy swapping of implementations.

### Chaining together multiple AI model interactions

**Chains:** Many AI solutions require multiple AI interactions to respond to a single user input.
"Chains" organize these interactions, offering modular AI workflows that promote reusability.
While you can create custom Chains tailored to your specific use case, pre-configured use-case-specific Chains are provided to accelerate your development.
Use cases such as Question-Answering, Text Generation, and Summarization are examples.

### Memory

**Memory:** To support multiple AI model interactions, your application must recall the previous inputs and outputs.
A variety of algorithms are available for different scenarios, often backed by databases like Redis, Cassandra, MongoDB, Postgres, and other database technologies.

### Agents

Beyond Chains, Agents represent the next level of sophistication.
Agents use the AI models themselves to determine the techniques and steps to respond to a user's query.
Agents might even dynamically access external data sources to retrieve information necessary for responding to a user.
It's getting a bit funky, isn't it?



## Building

To build with only unit tests

```shell
./mvnw clean package
```

To build including integration tests.
Set API key environment variables for OpenAI and Azure OpenAI before running.  

```shell
./mvnw clean package -Pintegration-tests
```

To build the docs
```shell
./mvnw -pl spring-ai-docs antora
```

The docs are then in the directory `spring-ai-docs/target/antora/site/index.html`