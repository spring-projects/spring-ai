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
package org.springframework.ai.moonshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.moonshot.api.MoonshotApi;
import org.springframework.ai.moonshot.api.MoonshotApi.ChatCompletion;
import org.springframework.ai.moonshot.api.MoonshotApi.ChatCompletion.Choice;
import org.springframework.ai.moonshot.api.MoonshotApi.ChatCompletionChunk;
import org.springframework.ai.moonshot.api.MoonshotApi.ChatCompletionRequest;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

import reactor.core.publisher.Flux;

/**
 * @author Geng Rong
 */
public class MoonshotChatModel implements ChatModel {

	private static final Logger logger = LoggerFactory.getLogger(MoonshotChatModel.class);

	/**
	 * The default options used for the chat completion requests.
	 */
	private final MoonshotChatOptions defaultOptions;

	/**
	 * Low-level access to the Moonshot API.
	 */
	private final MoonshotApi moonshotApi;

	private final RetryTemplate retryTemplate;

	/**
	 * Initializes a new instance of the MoonshotChatModel.
	 * @param moonshotApi The Moonshot instance to be used for interacting with the
	 * Moonshot Chat API.
	 */
	public MoonshotChatModel(MoonshotApi moonshotApi) {
		this(moonshotApi, MoonshotChatOptions.builder().withModel(MoonshotApi.DEFAULT_CHAT_MODEL).build());
	}

	/**
	 * Initializes a new instance of the MoonshotChatModel.
	 * @param moonshotApi The Moonshot instance to be used for interacting with the
	 * Moonshot Chat API.
	 * @param options The MoonshotChatOptions to configure the chat client.
	 */
	public MoonshotChatModel(MoonshotApi moonshotApi, MoonshotChatOptions options) {
		this(moonshotApi, options, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Initializes a new instance of the MoonshotChatModel.
	 * @param moonshotApi The Moonshot instance to be used for interacting with the
	 * Moonshot Chat API.
	 * @param options The MoonshotChatOptions to configure the chat client.
	 * @param retryTemplate The retry template.
	 */
	public MoonshotChatModel(MoonshotApi moonshotApi, MoonshotChatOptions options, RetryTemplate retryTemplate) {
		Assert.notNull(moonshotApi, "MoonshotApi must not be null");
		Assert.notNull(options, "Options must not be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");
		this.moonshotApi = moonshotApi;
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

			return new ChatResponse(generations);
		});
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return null;
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		var request = createRequest(prompt, true);

		return retryTemplate.execute(ctx -> {
			var completionChunks = this.moonshotApi.chatCompletionStream(request);
			ConcurrentHashMap<String, String> roleMap = new ConcurrentHashMap<>();

			return completionChunks.map(this::toChatCompletion).map(chatCompletion -> {
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

	private ChatCompletion toChatCompletion(ChatCompletionChunk chunk) {
		List<Choice> choices = chunk.choices()
			.stream()
			.map(cc -> new Choice(cc.index(), cc.delta(), cc.finishReason()))
			.toList();

		return new ChatCompletion(chunk.id(), "chat.completion", chunk.created(), chunk.model(), choices, null);
	}

	/**
	 * Accessible for testing.
	 */
	public MoonshotApi.ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {
		var chatCompletionMessages = prompt.getInstructions()
			.stream()
			.map(m -> new MoonshotApi.ChatCompletionMessage(m.getContent(),
					MoonshotApi.ChatCompletionMessage.Role.valueOf(m.getMessageType().name())))
			.toList();

		var request = new MoonshotApi.ChatCompletionRequest(chatCompletionMessages, stream);

		if (this.defaultOptions != null) {
			request = ModelOptionsUtils.merge(request, this.defaultOptions, MoonshotApi.ChatCompletionRequest.class);
		}

		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ChatOptions) {
				var updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
						MoonshotChatOptions.class);
				request = ModelOptionsUtils.merge(updatedRuntimeOptions, request,
						MoonshotApi.ChatCompletionRequest.class);
			}
			else {
				throw new IllegalArgumentException("Prompt options are not of type ChatOptions: "
						+ prompt.getOptions().getClass().getSimpleName());
			}
		}
		return request;
	}

	protected ResponseEntity<ChatCompletion> doChatCompletion(ChatCompletionRequest request) {
		return this.moonshotApi.chatCompletionEntity(request);
	}

}
