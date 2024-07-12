/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.deepseek;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletion;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletion.Choice;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.Role;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionRequest;
import org.springframework.ai.deepseek.metadata.DeepSeekUsage;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Geng Rong
 */
public class DeepSeekChatModel implements ChatModel, StreamingChatModel {

	private static final Logger logger = LoggerFactory.getLogger(DeepSeekChatModel.class);

	/**
	 * The default options used for the chat completion requests.
	 */
	private final DeepSeekChatOptions defaultOptions;

	/**
	 * The retry template used to retry the DeepSeek API calls.
	 */
	public final RetryTemplate retryTemplate;

	/**
	 * Low-level access to the DeepSeek API.
	 */
	private final DeepSeekApi deepSeekApi;

	/**
	 * Creates an instance of the DeepSeekChatModel.
	 * @param deepSeekApi The DeepSeekApi instance to be used for interacting with the
	 * DeepSeek Chat API.
	 * @throws IllegalArgumentException if deepSeekApi is null
	 */
	public DeepSeekChatModel(DeepSeekApi deepSeekApi) {
		this(deepSeekApi,
				DeepSeekChatOptions.builder().withModel(DeepSeekApi.DEFAULT_CHAT_MODEL).withTemperature(1D).build());
	}

	/**
	 * Initializes an instance of the DeepSeekChatModel.
	 * @param deepSeekApi The DeepSeekApi instance to be used for interacting with the
	 * DeepSeek Chat API.
	 * @param options The DeepSeekChatOptions to configure the chat client.
	 */
	public DeepSeekChatModel(DeepSeekApi deepSeekApi, DeepSeekChatOptions options) {
		this(deepSeekApi, options, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Initializes a new instance of the DeepSeekChatModel.
	 * @param deepSeekApi The DeepSeekApi instance to be used for interacting with the
	 * DeepSeek Chat API.
	 * @param options The DeepSeekChatOptions to configure the chat client.
	 * @param retryTemplate The retry template.
	 */
	public DeepSeekChatModel(DeepSeekApi deepSeekApi, DeepSeekChatOptions options, RetryTemplate retryTemplate) {
		Assert.notNull(deepSeekApi, "DeepSeekApi must not be null");
		Assert.notNull(options, "Options must not be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");
		this.deepSeekApi = deepSeekApi;
		this.defaultOptions = options;
		this.retryTemplate = retryTemplate;
	}

	@Override
	public ChatResponse call(Prompt prompt) {

		ChatCompletionRequest request = createRequest(prompt, false);

		return this.retryTemplate.execute(ctx -> {

			ResponseEntity<ChatCompletion> completionEntity = this.doChatCompletion(request);

			var chatCompletion = completionEntity.getBody();
			if (chatCompletion == null) {
				logger.warn("No chat completion returned for prompt: {}", prompt);
				return new ChatResponse(List.of());
			}

			List<Generation> generations = chatCompletion.choices()
				.stream()
				.map(choice -> new Generation(choice.message().content(), toMap(chatCompletion.id(), choice))
					.withGenerationMetadata(ChatGenerationMetadata.from(choice.finishReason().name(), null)))
				.toList();

			return new ChatResponse(generations, from(completionEntity.getBody()));
		});
	}

	private ChatResponseMetadata from(DeepSeekApi.ChatCompletion result) {
		Assert.notNull(result, "DeepSeek ChatCompletionResult must not be null");
		return ChatResponseMetadata.builder()
			.withId(result.id())
			.withUsage(DeepSeekUsage.from(result.usage()))
			.withModel(result.model())
			.withKeyValue("created", result.created())
			.withKeyValue("system-fingerprint", result.systemFingerprint())
			.build();
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return DeepSeekChatOptions.fromOptions(this.defaultOptions);
	}

	private Map<String, Object> toMap(String id, ChatCompletion.Choice choice) {
		Map<String, Object> map = new HashMap<>();

		var message = choice.message();
		if (message.role() != null) {
			map.put("role", message.role().name());
		}
		if (choice.finishReason() != null) {
			map.put("finishReason", choice.finishReason().name());
		}
		map.put("id", id);
		return map;
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {

		ChatCompletionRequest request = createRequest(prompt, true);
		return retryTemplate.execute(ctx -> {
			var completionChunks = this.deepSeekApi.chatCompletionStream(request);
			ConcurrentHashMap<String, String> roleMap = new ConcurrentHashMap<>();

			return completionChunks.map(this::chunkToChatCompletion).map(chatCompletion -> {
				String id = chatCompletion.id();

				List<Generation> generations = chatCompletion.choices().stream().map(choice -> {
					if (choice.message().role() != null) {
						roleMap.putIfAbsent(id, choice.message().role().name());
					}
					String finish = (choice.finishReason() != null ? choice.finishReason().name() : "");
					var generation = new Generation(choice.message().content(),
							Map.of("id", id, "role", roleMap.get(id), "finishReason", finish));
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
	 * Convert the ChatCompletionChunk into a ChatCompletion. The Usage is set to null.
	 * @param chunk the ChatCompletionChunk to convert
	 * @return the ChatCompletion
	 */
	private DeepSeekApi.ChatCompletion chunkToChatCompletion(DeepSeekApi.ChatCompletionChunk chunk) {
		List<Choice> choices = chunk.choices()
			.stream()
			.map(cc -> new Choice(cc.finishReason(), cc.index(), cc.delta(), cc.logprobs()))
			.toList();

		return new DeepSeekApi.ChatCompletion(chunk.id(), choices, chunk.created(), chunk.model(),
				chunk.systemFingerprint(), "chat.completion", null);
	}

	protected ResponseEntity<ChatCompletion> doChatCompletion(ChatCompletionRequest request) {
		return this.deepSeekApi.chatCompletionEntity(request);
	}

	/**
	 * Accessible for testing.
	 */
	ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {
		List<ChatCompletionMessage> chatCompletionMessages = prompt.getInstructions()
			.stream()
			.map(m -> new ChatCompletionMessage(m.getContent(), Role.valueOf(m.getMessageType().name())))
			.toList();

		ChatCompletionRequest request = new ChatCompletionRequest(chatCompletionMessages, stream);

		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ChatOptions) {
				ChatOptions runtimeOptions = (ChatOptions) prompt.getOptions();
				DeepSeekChatOptions updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(runtimeOptions,
						ChatOptions.class, DeepSeekChatOptions.class);

				request = ModelOptionsUtils.merge(updatedRuntimeOptions, request, ChatCompletionRequest.class);
			}
			else {
				throw new IllegalArgumentException("Prompt options are not of type ChatOptions: "
						+ prompt.getOptions().getClass().getSimpleName());
			}
		}

		if (this.defaultOptions != null) {
			request = ModelOptionsUtils.merge(request, this.defaultOptions, ChatCompletionRequest.class);
		}
		return request;
	}

}
