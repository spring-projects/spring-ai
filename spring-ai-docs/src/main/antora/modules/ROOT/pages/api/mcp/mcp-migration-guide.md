# MCP Spring Transport Migration Guide

This guide covers the migration of MCP Spring transport modules from the MCP Java SDK to Spring AI, introduced with MCP Java SDK 0.18.0 and Spring AI 2.0.0.

## Overview

The Spring-specific MCP transport implementations (`mcp-spring-webflux` and `mcp-spring-webmvc`) have moved from the MCP Java SDK project into the Spring AI project. This gives the Spring AI team direct ownership of the Spring transport layer while the MCP Java SDK focuses on the core protocol.

## MCP Java SDK Version

| Before | After |
|--------|-------|
| `0.17.1` | `0.18.0` |

## Dependency Coordinate Changes

The `mcp-spring-webflux` and `mcp-spring-webmvc` artifacts changed their Maven `groupId`:

### mcp-spring-webflux

```xml
<!-- Before -->
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp-spring-webflux</artifactId>
</dependency>

<!-- After -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>mcp-spring-webflux</artifactId>
</dependency>
```

### mcp-spring-webmvc

```xml
<!-- Before -->
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp-spring-webmvc</artifactId>
</dependency>

<!-- After -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>mcp-spring-webmvc</artifactId>
</dependency>
```

> **Note:** If you only use the Spring AI Boot Starters (`spring-ai-starter-mcp-*`), these dependencies are managed transitively and no changes are needed in your POM.

## Package Renames

All Spring transport classes retain their original class names but have moved to new packages under `org.springframework.ai.mcp`.

### Client Transports

| Before | After |
|--------|-------|
| `io.modelcontextprotocol.client.transport.WebFluxSseClientTransport` | `org.springframework.ai.mcp.client.webflux.transport.WebFluxSseClientTransport` |
| `io.modelcontextprotocol.client.transport.WebClientStreamableHttpTransport` | `org.springframework.ai.mcp.client.webflux.transport.WebClientStreamableHttpTransport` |

### Server Transports (WebFlux)

| Before | After |
|--------|-------|
| `io.modelcontextprotocol.server.transport.WebFluxSseServerTransportProvider` | `org.springframework.ai.mcp.server.webflux.transport.WebFluxSseServerTransportProvider` |
| `io.modelcontextprotocol.server.transport.WebFluxStatelessServerTransport` | `org.springframework.ai.mcp.server.webflux.transport.WebFluxStatelessServerTransport` |
| `io.modelcontextprotocol.server.transport.WebFluxStreamableServerTransportProvider` | `org.springframework.ai.mcp.server.webflux.transport.WebFluxStreamableServerTransportProvider` |

### Server Transports (WebMvc)

| Before | After |
|--------|-------|
| `io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider` | `org.springframework.ai.mcp.server.webmvc.transport.WebMvcSseServerTransportProvider` |
| `io.modelcontextprotocol.server.transport.WebMvcStatelessServerTransport` | `org.springframework.ai.mcp.server.webmvc.transport.WebMvcStatelessServerTransport` |
| `io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider` | `org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider` |

### Unchanged

The following transports remain in the MCP Java SDK and are **not affected**:

- `io.modelcontextprotocol.client.transport.StdioClientTransport`
- `io.modelcontextprotocol.client.transport.HttpClientSseClientTransport`
- `io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport`
- `io.modelcontextprotocol.server.transport.StdioServerTransportProvider`

## JSON Mapper Changes

The explicit dependency on `JacksonMcpJsonMapper` and `ObjectMapper` has been replaced with the SDK's built-in default abstraction.

### Before

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;

// In transport construction:
var transport = new StdioClientTransport(params, new JacksonMcpJsonMapper(new ObjectMapper()));

// Or in builder pattern:
HttpClientStreamableHttpTransport.builder(url)
    .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
    .build();
```

### After

```java
import io.modelcontextprotocol.json.McpJsonMapper;

// In transport construction:
var transport = new StdioClientTransport(params, McpJsonMapper.getDefault());

// Or in builder pattern:
HttpClientStreamableHttpTransport.builder(url)
    .jsonMapper(McpJsonMapper.getDefault())
    .build();
```

> **Note:** `McpJsonMapper.getDefault()` uses the Jackson-based implementation internally via SPI. You no longer need to inject or construct `ObjectMapper` instances for MCP transport configuration.

## Auto-Configuration Changes

If you rely on Spring AI auto-configuration (the Boot Starters), these changes are handled automatically. The key behavioral changes are:

1. **ObjectMapper no longer injected** — Transport beans no longer accept `ObjectMapper` or `@Qualifier("mcpServerObjectMapper") ObjectMapper` parameters. Custom `ObjectMapper` beans configured for MCP serialization are no longer picked up by the auto-configuration.

2. **keepAliveInterval is conditionally applied** — The `spring.ai.mcp.server.keep-alive-interval` property is now only set on the transport builder when explicitly configured (non-null), rather than always being passed through.

## Migration Checklist

- [ ] Update `mcp-spring-webflux` dependency `groupId` from `io.modelcontextprotocol.sdk` to `org.springframework.ai` (if declared directly)
- [ ] Update `mcp-spring-webmvc` dependency `groupId` from `io.modelcontextprotocol.sdk` to `org.springframework.ai` (if declared directly)
- [ ] Update imports for any Spring transport classes (see package rename tables above)
- [ ] Replace `new JacksonMcpJsonMapper(objectMapper)` with `McpJsonMapper.getDefault()`
- [ ] Remove unused `ObjectMapper` injections that were only used for MCP JSON mapper construction
- [ ] Remove `import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper` and `import com.fasterxml.jackson.databind.ObjectMapper` where no longer needed
- [ ] Verify any custom `@Qualifier("mcpServerObjectMapper")` beans — they are no longer consumed by MCP auto-configuration
