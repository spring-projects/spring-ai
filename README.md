# Spring AI [![build status](https://github.com/spring-projects-experimental/spring-ai/actions/workflows/continuous-integration.yml/badge.svg)](https://github.com/spring-projects-experimental/spring-ai/actions/workflows/continuous-integration.yml)

Welcome to the Spring AI project!

The Spring AI provides a Spring-friendly API and abstractions for developing AI applications.

Let's make your `@Beans` intelligent!

## Overview

Despite the extensive history of AI, Java's role in this domain has been relatively minor.
This is mainly due to the historical reliance on efficient algorithms developed in languages such as C/C++, with Python services as bridges to access these libraries.
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
* Prebuilt solutions for common use cases such as Question-Answering, Text Generation, and Summarization.

Taking inspiration from these libraries, the Spring AI project aims to provide a similar experience for Spring developers in the AI domain.

## Feature Overview

The following is a feature list resembling those found in the LangChain documentation.
The initial features lay the foundation, with subsequent, more complex features building upon them.
Implemented features are linked to the Spring AI documentation.
Features without links are part of our future roadmap.

### Model I/O

**Language Models:** A foundational feature is a common client API for interacting with various Large Language Models (LLMs).
A common API enables you to develop an application targeting OpenAI's ChatGPT HTTP interface and easily switch to Azure's OpenAI service, as an example.

**Prompts:** At the center of LLM interaction is the Prompt - a set of instructions for the LLM to respond to.
Creating an effective part is part art and part science, giving rise to the discipline of Prompt Engineering.
Prompts utilize a templating engine, enabling easy replacement of data within prompt text placeholders.

**Output Parsers:**  LLM responses are typically a raw `java.lang.String`.  
Output Parsers transform the raw String into structured formats like CSV or JSON, to make the output usable in a programming environment.
Output Parser may also do additional post-processing on the response String.

### Incorporating your data

**Data Management:** A significant innovation in Generative AI involves enabling LLMs to understand your proprietary data without having to retrain the model's weights.  Retraining a model is a complex and compute-intensive task.  
Recent Generative AI models have billions of parameters that require specialized hard-to-find hardware making it practically impossible to retrain the largest of models.
Instead, the 'In-context' learning technique lets you more easily incorporate your data into the pre-trained model.
This data can be from text files, HTML, database results, etc.
Effectively incorporating your data in a LLM requires specific techniques critical for developing successful solutions.

**Vector Stores:**  A widely used technique to incorporate your data in a LLM is using Vector Databases.
Vector Databases help to classify which part of your documents are most relevant for the LLM to use in creating a response.
Examples of Vector Databases are Chroma, Pinecone, Weaviate, Mongo Atlas, and RediSearch.
Spring IO abstracts these databases, allowing easy swapping of implementations.

### Chaining together multiple LLM interactions

**Chains:** Many AI solutions require multiple LLM interactions to respond to a single user input.
"Chains" organize these interactions, offering modular AI workflows that promote reusability.
While you can create custom Chains tailored to your specific use case, pre-configured use-case-specific Chains are provided to accelerate your development.

### Memory

**Memory:** To support multiple LLM interactions, your application must recall the previous inputs and outputs.
A variety of algorithms are available for different scenarios, often backed by databases like Redis, Cassandra, MongoDB, Postgres, and other database technologies.

### Agents

Beyond Chains, Agents represent the next level of sophistication.
Agents use the LLM to determine the techniques and steps to respond to a user's query.
Agents might even dynamically access external data sources to retrieve information necessary for responding to a user.
It's getting a bit funky, isn't it?


## Project Links

* [Issues](https://github.com/spring-projects-experimental/spring-ai/issues)
* Documentation
* [JavaDocs](https://docs.spring.io/spring-ai/docs/current-SNAPSHOT/)

