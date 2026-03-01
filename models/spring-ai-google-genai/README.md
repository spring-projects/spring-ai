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

## Text-to-Speech (TTS)

The Google GenAI module provides native text-to-speech capabilities through the `GeminiTtsModel` class, supporting both single-speaker and multi-speaker voice synthesis.

### Features

- **30+ Voices**: Wide selection of voices across 24+ languages
- **Single-Speaker TTS**: Simple voice synthesis with style control
- **Multi-Speaker TTS**: Conversational dialogue with multiple voices
- **Prompt-Based Control**: Natural language directives for accent, pace, and style
- **PCM Audio Output**: High-quality 24kHz, mono, s16le PCM audio

### Basic Usage (Single-Speaker)

```java
@Component
public class TextToSpeechExample {

    private final GeminiTtsModel ttsModel;

    public byte[] generateSpeech(String text) {
        // Simple convenience method
        byte[] audioData = ttsModel.call(text);
        return audioData;
    }

    public byte[] generateWithPrompt() {
        // Using TextToSpeechPrompt for more control
        TextToSpeechPrompt prompt = new TextToSpeechPrompt(
            "Say cheerfully: Have a wonderful day!"
        );

        TextToSpeechResponse response = ttsModel.call(prompt);
        return response.getResult().getOutput();
    }
}
```

### Voice Selection

Specify a voice using `GeminiTtsOptions`:

```java
GeminiTtsOptions options = GeminiTtsOptions.builder()
    .model("gemini-2.5-flash-preview-tts")
    .voice("Puck")  // Choose from 30+ available voices
    .speed(1.1)     // Optional: Adjust speech speed
    .build();

TextToSpeechPrompt prompt = new TextToSpeechPrompt(
    "Hello, this is using a specific voice.",
    options
);

byte[] audioData = ttsModel.call(prompt).getResult().getOutput();
```

### Multi-Speaker Conversations

For dialogue with multiple speakers, use speaker voice configurations:

```java
// Define speakers with their voice assignments
GeminiTtsApi.SpeakerVoiceConfig joe = new GeminiTtsApi.SpeakerVoiceConfig("Joe",
    new GeminiTtsApi.VoiceConfig(new GeminiTtsApi.PrebuiltVoiceConfig("Kore")));

GeminiTtsApi.SpeakerVoiceConfig jane = new GeminiTtsApi.SpeakerVoiceConfig("Jane",
    new GeminiTtsApi.VoiceConfig(new GeminiTtsApi.PrebuiltVoiceConfig("Puck")));

// Configure multi-speaker options
GeminiTtsOptions options = GeminiTtsOptions.builder()
    .model("gemini-2.5-flash-preview-tts")
    .speakerVoiceConfigs(List.of(joe, jane))
    .build();

// Provide dialogue text with speaker labels
String conversation = """
    TTS the following conversation between Joe and Jane:
    Joe: How's it going today Jane?
    Jane: Not too bad, how about you?
    Joe: Pretty good, thanks for asking!
    """;

TextToSpeechPrompt prompt = new TextToSpeechPrompt(conversation, options);
byte[] audioData = ttsModel.call(prompt).getResult().getOutput();
```

### Prompt-Based Style Control

Use natural language directives in your text to control delivery:

```java
// Control accent, pace, and style through prompts
String text = """
    Say in a British accent with a slow, dramatic pace:
    To be, or not to be, that is the question.
    """;

byte[] audioData = ttsModel.call(text);
```

### Configuration

Create and configure the TTS model:

```java
@Configuration
public class TtsConfiguration {

    @Bean
    public GeminiTtsModel geminiTtsModel(
            @Value("${spring.ai.google-genai.api-key}") String apiKey) {

        GeminiTtsApi api = new GeminiTtsApi(apiKey);

        GeminiTtsOptions defaultOptions = GeminiTtsOptions.builder()
            .model("gemini-2.5-flash-preview-tts")
            .voice("Kore")
            .build();

        return new GeminiTtsModel(api, defaultOptions);
    }
}
```

### Available Voices

The Gemini TTS API provides 30+ voices including:
- **Kore**: Neutral, versatile voice
- **Puck**: Warm, friendly voice
- **Charon**: Deep, authoritative voice
- **Zephyr**: Light, energetic voice
- And many more across 24+ languages

### Audio Format

Gemini TTS returns PCM audio with the following specifications:
- **Format**: PCM (s16le)
- **Sample Rate**: 24kHz
- **Channels**: Mono

To convert to other formats (e.g., WAV, MP3), use audio processing libraries like FFmpeg:

```bash
# Convert PCM to WAV using FFmpeg
ffmpeg -f s16le -ar 24000 -ac 1 -i output.pcm output.wav
```

### Additional Resources

For more information about Gemini TTS, including rate limits and API options, see:
- [Gemini Speech Generation Documentation](https://ai.google.dev/gemini-api/docs/speech-generation)