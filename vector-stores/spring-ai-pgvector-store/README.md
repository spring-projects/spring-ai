# PGvector VectorStore

This readme will walk you through setting up the PGvector VectorStore to store document embeddings and perform similarity searches.

## What is PGvector?

[PGvector](https://github.com/pgvector/pgvector) is an open-source extension for PostgreSQL that enables storing and searching over machine learning-generated embeddings. It provides different capabilities that let users identify both exact and approximate nearest neighbors. It is designed to work seamlessly with other PostgreSQL features, including indexing and querying.

## Prerequisites

1. Access to PostgresSQL instance with required database credentials.
For test purposes you can deploy a local, Docker PostgresSQL/PGvector instance. See the [Run Postgres & PGVector DB locally](#appendix_a) appendix.
2. OpenAI Account: Create an account at [OpenAI Signup](https://platform.openai.com/signup) and generate the token at [API Keys](https://platform.openai.com/account/api-keys)

## Configuration

To set up PgVectorStore, you need to provide (via application.yaml) configurations to your PostgresSQL database.

Additionally, you'll need to provide your OpenAI API Key. Set it as an environment variable like so:

```bash
export SPRING_AI_OPENAI_API_KEY='Your_OpenAI_API_Key'
```

## Dependencies

Add these dependencies to your project:

1. PostgresSQL connection and JdbcTemplate auto-configuration.

	```xml
	<dependency>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-jdbc</artifactId>
	</dependency>

	<dependency>
		<groupId>org.postgresql</groupId>
		<artifactId>postgresql</artifactId>
		<scope>runtime</scope>
	</dependency>
	```

2. OpenAI: Required for calculating embeddings.

	```xml
	<dependency>
		<groupId>org.springframework.experimental.ai</groupId>
		<artifactId>spring-ai-openai-spring-boot-starter</artifactId>
		<version>0.7.0-SNAPSHOT</version>
	</dependency>
	```

3. PGvector

	```xml
	<dependency>
		<groupId>org.springframework.experimental.ai</groupId>
		<artifactId>spring-ai-pgvector-store</artifactId>
		<version>0.7.0-SNAPSHOT</version>
	</dependency>
	```

## Sample Code

To configure PgVectorStore in your application, you can use the following setup:

Add to `application.yml` (using your DB credentials):

```yml
spring:
	datasource:
		url: jdbc:postgresql://localhost:5432/vector_store
		username: postgres
		password: postgres
```

Integrate with OpenAI's embeddings by adding the Spring Boot OpenAI starter to your project.
This provides you with an implementation of the Embeddings client:

```java
@Bean
public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingClient embeddingClient) {
	return new PgVectorStore(jdbcTemplate, embeddingClient);
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

## <a name="appendix_a" /> Appendix A: Run Postgres & PGVector DB locally

```
docker run -it --rm --name postgres -p 5432:5432 -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres ankane/pgvector
```

You can connect to this server like this:

```
psql -U postgres -h localhost -p 5432
```
