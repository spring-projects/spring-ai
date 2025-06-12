# Redis Chat Memory for Spring AI

This module provides a Redis-based implementation of the Spring AI `ChatMemory` and `ChatMemoryRepository` interfaces.

## Overview

The `RedisChatMemory` class offers a persistent chat memory solution using Redis (with JSON and Query Engine support).
It stores chat messages as JSON documents and provides efficient querying capabilities for conversation management.

## Features

- Persistent storage of chat messages using Redis
- Message querying by conversation ID
- Support for message pagination and limiting
- Configurable time-to-live for automatic message expiration
- Efficient retrieval of conversation metadata
- Implements `ChatMemory`, `ChatMemoryRepository`, and `AdvancedChatMemoryRepository` interfaces
- Advanced query capabilities:
  - Search messages by content keywords
  - Find messages by type (USER, ASSISTANT, SYSTEM, TOOL)
  - Query messages within time ranges
  - Search by metadata fields
  - Execute custom Redis search queries

## Requirements

- Redis Stack with JSON and Search capabilities
- Java 17 or later
- Spring AI core dependencies

## Usage

### Maven Configuration

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-model-chat-memory-redis</artifactId>
</dependency>
```

For Spring Boot applications, you can use the starter:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-chat-memory-redis</artifactId>
</dependency>
```

### Basic Usage

```java
// Create a Jedis client
JedisPooled jedisClient = new JedisPooled("localhost", 6379);

// Configure and create the RedisChatMemory
RedisChatMemory chatMemory = RedisChatMemory.builder()
    .jedisClient(jedisClient)
    .timeToLive(Duration.ofDays(7)) // Optional: messages expire after 7 days
    .build();

// Use the chat memory
String conversationId = "user-123";
chatMemory.add(conversationId, new UserMessage("Hello, AI assistant!"));
chatMemory.add(conversationId, new AssistantMessage("Hello! How can I help you today?"));

// Retrieve messages
List<Message> messages = chatMemory.get(conversationId, 10); // Get last 10 messages

// Clear a conversation
chatMemory.clear(conversationId);

// Find all conversations (using ChatMemoryRepository interface)
List<String> allConversationIds = chatMemory.findConversationIds();
```

### Advanced Query Features

The `RedisChatMemory` also implements `AdvancedChatMemoryRepository`, providing powerful query capabilities:

```java
// Search messages by content
List<MessageWithConversation> results = chatMemory.findByContent("AI assistant", 10);

// Find messages by type
List<MessageWithConversation> userMessages = chatMemory.findByType(MessageType.USER, 20);

// Query messages within a time range
List<MessageWithConversation> recentMessages = chatMemory.findByTimeRange(
    "conversation-id",  // optional - null for all conversations
    Instant.now().minus(1, ChronoUnit.HOURS),
    Instant.now(),
    50
);

// Search by metadata
List<MessageWithConversation> priorityMessages = chatMemory.findByMetadata(
    "priority",  // metadata key
    "high",      // metadata value
    10
);

// Execute custom Redis search query
List<MessageWithConversation> customResults = chatMemory.executeQuery(
    "@type:USER @content:help",  // Redis search syntax
    25
);
```

### Metadata Schema

To enable metadata searching, define the metadata fields when building the chat memory:

```java
RedisChatMemory chatMemory = RedisChatMemory.builder()
    .jedisClient(jedisClient)
    .metadataFields(List.of(
        Map.of("name", "priority", "type", "tag"),
        Map.of("name", "category", "type", "tag"),
        Map.of("name", "score", "type", "numeric")
    ))
    .build();
```

### Configuration Options

The `RedisChatMemory` can be configured with the following options:

- `jedisClient` - The Redis client to use
- `indexName` - The name of the Redis search index (default: "chat-memory-idx")
- `keyPrefix` - The prefix for Redis keys (default: "chat-memory:")
- `timeToLive` - The duration after which messages expire
- `initializeSchema` - Whether to initialize the Redis schema (default: true)
- `maxConversationIds` - Maximum number of conversation IDs to return
- `maxMessagesPerConversation` - Maximum number of messages to return per conversation
- `metadataFields` - List of metadata field definitions for searching (name, type)

## Implementation Details

The implementation uses:

- Redis JSON for storing message content, metadata, and conversation information
- Redis Query Engine for efficient searching and filtering
- Redis key expiration for automatic TTL management
- Redis Aggregation for efficient conversation ID retrieval

## Spring Boot Integration

When using Spring Boot and the Redis Chat Memory starter, the `RedisChatMemory` bean will be automatically configured.
You can customize its behavior using properties in `application.properties` or `application.yml`:

```yaml
spring:
  ai:
    chat:
      memory:
        redis:
          host: localhost
          port: 6379
          index-name: my-chat-index
          key-prefix: my-chats:
          time-to-live: 604800s  # 7 days
          metadata-fields:
            - name: priority
              type: tag
            - name: category
              type: tag
            - name: score
              type: numeric
```
