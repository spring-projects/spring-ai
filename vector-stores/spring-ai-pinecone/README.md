# Pinecone VectorStore

This readme will walk you through setting up the Pinecone VectorStore to store document embeddings and perform similarity searches.

## What is Pinecone?

[Pinecone](https://www.pinecone.io/) is a popular cloud-based vector database, which allows you to store and search vectors efficiently.

## Prerequisites

1. Pinecone Account: Before you start, ensure you sign up for a [Pinecone account](https://app.pinecone.io/).
2. Pinecone Project: Once registered, create a new project, an index, and generate an API key. You'll need these details for configuration.
3. OpenAI Account: Create an account at [OpenAI Signup](https://platform.openai.com/signup) and generate the token at [API Keys](https://platform.openai.com/account/api-keys)

## Configuration

To set up PineconeVectorStore, gather the following details from your Pinecone account:

* Pinecone API Key
* Pinecone Environment
* Pinecone Project ID
* Pinecone Index Name
* Pinecone Namespace

> **Note**
>  This information is available to you in the Pinecone UI portal.


When setting up embeddings, select a vector dimension of `1536`. This matches the dimensionality of OpenAI's model `text-embedding-ada-002`, which we'll be using for this guide.

Additionally, you'll need to provide your OpenAI API Key. Set it as an environment variable like so:

```bash
export SPRING_AI_OPENAI_API_KEY='Your_OpenAI_API_Key'
```

## Dependencies

Add these dependencies to your project:

1. OpenAI: Required for calculating embeddings.

```xml
    <dependency>
        <groupId>org.springframework.experimental.ai</groupId>
        <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
        <version>0.7.0-SNAPSHOT</version>
    </dependency>
```

2. Pinecone

```xml
    <dependency>
        <groupId>org.springframework.experimental.ai</groupId>
        <artifactId>spring-ai-pinecone</artifactId>
        <version>0.7.0-SNAPSHOT</version>
    </dependency>
```

## Sample Code

To configure Pinecone in your application, you can use the following setup:

```java
@Bean
public PineconeVectorStoreConfig pineconeVectorStoreConfig() {

    return PineconeVectorStoreConfig.builder()
        .withApiKey(<PINECONE_API_KEY>)
        .withEnvironment("gcp-starter")
        .withProjectId("89309e6")
        .withIndexName("spring-ai-test-index")
        .withNamespace("") // the free tier doesn't support namespaces.
        .build();
}
```

Integrate with OpenAI's embeddings by adding the Spring Boot OpenAI starter to your project.
This provides you with an implementation of the Embeddings client:

```java
@Bean
public VectorStore vectorStore(PineconeVectorStoreConfig config, EmbeddingClient embeddingClient) {
    return new PineconeVectorStore(config, embeddingClient);
}
```

In your main code, create some documents

```java
List<Document> documents = List.of(
	new Document("Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!", Map.of("meta1", "meta1")),
	new Document("The World is Big and Salvation Lurks Around the Corner"),
	new Document("You walk forward facing the past and you turn back toward the future.", Map.of("meta2", "meta2")));
```

Add the documents to your vector store:

```java
vectorStore.add(List.of(document));
```

And finally, retrieve documents similar to a query:

```java
List<Document> results = vectorStore.similaritySearch("Spring", 5);
```

If all goes well, you should retrieve the document containing the text "Spring AI rocks!!".