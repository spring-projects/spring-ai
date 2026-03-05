# Spring AI Redis Vector Store

A Redis-based vector store implementation for Spring AI using Redis Stack with Redis Query Engine and RedisJSON.

## Documentation

For comprehensive documentation, see
the [Redis Vector Store Documentation](https://docs.spring.io/spring-ai/reference/api/vectordbs/redis.html).

## Features

- Vector similarity search using KNN
- Range-based vector search with radius threshold
- Text-based search on TEXT fields
- Support for multiple distance metrics (COSINE, L2, IP)
- Multiple text scoring algorithms (BM25, TFIDF, etc.)
- HNSW and FLAT vector indexing algorithms
- Configurable metadata fields (TEXT, TAG, NUMERIC)
- Filter expressions for advanced filtering
- Batch processing support

## Usage

### KNN Search

The standard similarity search returns the k-nearest neighbors:

```java
// Create the vector store
RedisVectorStore vectorStore = RedisVectorStore.builder(jedisPooled, embeddingModel)
    .indexName("my-index")
    .vectorAlgorithm(Algorithm.HNSW)
    .distanceMetric(DistanceMetric.COSINE)
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

### Text Search

The text search capability allows you to find documents based on keywords and phrases in TEXT fields:

```java
// Search for documents containing specific text
List<Document> textResults = vectorStore.searchByText(
    "machine learning",   // search query
    "content",            // field to search (must be TEXT type)
    10,                   // limit
    "category == 'AI'"    // optional filter expression
);
```

Text search supports:

- Single word searches
- Phrase searches with exact matching when `inOrder` is true
- Term-based searches with OR semantics when `inOrder` is false
- Stopword filtering to ignore common words
- Multiple text scoring algorithms (BM25, TFIDF, DISMAX, etc.)

Configure text search behavior at construction time:

```java
RedisVectorStore vectorStore = RedisVectorStore.builder(jedisPooled, embeddingModel)
    .textScorer(TextScorer.TFIDF)                    // Text scoring algorithm
    .inOrder(true)                                   // Match terms in order
    .stopwords(Set.of("is", "a", "the", "and"))      // Ignore common words
    .metadataFields(MetadataField.text("description")) // Define TEXT fields
    .build();
```

### Range Search

The range search returns all documents within a specified radius:

```java
// Search with radius
List<Document> rangeResults = vectorStore.searchByRange(
    "AI and machine learning",  // query
    0.8,                        // radius (similarity threshold)
    "category == 'AI'"          // optional filter expression
);
```

You can also set a default range threshold at construction time:

```java
RedisVectorStore vectorStore = RedisVectorStore.builder(jedisPooled, embeddingModel)
    .defaultRangeThreshold(0.8)  // Set default threshold
    .build();

// Use default threshold
List<Document> results = vectorStore.searchByRange("query");
```

## Configuration Options

The Redis Vector Store supports multiple configuration options:

```java
RedisVectorStore vectorStore = RedisVectorStore.builder(jedisPooled, embeddingModel)
    .indexName("custom-index")        // Redis index name
    .prefix("custom-prefix")          // Redis key prefix
    .contentFieldName("content")      // Field for document content
    .embeddingFieldName("embedding")  // Field for vector embeddings
    .vectorAlgorithm(Algorithm.HNSW)  // Vector algorithm (HNSW or FLAT)
    .distanceMetric(DistanceMetric.COSINE) // Distance metric
    .hnswM(32)                        // HNSW parameter for connections
    .hnswEfConstruction(100)          // HNSW parameter for index building
    .hnswEfRuntime(50)                // HNSW parameter for search
    .defaultRangeThreshold(0.8)       // Default radius for range searches
    .textScorer(TextScorer.BM25)      // Text scoring algorithm
    .inOrder(true)                    // Match terms in order
    .stopwords(Set.of("the", "and"))  // Stopwords to ignore
    .metadataFields(                  // Metadata field definitions
        MetadataField.tag("category"),
        MetadataField.numeric("year"),
        MetadataField.text("description")
    )
    .initializeSchema(true)           // Auto-create index schema
    .build();
```

## Distance Metrics

The Redis Vector Store supports three distance metrics:

- **COSINE**: Cosine similarity (default)
- **L2**: Euclidean distance
- **IP**: Inner Product

Each metric is automatically normalized to a 0-1 similarity score, where 1 is most similar.

## Text Scoring Algorithms

For text search, several scoring algorithms are supported:

- **BM25**: Modern version of TF-IDF with term saturation (default)
- **TFIDF**: Classic term frequency-inverse document frequency
- **BM25STD**: Standardized BM25
- **DISMAX**: Disjunction max
- **DOCSCORE**: Document score

Scores are normalized to a 0-1 range for consistency with vector similarity scores.