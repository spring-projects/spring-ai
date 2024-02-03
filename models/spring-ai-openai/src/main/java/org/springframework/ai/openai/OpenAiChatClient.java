/*
 * Copyright 2023-2023 the original author or authors.
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
package org.springframework.ai.openai;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatOptions;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletion;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;
import org.springframework.ai.openai.api.OpenAiApi.OpenAiApiException;
import org.springframework.ai.openai.metadata.OpenAiChatResponseMetadata;
import org.springframework.ai.openai.metadata.support.OpenAiResponseHeaderExtractor;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

/**
 * {@link ChatClient} implementation for {@literal OpenAI} backed by {@link OpenAiApi}.
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @author Ueibin Kim
 * @author John Blum
 * @author Josh Long
 * @author Jemin Huh
 * @see ChatClient
 * @see StreamingChatClient
 * @see OpenAiApi
 */
public class OpenAiChatClient implements ChatClient, StreamingChatClient {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private OpenAiChatOptions defaultOptions = OpenAiChatOptions.builder()
		.withModel("gpt-3.5-turbo")
		.withTemperature(0.7f)
		.build();

	public final RetryTemplate retryTemplate = RetryTemplate.builder()
		.maxAttempts(10)
		.retryOn(OpenAiApiException.class)
		.exponentialBackoff(Duration.ofMillis(2000), 5, Duration.ofMillis(3 * 60000))
		.withListener(new RetryListener() {
			public <T extends Object, E extends Throwable> void onError(RetryContext context,
					RetryCallback<T, E> callback, Throwable throwable) {
				logger.warn("Retry error. Retry count:" + context.getRetryCount(), throwable);
			};
		})
		.build();

	private final OpenAiApi openAiApi;

	public OpenAiChatClient(OpenAiApi openAiApi) {
		Assert.notNull(openAiApi, "OpenAiApi must not be null");
		this.openAiApi = openAiApi;
	}

	public OpenAiChatClient withDefaultOptions(OpenAiChatOptions options) {
		this.defaultOptions = options;
		return this;
	}

	@Override
	public ChatResponse call(Prompt prompt) {

		return this.retryTemplate.execute(ctx -> {

			ChatCompletionRequest request = createRequest(prompt, false);

			ResponseEntity<ChatCompletion> completionEntity = this.openAiApi.chatCompletionEntity(request);

			var chatCompletion = completionEntity.getBody();
			if (chatCompletion == null) {
				logger.warn("No chat completion returned for request: {}", prompt);
				return new ChatResponse(List.of());
			}

			RateLimit rateLimits = OpenAiResponseHeaderExtractor.extractAiResponseHeaders(completionEntity);

			List<Generation> generations = chatCompletion.choices().stream().map(choice -> {
				return new Generation(choice.message().content(), Map.of("role", choice.message().role().name()))
					.withGenerationMetadata(ChatGenerationMetadata.from(choice.finishReason().name(), null));
			}).toList();

			return new ChatResponse(generations,
					OpenAiChatResponseMetadata.from(completionEntity.getBody()).withRateLimit(rateLimits));
		});
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		return this.retryTemplate.execute(ctx -> {
			ChatCompletionRequest request = createRequest(prompt, true);

			Flux<OpenAiApi.ChatCompletionChunk> completionChunks = this.openAiApi.chatCompletionStream(request);

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

	/**
	 * Accessible for testing.
	 */
	ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {

		List<ChatCompletionMessage> chatCompletionMessages = prompt.getInstructions()
			.stream()
			.map(m -> new ChatCompletionMessage(m.getContent(),
					ChatCompletionMessage.Role.valueOf(m.getMessageType().name())))
			.toList();

		ChatCompletionRequest request = new ChatCompletionRequest(chatCompletionMessages, stream);

		if (this.defaultOptions != null) {
			request = ModelOptionsUtils.merge(request, this.defaultOptions, ChatCompletionRequest.class);
		}

		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ChatOptions runtimeOptions) {
				OpenAiChatOptions updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(runtimeOptions,
						ChatOptions.class, OpenAiChatOptions.class);
				request = ModelOptionsUtils.merge(updatedRuntimeOptions, request, ChatCompletionRequest.class);
			}
			else {
				throw new IllegalArgumentException("Prompt options are not of type ChatOptions: "
						+ prompt.getOptions().getClass().getSimpleName());
			}
		}

		return request;
	}

}
