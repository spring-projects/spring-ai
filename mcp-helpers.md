# Spring AI MCP Common Utilities

This document provides reference documentation for the common utilities in Spring AI's Model Context Protocol (MCP) integration.

## Overview

The MCP common utilities provide foundational support for integrating Model Context Protocol with Spring AI applications. These utilities enable seamless communication between Spring AI's tool system and MCP servers, supporting both synchronous and asynchronous operations.

## Core Components

### Tool Callbacks

#### AsyncMcpToolCallback

Adapts MCP tools to Spring AI's tool interface with asynchronous execution support.

```java
McpAsyncClient mcpClient = // obtain MCP client
Tool mcpTool = // obtain MCP tool definition
ToolCallback callback = new AsyncMcpToolCallback(mcpClient, mcpTool);

// Use the tool through Spring AI's interfaces
ToolDefinition definition = callback.getToolDefinition();
String result = callback.call("{\"param\": \"value\"}");
```

#### SyncMcpToolCallback

Similar to AsyncMcpToolCallback but provides synchronous execution semantics.

```java
McpSyncClient mcpClient = // obtain MCP client
Tool mcpTool = // obtain MCP tool definition
ToolCallback callback = new SyncMcpToolCallback(mcpClient, mcpTool);

// Use the tool through Spring AI's interfaces
ToolDefinition definition = callback.getToolDefinition();
String result = callback.call("{\"param\": \"value\"}");
```

### Tool Callback Providers

#### AsyncMcpToolCallbackProvider

Discovers and provides MCP tools asynchronously.

```java
McpAsyncClient mcpClient = // obtain MCP client
ToolCallbackProvider provider = new AsyncMcpToolCallbackProvider(mcpClient);

// Get all available tools
ToolCallback[] tools = provider.getToolCallbacks();
```

The provider also offers a utility method for working with multiple clients:

```java
List<McpAsyncClient> clients = // obtain list of clients
Flux<ToolCallback> callbacks = AsyncMcpToolCallbackProvider.asyncToolCallbacks(clients);
```

#### SyncMcpToolCallbackProvider

Similar to AsyncMcpToolCallbackProvider but works with synchronous clients.

```java
McpSyncClient mcpClient = // obtain MCP client
ToolCallbackProvider provider = new SyncMcpToolCallbackProvider(mcpClient);

// Get all available tools
ToolCallback[] tools = provider.getToolCallbacks();
```

For multiple clients:

```java
List<McpSyncClient> clients = // obtain list of clients
List<ToolCallback> callbacks = SyncMcpToolCallbackProvider.syncToolCallbacks(clients);
```

### Client Customization

#### McpAsyncClientCustomizer

Allows customization of asynchronous MCP client configurations.

```java
@Component
public class CustomMcpAsyncClientCustomizer implements McpAsyncClientCustomizer {
    @Override
    public void customize(String name, McpClient.AsyncSpec spec) {
        // Customize the async client configuration
        spec.requestTimeout(Duration.ofSeconds(30));
    }
}
```

#### McpSyncClientCustomizer

Similar to McpAsyncClientCustomizer but for synchronous clients.

```java
@Component
public class CustomMcpSyncClientCustomizer implements McpSyncClientCustomizer {
    @Override
    public void customize(String name, McpClient.SyncSpec spec) {
        // Customize the sync client configuration
        spec.requestTimeout(Duration.ofSeconds(30));
    }
}
```

### Utility Classes

#### McpToolUtils

Provides helper methods for working with MCP tools in a Spring AI environment.

Converting Spring AI tool callbacks to MCP tool registrations:

```java
// For synchronous tools
List<ToolCallback> toolCallbacks = // obtain tool callbacks
List<SyncToolRegistration> syncRegs = McpToolUtils.toSyncToolRegistration(toolCallbacks);

// For asynchronous tools
List<AsyncToolRegistration> asyncRegs = McpToolUtils.toAsyncToolRegistration(toolCallbacks);
```

Getting tool callbacks from MCP clients:

```java
// From sync clients
List<McpSyncClient> syncClients = // obtain sync clients
List<ToolCallback> syncCallbacks = McpToolUtils.getToolCallbacksFromSyncClients(syncClients);

// From async clients
List<McpAsyncClient> asyncClients = // obtain async clients
List<ToolCallback> asyncCallbacks = McpToolUtils.getToolCallbacksFromAsyncClients(asyncClients);
```

### Native Image Support

#### McpHints

Provides GraalVM native image hints for MCP schema classes.

```java
@Configuration
@ImportRuntimeHints(McpHints.class)
public class MyConfiguration {
    // Configuration code
}
```

This class automatically registers all necessary reflection hints for MCP schema classes when building native images.