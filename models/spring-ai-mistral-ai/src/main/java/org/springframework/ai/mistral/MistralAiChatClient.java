/*
 * Copyright 2024-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.mistral;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

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

/**
 * @author Ricken Bazolo
 * @since 0.8.1
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
		.retryOn(MistralAiApi.MistralAiApiException.class)
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
		this(mistralAiApi,
				MistralAiChatOptions.builder()
					.withTemperature(0.7f)
					.withTopP(1f)
					.withSafePrompt(false)
					.withModel(MistralAiApi.ChatModel.TINY.getValue())
					.build());
	}

	/**
	 * Accessible for testing.
	 */
	public MistralAiApi.ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {
		var chatCompletionMessages = prompt.getInstructions()
			.stream()
			.map(m -> new MistralAiApi.ChatCompletionMessage(m.getContent(),
					MistralAiApi.ChatCompletionMessage.Role.valueOf(m.getMessageType().name())))
			.toList();

		var request = new MistralAiApi.ChatCompletionRequest(chatCompletionMessages, stream);

		if (this.defaultOptions != null) {
			request = ModelOptionsUtils.merge(request, this.defaultOptions, MistralAiApi.ChatCompletionRequest.class);
		}

		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ChatOptions runtimeOptions) {
				var updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(runtimeOptions, ChatOptions.class,
						MistralAiChatOptions.class);
				request = ModelOptionsUtils.merge(updatedRuntimeOptions, request,
						MistralAiApi.ChatCompletionRequest.class);
			}
			else {
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

			List<Generation> generations = chatCompletion.choices()
				.stream()
				.map(choice -> new Generation(choice.message().content(),
						Map.of("role", choice.message().role().name()))
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
