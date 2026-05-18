# Spring AI GoodMem

GoodMem is a memory layer for AI agents with support for semantic storage, retrieval,
and summarization. This package exposes GoodMem operations as Spring AI tools that can
be used with any Spring AI `ChatClient` or agent.

## Tools

| Tool name | Description |
|---|---|
| `goodmem_list_embedders` | List the available embedder models |
| `goodmem_list_spaces` | List all spaces in your account |
| `goodmem_get_space` | Fetch a space by UUID (embedders, chunking config, labels) |
| `goodmem_create_space` | Create a new space or reuse an existing one (dedupe by name) |
| `goodmem_update_space` | Update a space's name, public-read flag, or labels (replace or merge) |
| `goodmem_delete_space` | Permanently delete a space and everything in it |
| `goodmem_create_memory` | Store text or files (PDF, DOCX, image, ...) as memories |
| `goodmem_list_memories` | List memories in a space with optional status filter, sorting, and pagination |
| `goodmem_retrieve_memories` | Semantic similarity search across one or more spaces |
| `goodmem_get_memory` | Fetch a specific memory by ID, optionally with original content |
| `goodmem_delete_memory` | Permanently delete a memory and its embeddings |

## Installation

Add the dependency:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-goodmem</artifactId>
</dependency>
```

If you use the Spring AI BOM, the `spring-ai-goodmem` artifact is managed there, so no
explicit version is required.

## Quick start

```java
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.goodmem.GoodMemClient;
import org.springframework.ai.goodmem.GoodMemTools;

GoodMemClient goodMemClient = GoodMemClient.builder()
    .baseUrl("https://localhost:8080")
    .apiKey(System.getenv("GOODMEM_API_KEY"))
    .verifySsl(false) // self-signed cert in local dev
    .build();

GoodMemTools goodMemTools = new GoodMemTools(goodMemClient);

String reply = ChatClient.builder(chatModel)
    .build()
    .prompt()
    .user("Save this important note for later: <content>")
    .tools(goodMemTools)
    .call()
    .content();
```

You can also wrap the tools as a `ToolCallbackProvider` for use with the lower-level
`ChatModel` API:

```java
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

var provider = MethodToolCallbackProvider.builder()
    .toolObjects(goodMemTools)
    .build();

var options = ToolCallingChatOptions.builder()
    .toolCallbacks(provider.getToolCallbacks())
    .build();
```

## Direct (non-tool) usage

`GoodMemClient` is framework-agnostic and can be called directly when you do not need a
chat model in the loop:

```java
GoodMemClient client = GoodMemClient.builder()
    .baseUrl("https://localhost:8080")
    .apiKey("your-api-key")
    .verifySsl(false)
    .build();

var space = client.createSpace("knowledge-base", embedderId, "recursive", 512, 50);
var memory = client.createMemory(space.get("spaceId").toString(),
        "Spring AI is a framework for AI applications.", null, null);
var matches = client.retrieveMemories("Java AI framework", space.get("spaceId").toString(),
        5, true, true, null, null, null, null, null);
```

## Configuration

| `GoodMemClient.Builder` setting | Default | Description |
|---|---|---|
| `.baseUrl(...)` | required | Base URL of the GoodMem API server |
| `.apiKey(...)` | required | API key sent via the `X-API-Key` header |
| `.timeout(Duration)` | 30 s | Per-request timeout |
| `.verifySsl(boolean)` | `true` | Set to `false` to skip TLS verification (local dev) |

## Error handling

Every `@Tool` method catches `GoodMemClientException` and returns a result map of:

```json
{
  "success": false,
  "error":   "<actionable message preserving the GoodMem API response>",
  "statusCode": 400
}
```

This keeps Spring AI's tool-calling pipeline clean and ensures the chat model receives
a useful, descriptive error instead of a stack trace. When operations succeed, the
result map contains `success: true` plus the operation-specific fields (`spaceId`,
`memoryId`, `results`, `memories`, etc.).

## Building and testing

```bash
# Compile only
./mvnw -pl goodmem/spring-ai-goodmem -am compile -DskipTests

# Run the unit tests
./mvnw -pl goodmem/spring-ai-goodmem test

# Run the live integration tests (requires a running GoodMem server)
GOODMEM_BASE_URL=https://localhost:8080 \
GOODMEM_API_KEY=... \
GOODMEM_TEST_PDF=/path/to/file.pdf \
GOODMEM_VERIFY_SSL=false \
./mvnw -pl goodmem/spring-ai-goodmem test \
    -Dtest=GoodMemIntegrationIT \
    -DfailIfNoTests=false \
    -Dmaven.build.cache.enabled=false
```

The integration tests will be skipped automatically when `GOODMEM_API_KEY` is not set.

## Compatibility

| Spring AI requirement | Notes |
|---|---|
| Java 17 source/target | Built on JDK 21 (matches the rest of the spring-ai project) |
| `spring-ai-model` | Tool-callback abstractions (`@Tool`, `ToolCallback`, `MethodToolCallbackProvider`) |
| Jackson 3 (`tools.jackson.core`) | Used internally for request/response (de)serialization |
