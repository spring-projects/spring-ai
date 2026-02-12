# Model Context Protocol (MCP) Server

The Spring AI MCP module provides integration with the Model Context Protocol, allowing you to expose your AI tools and resources through a standardized protocol. This module is particularly useful when you want to make your Spring AI tools and resources available to MCP-compatible clients.

## Dependencies

To use the MCP server functionality, add the following dependency to your project:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server</artifactId>
    <version>${spring-ai.version}</version>
</dependency>
```

## Configuration Properties

The MCP server can be configured using the following properties under the `spring.ai.mcp.server` prefix:

| Property | Default | Description |
|----------|---------|-------------|
| `enabled` | `false` | Enable/disable the MCP server |
| `name` | `"mcp-server"` | Name of the MCP server |
| `version` | `"1.0.0"` | Version of the MCP server |
| `type` | `SYNC` | Server type (`SYNC` or `ASYNC`) |
| `resource-change-notification` | `true` | Enable/disable resource change notifications |
| `tool-change-notification` | `true` | Enable/disable tool change notifications |
| `prompt-change-notification` | `true` | Enable/disable prompt change notifications |
| `transport` | `STDIO` | Transport type (`STDIO`, `WEBMVC`, or `WEBFLUX`) |
| `sse-message-endpoint` | `"/mcp/message"` | Server-Sent Events (SSE) message endpoint for web transports |

## Server Types

The MCP server supports two operation modes:

### 1. Synchronous Mode (Default)

The synchronous mode is the default option, suitable for most use cases where tools and resources are accessed sequentially:

```yaml
spring:
  ai:
    mcp:
      server:
        type: SYNC
```

### 2. Asynchronous Mode

The asynchronous mode is designed for reactive applications and scenarios requiring non-blocking operations:

```yaml
spring:
  ai:
    mcp:
      server:
        type: ASYNC
```

## Transport Options

The MCP server supports three transport types:

### 1. STDIO Transport (Default)

The Standard Input/Output transport is the default option, suitable for command-line tools and local development:

```yaml
spring:
  ai:
    mcp:
      server:
        transport: STDIO
```

### 2. WebMvc Transport

The WebMvc transport uses Spring MVC's Server-Sent Events (SSE) for communication:

```yaml
spring:
  ai:
    mcp:
      server:
        transport: WEBMVC
        sse-message-endpoint: /mcp/message  # Optional, defaults to /mcp/message
```

Required dependencies:
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>mcp-spring-webmvc</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

### 3. WebFlux Transport

The WebFlux transport uses Spring WebFlux's Server-Sent Events for reactive communication:

```yaml
spring:
  ai:
    mcp:
      server:
        transport: WEBFLUX
        sse-message-endpoint: /mcp/message  # Optional, defaults to /mcp/message
```

Required dependencies:
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>mcp-spring-webflux</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

## Core Features

The MCP server provides several core features:

### Tools

- Extensible tool registration system supporting both sync and async execution
- Automatic tool discovery and registration through Spring's component scanning
- Change notification support for tool updates

### Resources

- Static and dynamic resource management
- Optional change notifications for resource updates
- Support for both sync and async resource access

### Prompts

- Configurable prompt templates
- Change notification support for template updates
- Integration with Spring AI's prompt system

## Usage Example

Here's an example of configuring the MCP server with WebMvc transport and custom settings:

```yaml
spring:
  ai:
    mcp:
      server:
        enabled: true
        name: "My AI Tools Server"
        version: "1.0.0"
        type: SYNC
        transport: WEBMVC
        sse-message-endpoint: /ai/mcp/events
        resource-change-notification: true
        tool-change-notification: true
        prompt-change-notification: false
```

## Auto-configuration

The MCP server auto-configuration is provided through:

1. `McpServerAutoConfiguration`: Core server configuration supporting both sync and async modes
2. `McpServerSseWebMvcAutoConfiguration`: WebMvc transport configuration (activated when WebMvc dependencies are present)
3. `McpServerSseWebFluxAutoConfiguration`: WebFlux transport configuration (activated when WebFlux dependencies are present)

The auto-configuration will automatically set up the appropriate server type and transport based on your configuration and available dependencies.

## Implementing Tools and Resources

To expose your Spring AI tools and resources through the MCP server:

1. Implement the `ToolCallback` interface for your AI tools:
```java
@Component
public class MyAiTool implements ToolCallback {
    // Implementation
}
```

2. The auto-configuration will automatically discover and register your tools with the MCP server, converting them to either sync or async implementations based on your server type configuration.

## Monitoring

The MCP server provides notifications for changes in:
- Tools (when tools are added or removed)
- Resources (when resources are updated)
- Prompts (when prompt templates change)

You can enable/disable these notifications using the configuration properties. The notification system works with both sync and async server types, providing consistent change tracking regardless of the chosen operation mode.
