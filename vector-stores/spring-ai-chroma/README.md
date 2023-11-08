# Chroma VectorStore

This readme will walk you through setting up the Chroma VectorStore to store document embeddings and perform similarity searches.

<https://github.com/chroma-core/chroma/pkgs/container/chroma>

## What is Chroma?

[Chroma](https://docs.trychroma.com/) is the open-source embedding database. It gives you the tools to store document embeddings, content and metadata and to search through those embeddings including metadata filtering.

## Prerequisites

1. OpenAI Account: Create an account at [OpenAI Signup](https://platform.openai.com/signup) and generate the token at [API Keys](https://platform.openai.com/account/api-keys).

2. Access to ChromeDB. The [setup local ChromaDB](#appendix_a) appendix show how to setup a DB locally with a Docker container.

On startup the `ChromaVectorStore` creates the required collection if one is not provisioned already.

## Configuration

To set up ChromaVectorStore, you'll need to provide your OpenAI API Key. Set it as an environment variable like so:

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

2. Chroma VectorStore.

   ```xml
   <dependency>
      <groupId>org.springframework.experimental.ai</groupId>
      <artifactId>spring-ai-chroma-store</artifactId>
      <version>0.7.0-SNAPSHOT</version>
   </dependency>
   ```

## Sample Code

Create an `RestTemplate` instance with proper ChromaDB authorization configurations and Use it to create `ChromaApi` instance:

```java
@Bean
public RestTemplate restTemplate() {
   return new RestTemplate();
}

@Bean
public ChromaApi chromaApi(RestTemplate restTemplate) {
   String chromaUrl = "http://localhost:8000";
   ChromaApi chromaApi =  ChromaApi(chromaUrl, restTemplate);
   return chromaApi;
}
```
> [!NOTE]
> For ChromaDB secured with [Static API Token Authentication](https://docs.trychroma.com/usage-guide#static-api-token-authentication) use the `ChromaApi#withKeyToken(<Your Token Credentials>)` method to set your credentials. Check the `ChromaWhereIT` for an example.

> [!NOTE]
> For ChromaDB secured with [Basic Authentication](https://docs.trychroma.com/usage-guide#basic-authentication) use the `ChromaApi#withBasicAuth(<your user>, <your password>)` method to set your credentials. Check the `BasicAuthChromaWhereIT` for an example.


Integrate with OpenAI's embeddings by adding the Spring Boot OpenAI starter to your project.
This provides you with an implementation of the Embeddings client:

```java
@Bean
public VectorStore chromaVectorStore(EmbeddingClient embeddingClient, ChromaApi chromaApi) {
 return new ChromaVectorStore(embeddingClient, chromaApi, "TestCollection");
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

### Metadata filtering

You can leverage the generic, portable [metadata filters](https://docs.spring.io/spring-ai/reference/api/vectordbs.html#_metadata_filters) with ChromaVector store as well.

For example you can use either the text expression language:

```java
vectorStore.similaritySearch("The World", TOP_K, SIMILARITY_THRESHOLD,
      "author in ['john', 'jill'] && article_type == 'blog'");
```

or programmatically using the `Filter.Expression` DSL:

```java
FilterExpressionBuilder b = new FilterExpressionBuilder();

vectorStore.similaritySearch("The World", TOP_K, SIMILARITY_THRESHOLD,
	b.and(
		b.in(List.of("john", "jill")),
		b.eq("article_type", "blog")).build());
```

NOTE: Those (portable) filter expressions get automatically converted into the proprietary Chroma `where` [filter expressions](https://docs.trychroma.com/usage-guide#using-where-filters).

For example this portable filter expression:

```sql
author in ['john', 'jill'] && article_type == 'blog'
```

is converted into the proprietary Chroma format:

```json
{"$and":[
	{"author": {"$in": ["john", "jill"]}},
	{"article_type":{"$eq":"blog"}}]
}"
```


## <a name="appendix_a" /> Appendix A: Run Chroma Locally

```
docker run -it --rm --name chroma -p 8000:8000 ghcr.io/chroma-core/chroma:0.4.15
```

starts a chroma store at <http://localhost:8000/api/v1>
