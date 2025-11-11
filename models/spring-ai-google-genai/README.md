[Google GenAI Chat](https://docs.spring.io/spring-ai/reference/api/chat/google-genai-chat.html)

### Starter
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-google-genai</artifactId>
</dependency>
```

### Manual config
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-google-genai</artifactId>
</dependency>
```

### Environment variables
```shell
export GOOGLE_GENAI_USE_VERTEXAI=true
export GOOGLE_CLOUD_PROJECT='your-project-id'
export GOOGLE_CLOUD_LOCATION='your-region'
```

## Extended Usage Metadata

The Google GenAI module provides comprehensive usage metadata tracking through the `GoogleGenAiUsage` class, which extends the standard `Usage` interface with additional token tracking capabilities specific to Google GenAI models.

### Features

#### Thinking Tokens
Track reasoning tokens for thinking-enabled models like Gemini 2.0 Flash Thinking:
```java
ChatResponse response = chatModel.call(prompt);
GoogleGenAiUsage usage = (GoogleGenAiUsage) response.getMetadata().getUsage();
Integer thoughtsTokens = usage.getThoughtsTokenCount(); // Reasoning tokens
```

#### Cached Content Tokens
Monitor tokens from cached context to optimize API costs:
```java
Integer cachedTokens = usage.getCachedContentTokenCount(); // Cached context tokens
```

#### Tool-Use Tokens
Track tokens consumed by function calling and tool use:
```java
Integer toolUseTokens = usage.getToolUsePromptTokenCount(); // Tool-use tokens
```

#### Modality Breakdowns
Get detailed token counts by modality (text, image, audio, video):
```java
List<GoogleGenAiModalityTokenCount> promptDetails = usage.getPromptTokensDetails();
for (GoogleGenAiModalityTokenCount detail : promptDetails) {
    System.out.println(detail.getModality() + ": " + detail.getTokenCount());
}
```

#### Traffic Type
Identify whether requests use Pay-As-You-Go or Provisioned Throughput:
```java
GoogleGenAiTrafficType trafficType = usage.getTrafficType();
// Returns: ON_DEMAND, PROVISIONED_THROUGHPUT, or UNKNOWN
```

### Configuration

Control whether to include extended metadata (enabled by default):
```java
GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
    .model("gemini-2.0-flash")
    .includeExtendedUsageMetadata(true) // Enable extended metadata
    .build();
```

### Complete Example

```java
@Component
public class ExtendedUsageExample {

    private final GoogleGenAiChatModel chatModel;

    public void demonstrateExtendedUsage() {
        Prompt prompt = new Prompt("Analyze this complex multi-modal request");
        ChatResponse response = chatModel.call(prompt);

        // Cast to GoogleGenAiUsage for extended metadata
        GoogleGenAiUsage usage = (GoogleGenAiUsage) response.getMetadata().getUsage();

        // Basic token counts (standard Usage interface)
        System.out.println("Prompt tokens: " + usage.getPromptTokens());
        System.out.println("Completion tokens: " + usage.getCompletionTokens());
        System.out.println("Total tokens: " + usage.getTotalTokens());

        // Extended metadata (Google GenAI specific)
        System.out.println("Thinking tokens: " + usage.getThoughtsTokenCount());
        System.out.println("Cached tokens: " + usage.getCachedContentTokenCount());
        System.out.println("Tool-use tokens: " + usage.getToolUsePromptTokenCount());

        // Modality breakdowns
        if (usage.getPromptTokensDetails() != null) {
            usage.getPromptTokensDetails().forEach(detail ->
                System.out.println("  " + detail.getModality() + ": " + detail.getTokenCount())
            );
        }

        // Traffic type
        System.out.println("Traffic type: " + usage.getTrafficType());

        // Access native SDK object for any additional metadata
        GenerateContentResponseUsageMetadata nativeUsage =
            (GenerateContentResponseUsageMetadata) usage.getNativeUsage();
    }
}
```

### Backward Compatibility

The extended usage metadata maintains full backward compatibility with the standard `Usage` interface. Code using the basic interface continues to work without modification:

```java
// Works with any Spring AI model
Usage usage = response.getMetadata().getUsage();
Long promptTokens = usage.getPromptTokens();
Long completionTokens = usage.getCompletionTokens();
```