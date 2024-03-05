# GemFire Vector Store

This readme walks you through setting up the GemFire `VectorStore` to store document embeddings and perform similarity searches.

## What is GemFire?

[GemFire](https://tanzu.vmware.com/gemfire) is an ultra high speed in-memory data and compute grid, with vector extensions to store and search vectors efficiently.

## What is GemFire Vector Database?

[GemFire VectorDB](https://docs.vmware.com/en/VMware-GemFire-VectorDB/1.0/gemfire-vectordb/overview.html) extends GemFire's capabilities, serving as a versatile vector database that efficiently stores, retrieves, and performs vector searches through a distributed and resilient infrastructure:

Capabilities:
- Create Indexes
- Store vectors and the associated metadata
- Perform vector searches based on similarity 

## Prerequisites

1. Access to a GemFire cluster with the [GemFire Vector Database](https://docs.vmware.com/en/VMware-GemFire-VectorDB/1.0/gemfire-vectordb/install.html) extension installed


## Dependencies

Add these dependencies to your project:

- Embedding Client boot starter, required for calculating embeddings. 
- Transformers Embedding (Local) and follow the ONNX Transformers Embedding instructions.

 ```xml
   <dependency>
     <groupId>org.springframework.
      ai</groupId>
     <artifactId>spring-ai-transformers
      </artifactId>
     <version>${parent.version}</version>
     <scope>test</scope>
 </dependency>
 ```

2. Add the GemFire VectorDB dependencies

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-gemfire</artifactId>
    <version>0.8.1-SNAPSHOT</version>
</dependency>
```

## Configuration

1. To configure GemFire in your application, use the following setup:

```java
@Bean
public GemFireVectorStoreConfig gemFireVectorStoreConfig() {
    return GemFireVectorStoreConfig.builder()
        .withUrl("http://localhost:8080")
        .withIndexName("spring-ai-test-index")
        .build();
}
```

2. Create a GemFireVectorStore instance connected to your GemFire VectorDB:

```java
 @Bean
 public VectorStore vectorStore(GemFireVectorStoreConfig config, EmbeddingClient embeddingClient) {
    return new GemFireVectorStore(config, embeddingClient);
}
```
3. Create a Vector Index which will configure GemFire region.

```java
  public void createIndex() {
		try {
			CreateRequest createRequest = new CreateRequest();
			createRequest.setName(INDEX_NAME);
			createRequest.setBeamWidth(20);
			createRequest.setMaxConnections(16);
			ObjectMapper objectMapper = new ObjectMapper();
			String index = objectMapper.writeValueAsString(createRequest);
			client.post()
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(index)
				.retrieve()
				.bodyToMono(Void.class)
				.block();
		}
		catch (Exception e) {
			logger.warn("An unexpected error occurred while creating the index");
		}
	}
```

4. Create some documents:

```java
    List<Document> documents = List.of(
        new Document("1", getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1")),
        new Document("2", getText("classpath:/test/data/time.shelter.txt"), Map.of()),
        new Document("3", getText("classpath:/test/data/great.depression.txt"), Map.of("meta2", "meta2")));
```

5. Add the documents to GemFire VectorDB:

```java
vectorStore.add(List.of(document));
```

6. And finally, retrieve documents similar to a query:

```java
  List<Document> results = vectorStore.similaritySearch("Spring", 5);
```

If all goes well, you should retrieve the document containing the text "Spring AI rocks!!".

