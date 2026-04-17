# Redis Semantic Cache for Spring AI

This module provides a Redis-based implementation of semantic caching for Spring AI.

## Overview

Semantic caching allows storing and retrieving chat responses based on the semantic similarity of user queries.
This implementation uses Redis vector search capabilities to efficiently find similar queries and return cached responses.

## Features

- Store chat responses with their associated queries in Redis
- Retrieve responses based on semantic similarity
- Support for time-based expiration of cached entries
- Includes a ChatClient advisor for automatic caching
- Built on Redis vector search technology

## Requirements

- Redis Stack with Redis Query Engine and RedisJSON modules
- Java 17 or later
- Spring AI core dependencies
- An embedding model for vector generation

## Usage

### Maven Configuration

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-redis-semantic-cache</artifactId>
</dependency>
```

For Spring Boot applications, you can use the starter:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-vector-store-redis-semantic-cache</artifactId>
</dependency>
```

### Basic Usage

```java
// Create Redis client
JedisPooled jedisClient = new JedisPooled("localhost", 6379);

// Create the embedding model
EmbeddingModel embeddingModel = new OpenAiEmbeddingModel(apiKey);

// Create the semantic cache
SemanticCache semanticCache = DefaultSemanticCache.builder()
    .jedisClient(jedisClient)
    .embeddingModel(embeddingModel)
    .similarityThreshold(0.85) // Optional: adjust similarity threshold (0-1)
    .build();

// Create the cache advisor
SemanticCacheAdvisor cacheAdvisor = SemanticCacheAdvisor.builder()
    .cache(semanticCache)
    .build();

// Use with ChatClient
ChatResponse response = ChatClient.builder(chatModel)
    .build()
    .prompt("What is the capital of France?")
    .advisors(cacheAdvisor) // Add the advisor
    .call()
    .chatResponse();
```

### Direct Cache Usage

You can also use the cache directly:

```java
// Store a response
semanticCache.set("What is the capital of France?", parisResponse);

// Store with expiration
semanticCache.set("What's the weather today?", weatherResponse, Duration.ofHours(1));

// Retrieve a semantically similar response
Optional<ChatResponse> response = semanticCache.get("Tell me the capital city of France");

// Clear the cache
semanticCache.clear();
```

## Configuration Options

The `DefaultSemanticCache` can be configured with the following options:

- `jedisClient` - The Redis client
- `vectorStore` - Optional existing vector store to use
- `embeddingModel` - The embedding model for vector generation
- `similarityThreshold` - Threshold for determining similarity (0-1)
- `indexName` - The name of the Redis search index
- `prefix` - Key prefix for Redis documents

## Spring Boot Integration

When using Spring Boot and the Redis Semantic Cache starter, the components will be automatically configured.
You can customize behavior using properties in `application.properties` or `application.yml`:

```yaml
spring:
  ai:
    vectorstore:
      redis:
        semantic-cache:
          host: localhost
          port: 6379
          similarity-threshold: 0.85
          index-name: semantic-cache
```