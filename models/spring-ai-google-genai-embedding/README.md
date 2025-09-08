# Google GenAI Embeddings Module

[Google GenAI Text Embeddings Documentation](https://docs.spring.io/spring-ai/reference/api/embeddings/google-genai-embeddings-text.html)

## Overview

The Google GenAI Embeddings module provides text embedding generation using Google's embedding models through either the Gemini Developer API or Vertex AI.

## Current Support

Please note that at this time the *spring-ai-google-genai-embedding* module supports **text embeddings only**.

This is due to the fact that the Google GenAI SDK currently supports text embeddings only, with multimodal embeddings support pending.

## Starter Dependency

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-google-genai-embedding</artifactId>
</dependency>
```

## Manual Configuration

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-google-genai-embedding</artifactId>
</dependency>
```

## Authentication Modes

The module supports two authentication modes:
- **Gemini Developer API**: Use an API key for quick prototyping
- **Vertex AI**: Use Google Cloud credentials for production deployments

See the [documentation](https://docs.spring.io/spring-ai/reference/api/embeddings/google-genai-embeddings-text.html) for detailed configuration instructions. 