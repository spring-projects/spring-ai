# Spring AI Valkey Vector Store

A Valkey-based vector store implementation for Spring AI using Valkey Search with the Valkey Glide client.

## Documentation

For comprehensive documentation, see the [Valkey Search Documentation](https://valkey.io/topics/search/).

## Features

- Vector similarity search using KNN
- Support for multiple distance metrics (COSINE, L2, IP)
- HNSW and FLAT vector indexing algorithms
- Configurable metadata fields (TAG, NUMERIC)
- Filter expressions for advanced filtering
- Batch processing support

## Usage

### KNN Search

The standard similarity search returns the k-nearest neighbors:

```java
// Create the vector store
ValkeyVectorStore vectorStore = ValkeyVectorStore.builder(glideClient, embeddingModel)
    .indexName("my-index")
    .vectorAlgorithm(Algorithm.HNSW)
    .distanceMetric(DistanceMetric.COSINE)
    .initializeSchema(true)
    .build();

// Add documents
vectorStore.add(List.of(
    new Document("content1", Map.of("category", "AI")),
    new Document("content2", Map.of("category", "DB"))
));

// Search with KNN
List<Document> results = vectorStore.similaritySearch(
    SearchRequest.builder()
        .query("AI and machine learning")
        .topK(5)
        .similarityThreshold(0.7)
        .filterExpression("category == 'AI'")
        .build()
);
```

## Configuration Options

The Valkey Vector Store supports multiple configuration options:

```java
ValkeyVectorStore vectorStore = ValkeyVectorStore.builder(glideClient, embeddingModel)
    .indexName("custom-index")        // Valkey index name
    .prefix("custom-prefix:")         // Valkey key prefix
    .contentFieldName("content")      // Field for document content
    .embeddingFieldName("embedding")  // Field for vector embeddings
    .vectorAlgorithm(Algorithm.HNSW)  // Vector algorithm (HNSW or FLAT)
    .distanceMetric(DistanceMetric.COSINE) // Distance metric
    .metadataFields(                  // Metadata field definitions
        MetadataField.tag("category"),
        MetadataField.numeric("year")
    )
    .initializeSchema(true)           // Auto-create index schema
    .build();
```

## Distance Metrics

The Valkey Vector Store supports three distance metrics:

- **COSINE**: Cosine similarity (default)
- **L2**: Euclidean distance
- **IP**: Inner Product

Each metric is automatically normalized to a 0-1 similarity score, where 1 is most similar.

## Metadata Fields

Valkey Search supports the following metadata field types for filtering:

- **TAG**: For exact match filtering (e.g., categories, labels)
- **NUMERIC**: For range queries (e.g., year > 2020)

Example filter expressions:

```java
// Filter by tag
.filterExpression("category == 'AI'")

// Filter by numeric range
.filterExpression("year >= 2020")

// Combined filters
.filterExpression("category == 'AI' && year >= 2020")
```

## Client Setup

ValkeyVectorStore uses the Valkey Glide client. Create a client instance:

```java
GlideClientConfiguration config = GlideClientConfiguration.builder()
    .address(NodeAddress.builder()
        .host("localhost")
        .port(6379)
        .build())
    .build();

GlideClient glideClient = GlideClient.createClient(config).get();
```

For cluster mode, use `GlideClusterClient` instead.
