# Migration Guide: Spring AI Google GenAI Autoconfiguration

## Overview

This guide helps you migrate from the old Vertex AI-based autoconfiguration to the new Google GenAI SDK-based autoconfiguration.

## Starter Dependencies

Spring AI provides separate starters for Google GenAI functionality:

### Chat Functionality
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-google-genai</artifactId>
    <version>1.1.0-SNAPSHOT</version>
</dependency>
```

### Embedding Functionality
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-google-genai-embedding</artifactId>
    <version>1.1.0-SNAPSHOT</version>
</dependency>
```

**Note**: If you need both chat and embedding capabilities, include both starters in your project. The starters are designed to be used independently or together based on your requirements.

## Key Changes

### 1. Property Namespace Changes

Old properties:
```properties
spring.ai.vertex.ai.gemini.project-id=my-project
spring.ai.vertex.ai.gemini.location=us-central1
spring.ai.vertex.ai.gemini.chat.options.model=gemini-pro
spring.ai.vertex.ai.embedding.text.options.model=textembedding-gecko
```

New properties:
```properties
# For Vertex AI mode
spring.ai.google.genai.project-id=my-project
spring.ai.google.genai.location=us-central1
spring.ai.google.genai.chat.options.model=gemini-2.0-flash

# For Gemini Developer API mode (new!)
spring.ai.google.genai.api-key=your-api-key
spring.ai.google.genai.chat.options.model=gemini-2.0-flash

# Embedding properties
spring.ai.google.genai.embedding.project-id=my-project
spring.ai.google.genai.embedding.location=us-central1
spring.ai.google.genai.embedding.text.options.model=text-embedding-004
```

### 2. New Authentication Options

The new SDK supports both:
- **Vertex AI mode**: Using Google Cloud credentials (same as before)
- **Gemini Developer API mode**: Using API keys (new!)

### 3. Removed Features

- `transport` property is no longer needed
- Multimodal embedding autoconfiguration has been removed (pending support in new SDK)

### 4. Bean Name Changes

If you were autowiring beans by name:
- `vertexAi` → `googleGenAiClient`
- `vertexAiGeminiChat` → `googleGenAiChatModel`
- `textEmbedding` → `googleGenAiTextEmbedding`

### 5. Class Changes

If you were importing classes directly:
- `com.google.cloud.vertexai.VertexAI` → `com.google.genai.Client`
- `org.springframework.ai.vertexai.gemini.*` → `org.springframework.ai.google.genai.*`

## Migration Steps

1. Update your application properties:
   - Replace `spring.ai.vertex.ai.*` with `spring.ai.google.genai.*`
   - Remove any `transport` configuration

2. If using API key authentication:
   - Set `spring.ai.google.genai.api-key` property
   - Remove project-id and location for chat (not needed with API key)

3. Update any custom configurations or bean references

4. Test your application thoroughly

## Environment Variables
```bash
export GOOGLE_CLOUD_PROJECT=my-project
export GOOGLE_CLOUD_LOCATION=us-central1
```

New (additional option):
```bash
export GOOGLE_API_KEY=your-api-key
```

## Backward Compatibility

The old autoconfiguration module is still available but deprecated. We recommend migrating to the new module as soon as possible.