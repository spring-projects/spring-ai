# MCP Spring Transport Migration Guide

## Overview

Starting with **Spring AI 2.0**, the Spring-specific MCP transport implementations
(`mcp-spring-webflux` and `mcp-spring-webmvc`) are **no longer shipped by the MCP Java SDK**.
They have been moved into the Spring AI project itself. This is a breaking change that
requires dependency and import updates in every application that directly references
these transport classes.

---

## Breaking Changes

### 1. Maven Dependency Group ID Change

The `mcp-spring-webflux` and `mcp-spring-webmvc` artifacts have moved from the
`io.modelcontextprotocol.sdk` group to `org.springframework.ai`.

#### Before (MCP Java SDK < 1.0.x & Spring AI < 2.0.x)

```xml
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp-spring-webflux</artifactId>
</dependency>

<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp-spring-webmvc</artifactId>
</dependency>
```

#### After (MCP Java SDK ≥ 1.0.x & Spring AI ≥ 2.0.x)

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>mcp-spring-webflux</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>mcp-spring-webmvc</artifactId>
</dependency>
```

> **Note:** When using the `spring-ai-bom` or the Spring AI starter dependencies
> (`spring-ai-starter-mcp-server-webflux`, `spring-ai-starter-mcp-server-webmvc`,
> `spring-ai-starter-mcp-client-webflux`) **no explicit version** is needed — the BOM
> manages it automatically.

---

### 2. Java Package Relocation

All transport classes have been moved to `org.springframework.ai` packages.

#### Server Transports

| Class | Old package (MCP SDK) | New package (Spring AI) |
|---|---|---|
| `WebFluxSseServerTransportProvider` | `io.modelcontextprotocol.server.transport` | `org.springframework.ai.mcp.server.webflux.transport` |
| `WebFluxStreamableServerTransportProvider` | `io.modelcontextprotocol.server.transport` | `org.springframework.ai.mcp.server.webflux.transport` |
| `WebFluxStatelessServerTransport` | `io.modelcontextprotocol.server.transport` | `org.springframework.ai.mcp.server.webflux.transport` |
| `WebMvcSseServerTransportProvider` | `io.modelcontextprotocol.server.transport` | `org.springframework.ai.mcp.server.webmvc.transport` |
| `WebMvcStreamableServerTransportProvider` | `io.modelcontextprotocol.server.transport` | `org.springframework.ai.mcp.server.webmvc.transport` |
| `WebMvcStatelessServerTransport` | `io.modelcontextprotocol.server.transport` | `org.springframework.ai.mcp.server.webmvc.transport` |

#### Client Transports

| Class | Old package (MCP SDK) | New package (Spring AI) |
|---|---|---|
| `WebFluxSseClientTransport` | `io.modelcontextprotocol.client.transport` | `org.springframework.ai.mcp.client.webflux.transport` |
| `WebClientStreamableHttpTransport` | `io.modelcontextprotocol.client.transport` | `org.springframework.ai.mcp.client.webflux.transport` |

#### Example — Update Imports

```java
// Before
import io.modelcontextprotocol.server.transport.WebFluxSseServerTransportProvider;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import io.modelcontextprotocol.client.transport.WebFluxSseClientTransport;
import io.modelcontextprotocol.client.transport.WebClientStreamableHttpTransport;

// After
import org.springframework.ai.mcp.server.webflux.transport.WebFluxSseServerTransportProvider;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcSseServerTransportProvider;
import org.springframework.ai.mcp.client.webflux.transport.WebFluxSseClientTransport;
import org.springframework.ai.mcp.client.webflux.transport.WebClientStreamableHttpTransport;
```

---

### 3. MCP SDK Version Requirement

Spring AI 2.0 requires **MCP Java SDK 1.0.0** (RC1 or later). The SDK version has
been bumped from `0.18.x` to the `1.0.x` release line. Update your BOM or explicit version accordingly.

---

## Spring Boot Auto-configuration Users (No Manual Changes Needed)

If you rely **exclusively on Spring Boot auto-configuration** via the Spring AI starters,
you do **not** need to change any Java code. The auto-configurations have already been
updated internally to reference the new packages. Only update your `pom.xml`/`build.gradle`
dependency coordinates as described in [section 1](#1-maven-dependency-group-id-change).

---
