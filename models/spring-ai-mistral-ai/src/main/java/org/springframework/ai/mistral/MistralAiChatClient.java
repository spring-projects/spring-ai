package org.springframework.ai.mistral;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mistral.api.MistralAiApi;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.ai.mistral.api.MistralAiApi.*;

/**
 * @author Ricken Bazolo
 */
public class MistralAiChatClient implements ChatClient, StreamingChatClient {
    private final Logger log = LoggerFactory.getLogger(getClass());
    /**
     * The default options used for the chat completion requests.
     */
    private MistralAiChatOptions defaultOptions;
    /**
     * Low-level access to the OpenAI API.
     */
    private final MistralAiApi mistralAiApi;
    private final RetryTemplate retryTemplate = RetryTemplate.builder()
            .maxAttempts(10)
            .retryOn(MistralAiApiException.class)
            .exponentialBackoff(Duration.ofMillis(2000), 5, Duration.ofMillis(3 * 60000))
            .withListener(new RetryListener() {
                public <T extends Object, E extends Throwable> void onError(RetryContext context,
                                                                            RetryCallback<T, E> callback, Throwable throwable) {
                    log.warn("Retry error. Retry count:" + context.getRetryCount(), throwable);
                };
            })
            .build();

    public MistralAiChatClient(MistralAiApi mistralAiApi, MistralAiChatOptions options) {
        Assert.notNull(mistralAiApi, "MistralAiApi must not be null");
        Assert.notNull(options, "Options must not be null");
        this.mistralAiApi = mistralAiApi;
        this.defaultOptions = options;
    }

    public MistralAiChatClient(MistralAiApi mistralAiApi) {
        this(mistralAiApi, MistralAiChatOptions.builder()
                .withTemperature(0.7f)
                .withSafePrompt(false)
                .withModel(MistralAiApi.DEFAULT_CHAT_MODEL)
                .build());
    }

    /**
     * Accessible for testing.
     */
    public ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {
        var chatCompletionMessages = prompt.getInstructions()
                .stream()
                .map(m -> new ChatCompletionMessage(m.getContent(),
                        ChatCompletionMessage.Role.valueOf(m.getMessageType().name())))
                .toList();

        var request = new ChatCompletionRequest(chatCompletionMessages, stream);

        if (this.defaultOptions != null) {
            request = ModelOptionsUtils.merge(request, this.defaultOptions, ChatCompletionRequest.class);
        }

        if (prompt.getOptions() != null) {
            if (prompt.getOptions() instanceof ChatOptions runtimeOptions) {
                var updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(runtimeOptions,
                        ChatOptions.class, MistralAiChatOptions.class);
                request = ModelOptionsUtils.merge(updatedRuntimeOptions, request, ChatCompletionRequest.class);
            } else {
                throw new IllegalArgumentException("Prompt options are not of type ChatOptions: "
                        + prompt.getOptions().getClass().getSimpleName());
            }
        }

        return request;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return retryTemplate.execute(ctx -> {
            var request = createRequest(prompt, false);

            var completionEntity = this.mistralAiApi.chatCompletionEntity(request);

            var chatCompletion = completionEntity.getBody();
            if (chatCompletion == null) {
                log.warn("No chat completion returned for prompt: {}", prompt);
                return new ChatResponse(List.of());
            }

            List<Generation> generations = chatCompletion.choices().stream()
                    .map(choice -> new Generation(choice.message().content(), Map.of("role", choice.message().role().name()))
                            .withGenerationMetadata(ChatGenerationMetadata.from(choice.finishReason().name(), null)))
                    .toList();

            return new ChatResponse(generations);
        });
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return retryTemplate.execute(ctx -> {
            var request = createRequest(prompt, true);

            var completionChunks = this.mistralAiApi.chatCompletionStream(request);

            // For chunked responses, only the first chunk contains the choice role.
            // The rest of the chunks with same ID share the same role.
            ConcurrentHashMap<String, String> roleMap = new ConcurrentHashMap<>();

            return completionChunks.map(chunk -> {
                String chunkId = chunk.id();
                List<Generation> generations = chunk.choices().stream().map(choice -> {
                    if (choice.delta().role() != null) {
                        roleMap.putIfAbsent(chunkId, choice.delta().role().name());
                    }
                    var generation = new Generation(choice.delta().content(), Map.of("role", roleMap.get(chunkId)));
                    if (choice.finishReason() != null) {
                        generation = generation
                                .withGenerationMetadata(ChatGenerationMetadata.from(choice.finishReason().name(), null));
                    }
                    return generation;
                }).toList();
                return new ChatResponse(generations);
            });
        });
    }
}
