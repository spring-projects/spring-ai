# Neo4j Store

This readme walks you through setting up `Neo4jVectorStore` to store document embeddings and perform similarity searches.

## What is Neo4j?

[Neo4j](https://neo4j.com) is an open source NoSQL graph database.
It is a fully transactional database (ACID) that stores data structured as graphs consisting of nodes, connected by relationships.
Inspired by the structure of the real world, it allows for high query performance on complex data, while remaining intuitive and simple for the developer.

## What is Neo4j Vector Search?

[Neo4j's Vector Search](https://neo4j.com/docs/cypher-manual/current/indexes-for-vector-search/) got introduced in Neo4j 5.11 and was considered GA with the release of version 5.13.
Embeddings can be stored on _Node_ properties and can be queried with the [`db.index.vector.queryNodes()`](https://neo4j.com/docs/operations-manual/5/reference/procedures/#procedure_db_index_vector_queryNodes) function.
Those indexes are powered by Lucene using a Hierarchical Navigable Small World Graph (HNSW) to perform a k approximate nearest neighbors (k-ANN) query over the vector fields.

## Prerequisites

1. OpenAI Account: Create an account at [OpenAI Signup](https://platform.openai.com/signup) and generate the token at [API Keys](https://platform.openai.com/account/api-keys).

2. A running Neo4j (5.13+) instance
    1. [Docker](https://hub.docker.com/_/neo4j) image _neo4j:5.13_
	2. [Neo4j Desktop](https://neo4j.com/download/)
    3. [Neo4j Aura](https://neo4j.com/cloud/aura-free/)
	4. [Neo4j Server](https://neo4j.com/deployment-center/) instance

## Configuration

To connect to Neo4j and use the `Neo4jVectorStore`, you need to provide (e.g. via `application.properties`) configurations for your instance.

Additionally, you'll need to provide your OpenAI API Key. Set it as an environment variable like so:

```bash
export SPRING_AI_OPENAI_API_KEY='Your_OpenAI_API_Key'
```

## Repository

To acquire Spring AI artifacts, declare the Spring Snapshot repository:

```xml
<repository>
	<id>spring-snapshots</id>
	<name>Spring Snapshots</name>
	<url>https://repo.spring.io/snapshot</url>
	<releases>
		<enabled>false</enabled>
	</releases>
</repository>
```

## Dependencies

Add these dependencies to your project:

1. OpenAI: Required for calculating embeddings.
```xml
<dependency>
	<groupId>org.springframework.experimental.ai</groupId>
	<artifactId>spring-ai-openai-spring-boot-starter</artifactId>
	<version>0.7.1-SNAPSHOT</version>
</dependency>
```

2. Neo4j Vector Store

```xml
<dependency>
	<groupId>org.springframework.experimental.ai</groupId>
	<artifactId>spring-ai-neo4j-store</artifactId>
	<version>0.7.1-SNAPSHOT</version>
</dependency>
```

## Sample Code

To configure `Neo4jVectorStore` in your application, you can use the following setup:

Add to `application.properties` (using your Neo4j credentials):

```
spring.neo4j.uri=neo4j://localhost:7687
spring.neo4j.authentication.username=neo4j
spring.neo4j.authentication.password=password
```

Integrate with OpenAI's embeddings by adding the Spring Boot OpenAI Starter to your project.
This provides you with an implementation of the Embeddings client:

```java
public VectorStore vectorStore(Driver driver, EmbeddingClient embeddingClient) {
	return new Neo4jVectorStore(driver, embeddingClient,
			Neo4jVectorStore.Neo4jVectorStoreConfig.defaultConfig());
}
```

In your main code, create some documents:

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
List<Document> results = vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(5));
```

If all goes well, you should retrieve the document containing the text "Spring AI rocks!!" as the first result.

## Neo4jVectorStore config

As you have already noticed, the `Neo4jVectorStore` accepts a configuration parameter.
The default configuration should fit for most of the basic use-cases, but if you want to tweak it a little bit for you needs, you can edit those defaults.

The default params
* embedding dimension = 1536
* distance type = cosine
* document node label = "Document"
* node property for embedding = "embedding"
* database name = "neo4j"

can be configured with

```java
Neo4jVectorStore.Neo4jVectorStoreConfig.builder()
		.withDatabaseName("databaseName")
		.withDistanceType(Neo4jVectorStore.Neo4jDistanceType.COSINE / Neo4jVectorStore.Neo4jDistanceType.EUCLIDEAN)
		.withLabel("CustomLabel")
		.withEmbeddingProperty("vectorEmbedding")
		.withEmbeddingDimension(1024)
```
