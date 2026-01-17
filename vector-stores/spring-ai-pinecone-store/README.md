# Spring AI Pinecone Vector Store

A Pinecone-based vector store implementation for Spring AI using Pinecone cloud vector database.

## Documentation

See the [Pinecone Vector Store Documentation](https://docs.spring.io/spring-ai/reference/api/vectordbs/pinecone.html).

## Features

- Vector similarity search using cosine similarity
- Namespace support for data organization
- Metadata filtering with filter expressions
- Document CRUD operations (Create, Read, Update, Delete)
- Type-safe builder pattern for configuration
- Batch processing support
- Configurable field names for content and distance metadata

## Usage

### Maven Configuration

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-pinecone-store</artifactId>
</dependency>
```

### Basic Usage

```java
// Create the embedding model
EmbeddingModel embeddingModel = new OpenAiEmbeddingModel(apiKey);

// Create the vector store using builder pattern
PineconeVectorStore vectorStore = PineconeVectorStore.builder(embeddingModel)
    .apiKey("your-pinecone-api-key")
    .indexName("your-index-name")
    .build();

// Add documents
vectorStore.add(List.of(
    new Document("Spring AI makes building AI applications easy", Map.of("category", "AI")),
    new Document("Pinecone is a cloud vector database", Map.of("category", "Database"))
));

// Similarity search
List<Document> results = vectorStore.similaritySearch(
    SearchRequest.builder()
        .query("AI development")
        .topK(5)
        .similarityThreshold(0.7)
        .build()
);
```

### Using Namespaces

Namespaces allow you to partition your data within a single index:

```java
// Create vector store with namespace
PineconeVectorStore vectorStore = PineconeVectorStore.builder(embeddingModel)
    .apiKey("your-pinecone-api-key")
    .indexName("your-index-name")
    .namespace("production")  // Partition data by namespace
    .build();

// Add documents to specific namespace
vectorStore.add(documents, "production");

// Search within specific namespace
List<Document> results = vectorStore.similaritySearch(request, "production");
```

**Note:** The free-tier (gcp-starter) does not support namespaces. Leave the namespace empty for free tier usage.

### Metadata Filtering

Search documents with metadata filters using filter expressions:

```java
// Create documents with metadata
var document1 = new Document("Content about AI",
    Map.of("category", "AI", "year", 2024));
var document2 = new Document("Content about databases",
    Map.of("category", "Database", "year", 2023));

vectorStore.add(List.of(document1, document2));

// Search with filter
List<Document> results = vectorStore.similaritySearch(
    SearchRequest.builder()
        .query("technology")
        .topK(10)
        .filterExpression("category == 'AI' && year >= 2024")
        .build()
);
```

Supported filter operations:

- Equality: `category == 'AI'`
- Inequality: `year != 2023`
- Comparison: `year >= 2024`, `score < 0.5`
- Logical: `category == 'AI' && year >= 2024`
- Negation: `NOT(category == 'Database')`

### Deleting Documents

```java
// Delete by document IDs
vectorStore.delete(List.of("doc-id-1", "doc-id-2"));

// Delete by filter expression
Filter.Expression filter = new Filter.Expression(
    Filter.ExpressionType.EQ,
    new Filter.Key("category"),
    new Filter.Value("outdated")
);
vectorStore.delete(filter);
```

## Configuration Options

The Pinecone Vector Store supports multiple configuration options:

```java
PineconeVectorStore vectorStore = PineconeVectorStore.builder(embeddingModel)
    .apiKey("your-api-key")                        // Required: Pinecone API key
    .indexName("your-index")                       // Required: Pinecone index name
    .namespace("production")                       // Optional: Namespace (empty for free tier)
    .contentFieldName("custom_content")            // Optional: Field name for content (default: "document_content")
    .distanceMetadataFieldName("custom_distance")  // Optional: Field name for distance metadata
    .build();
```

### Builder Pattern

The Pinecone Vector Store uses a type-safe step builder pattern that enforces required fields:

```java
// Step 1: Provide embedding model
PineconeVectorStore.builder(embeddingModel)
    // Step 2: Provide API key (required)
    .apiKey("your-api-key")
    // Step 3: Provide index name (required)
    .indexName("your-index")
    // Step 4: Optional configurations
    .namespace("production")
    .contentFieldName("content")
    // Final step: Build
    .build();
```

## Spring Boot Integration

When using Spring Boot, you can configure the vector store as a bean:

```java
@Configuration
public class VectorStoreConfig {

    @Bean
    public PineconeVectorStore vectorStore(EmbeddingModel embeddingModel) {
        return PineconeVectorStore.builder(embeddingModel)
            .apiKey(System.getenv("PINECONE_API_KEY"))
            .indexName("spring-ai-index")
            .build();
    }
}
```

## Additional Resources

- [Pinecone Documentation](https://docs.pinecone.io/)
- [Spring AI Reference](https://docs.spring.io/spring-ai/reference/)
- [Pinecone Java Client](https://github.com/pinecone-io/pinecone-java-client)