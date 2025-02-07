# Spring AI MCP Server Boot Starter

The Spring AI MCP (Model Context Protocol) Server Boot Starter provides auto-configuration for setting up an MCP server in Spring Boot applications. It enables seamless integration of MCP server capabilities with Spring Boot's auto-configuration system.

## Overview

The MCP Server Boot Starter offers:
- Automatic configuration of MCP server components
- Support for both synchronous and asynchronous operation modes
- Multiple transport layer options
- Flexible tool, resource, and prompt registration
- Change notification capabilities

## Starter Dependencies

Choose one of the following starters based on your transport requirements:

### 1. Standard MCP Server

Full MCP Server features support with `STDIO` server transport.

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-mcp-server-spring-boot-starter</artifactId>
    <version>${spring-ai.version}</version>
</dependency>
```

### 2. WebMVC Server

Full MCP Server features support with `SSE` (Server-Sent Events) server transport based on Spring MVC and an optional `STDIO` transport.

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-mcp-server-webmvc-spring-boot-starter</artifactId>
    <version>${spring-ai.version}</version>
</dependency>
```
This starter includes:
- spring-boot-starter-web
- mcp-spring-webmvc
(optionally allows `stdio` transport deployment)

### 3. WebFlux Server 

Full MCP Server features support with `SSE` (Server-Sent Events) server transport based on Spring WebFlux and an optional `STDIO` transport.

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-mcp-server-webflux-spring-boot-starter</artifactId>
    <version>${spring-ai.version}</version>
</dependency>
```
This starter includes:
- spring-boot-starter-webflux
- mcp-spring-webflux
(optionally allows `stdio` transport deployment)

## Configuration Properties

All properties are prefixed with `spring.ai.mcp.server`:

| Property | Description | Default |
|----------|-------------|---------|
| `enabled` | Enable/disable the MCP server | `true` |
| `stdio` | Enable/disable stdio transport | `false` |
| `name` | Server name for identification | `mcp-server` |
| `version` | Server version | `1.0.0` |
| `type` | Server type (SYNC/ASYNC) | `SYNC` |
| `resource-change-notification` | Enable resource change notifications | `true` |
| `tool-change-notification` | Enable tool change notifications | `true` |
| `prompt-change-notification` | Enable prompt change notifications | `true` |
| `sse-message-endpoint` | SSE endpoint path for web transport | `/mcp/message` |

## Server Types

### Synchronous Server
- Default server type
- Uses `McpSyncServer`
- Suitable for straightforward request-response patterns
- Configure with `spring.ai.mcp.server.type=SYNC`
- Automatically configures synchronous tool registrations

### Asynchronous Server
- Uses `McpAsyncServer`
- Suitable for non-blocking operations
- Configure with `spring.ai.mcp.server.type=ASYNC`
- Automatically configures asynchronous tool registrations with Project Reactor support

## Transport Options

The MCP Server supports three transport mechanisms, each with its dedicated starter:

### 1. Standard Input/Output (STDIO)
- Use `spring-ai-mcp-server-spring-boot-starter`
- Default transport when using the standard starter
- Suitable for command-line tools and testing
- No additional web dependencies required

### 2. Spring MVC (Server-Sent Events)
- Use `spring-ai-mcp-server-webmvc-spring-boot-starter`
- Provides HTTP-based transport using Spring MVC
- Uses `WebMvcSseServerTransport`
- Automatically configures SSE endpoints
- Ideal for traditional web applications
- Optionally you can deploy `STDIO` transport by setting the `spring.ai.mcp.server.stdio=true` property.

### 3. Spring WebFlux (Reactive SSE)
- Use `spring-ai-mcp-server-webflux-spring-boot-starter`
- Provides reactive transport using Spring WebFlux
- Uses `WebFluxSseServerTransport`
- Automatically configures reactive SSE endpoints
- Ideal for reactive applications with non-blocking requirements
- Optionally you can deploy `STDIO` transport by setting the `spring.ai.mcp.server.stdio=true` property.

## Features and Capabilities

### 1. Tools Registration
- Support for both sync and async tool execution
- Automatic tool registration through Spring beans
- Change notification support
- Tools are automatically converted to sync/async registrations based on server type

### 2. Resource Management
- Static and dynamic resource registration
- Optional change notifications
- Support for resource templates
- Automatic conversion between sync/async resource registrations

### 3. Prompt Templates
- Configurable prompt registration
- Change notification support
- Template versioning
- Automatic conversion between sync/async prompt registrations

### 4. Root Change Consumers
- Support for monitoring root changes
- Automatic conversion to async consumers for reactive applications
- Optional registration through Spring beans

## Usage Examples

### 1. Standard STDIO Server Configuration
```yaml
# Using spring-ai-mcp-server-spring-boot-starter
spring:
  ai:
    mcp:
      server:
        name: stdio-mcp-server
        version: 1.0.0
        type: SYNC
        stdio: true
```

### 2. WebMVC Server Configuration
```yaml
# Using spring-ai-mcp-server-webmvc-spring-boot-starter
spring:
  ai:
    mcp:
      server:
        name: webmvc-mcp-server
        version: 1.0.0
        type: SYNC
        stdio: false
        sse-message-endpoint: /mcp/messages
```

### 3. WebFlux Server Configuration
```yaml
# Using spring-ai-mcp-server-webflux-spring-boot-starter
spring:
  ai:
    mcp:
      server:
        name: webflux-mcp-server
        version: 1.0.0
        type: ASYNC  # Recommended for reactive applications
        stdio: false
        sse-message-endpoint: /mcp/messages
```

### Tool Registration Examples

#### 1. Synchronous Tool (for SYNC server type)
```java
@Configuration
public class SyncToolConfig {
    
    @Bean
    public ToolCallback syncTool() {
        return new ToolCallback() {
            @Override
            public String getName() {
                return "syncTool";
            }
            
            @Override
            public Object execute(Map<String, Object> params) {
                // Synchronous implementation
                return result;
            }
        };
    }
}
```

#### 2. Asynchronous Tool (for ASYNC server type)
```java
@Configuration
public class AsyncToolConfig {
    
    @Bean
    public ToolCallback asyncTool() {
        return new ToolCallback() {
            @Override
            public String getName() {
                return "asyncTool";
            }
            
            @Override
            public Object execute(Map<String, Object> params) {
                // Asynchronous implementation using Project Reactor
                return Mono.just("result")
                    .map(r -> processResult(r))
                    .subscribeOn(Schedulers.boundedElastic());
            }
        };
    }
}
```

## Auto-configuration Classes

The starter provides several auto-configuration classes:

1. `MpcServerAutoConfiguration`: Core server configuration
   - Configures basic server components
   - Handles tool, resource, and prompt registrations
   - Manages server capabilities and change notifications
   - Provides both sync and async server implementations

2. `MpcWebMvcServerAutoConfiguration`: Spring MVC transport
   - Configures SSE endpoints for web transport
   - Integrates with Spring MVC infrastructure

3. `MpcWebFluxServerAutoConfiguration`: Spring WebFlux transport
   - Configures reactive SSE endpoints
   - Integrates with Spring WebFlux infrastructure

These classes are conditionally enabled based on the classpath and configuration properties.

## Conditional Configuration

The auto-configuration is activated when:
- Required MCP classes are on the classpath
- `spring.ai.mcp.server.enabled=true` (default)
- Appropriate transport dependencies are available

## Best Practices

1. Choose the appropriate server type based on your use case:
   - Use SYNC for simple request-response patterns
   - Use ASYNC for non-blocking operations and reactive applications

2. Select the transport mechanism based on your application type:
   - Use STDIO for command-line tools and testing
   - Use WebMvc for traditional web applications
   - Use WebFlux for reactive applications

3. Configure change notifications based on your needs:
   - Enable only the notifications you need
   - Consider performance implications of notifications
   - Use appropriate consumers for root changes

4. Properly version your server and tools:
   - Use semantic versioning
   - Document version changes
   - Handle version compatibility

5. Tool Implementation:
   - Implement tools as Spring beans for automatic registration
   - Return Mono/Flux for async operations in ASYNC mode
   - Use appropriate error handling strategies

## Additional Resources

- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
- [Model Context Protocol Specification](https://modelcontextprotocol.github.io/specification/)
- [Spring Boot Auto-configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.developing-auto-configuration)
