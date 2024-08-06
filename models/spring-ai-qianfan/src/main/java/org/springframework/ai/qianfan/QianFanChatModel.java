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
package org.springframework.ai.qianfan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.qianfan.api.QianFanApi;
import org.springframework.ai.qianfan.api.QianFanApi.ChatCompletion;
import org.springframework.ai.qianfan.api.QianFanApi.ChatCompletionChunk;
import org.springframework.ai.qianfan.api.QianFanApi.ChatCompletionMessage;
import org.springframework.ai.qianfan.api.QianFanApi.ChatCompletionMessage.Role;
import org.springframework.ai.qianfan.api.QianFanApi.ChatCompletionRequest;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * {@link ChatModel} and {@link StreamingChatModel} implementation for {@literal QianFan}
 * backed by {@link QianFanApi}.
 *
 * @author Geng Rong
 * @since 1.0
 * @see ChatModel
 * @see StreamingChatModel
 * @see QianFanApi
 */
public class QianFanChatModel implements ChatModel, StreamingChatModel {

	private static final Logger logger = LoggerFactory.getLogger(QianFanChatModel.class);

	/**
	 * The default options used for the chat completion requests.
	 */
	private final QianFanChatOptions defaultOptions;

	/**
	 * The retry template used to retry the QianFan API calls.
	 */
	public final RetryTemplate retryTemplate;

	/**
	 * Low-level access to the QianFan API.
	 */
	private final QianFanApi qianFanApi;

	/**
	 * Creates an instance of the QianFanChatModel.
	 * @param qianFanApi The QianFanApi instance to be used for interacting with the
	 * QianFan Chat API.
	 * @throws IllegalArgumentException if QianFanApi is null
	 */
	public QianFanChatModel(QianFanApi qianFanApi) {
		this(qianFanApi,
				QianFanChatOptions.builder().withModel(QianFanApi.DEFAULT_CHAT_MODEL).withTemperature(0.7f).build());
	}

	/**
	 * Initializes an instance of the QianFanChatModel.
	 * @param qianFanApi The QianFanApi instance to be used for interacting with the
	 * QianFan Chat API.
	 * @param options The QianFanChatOptions to configure the chat client.
	 */
	public QianFanChatModel(QianFanApi qianFanApi, QianFanChatOptions options) {
		this(qianFanApi, options, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Initializes a new instance of the QianFanChatModel.
	 * @param qianFanApi The QianFanApi instance to be used for interacting with the
	 * QianFan Chat API.
	 * @param options The QianFanChatOptions to configure the chat client.
	 * @param retryTemplate The retry template.
	 */
	public QianFanChatModel(QianFanApi qianFanApi, QianFanChatOptions options, RetryTemplate retryTemplate) {
		Assert.notNull(qianFanApi, "QianFanApi must not be null");
		Assert.notNull(options, "Options must not be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");
		this.qianFanApi = qianFanApi;
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

			// if (chatCompletion.baseResponse() != null &&
			// chatCompletion.baseResponse().statusCode() != 0) {
			// throw new RuntimeException(chatCompletion.baseResponse().message());
			// }

			var generation = new Generation(chatCompletion.result(),
					Map.of("id", chatCompletion.id(), "role", Role.ASSISTANT));
			return new ChatResponse(Collections.singletonList(generation));
		});
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		var request = createRequest(prompt, true);

		return retryTemplate.execute(ctx -> {
			var completionChunks = this.qianFanApi.chatCompletionStream(request);

			return completionChunks.map(this::toChatCompletion).map(chatCompletion -> {
				String id = chatCompletion.id();
				var generation = new Generation(chatCompletion.result(), Map.of("id", id, "role", Role.ASSISTANT));
				return new ChatResponse(Collections.singletonList(generation));
			});
		});
	}

	/**
	 * Convert the ChatCompletionChunk into a ChatCompletion.
	 * @param chunk the ChatCompletionChunk to convert
	 * @return the ChatCompletion
	 */
	private ChatCompletion toChatCompletion(ChatCompletionChunk chunk) {
		return new ChatCompletion(chunk.id(), chunk.object(), chunk.created(), chunk.result(), chunk.usage());
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
		var systemMessageList = chatCompletionMessages.stream().filter(msg -> msg.role() == Role.SYSTEM).toList();
		var userMessageList = chatCompletionMessages.stream().filter(msg -> msg.role() != Role.SYSTEM).toList();

		if (systemMessageList.size() > 1) {
			throw new IllegalArgumentException("Only one system message is allowed in the prompt");
		}

		var systemMessage = systemMessageList.isEmpty() ? null : systemMessageList.get(0).content();

		var request = new ChatCompletionRequest(userMessageList, systemMessage, stream);

		if (this.defaultOptions != null) {
			request = ModelOptionsUtils.merge(this.defaultOptions, request, ChatCompletionRequest.class);
		}

		if (prompt.getOptions() != null) {
			if (prompt.getOptions() != null) {
				var updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
						QianFanChatOptions.class);
				request = ModelOptionsUtils.merge(updatedRuntimeOptions, request, ChatCompletionRequest.class);
			}
			else {
				throw new IllegalArgumentException("Prompt options are not of type ChatOptions: "
						+ prompt.getOptions().getClass().getSimpleName());
			}
		}
		return request;
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return QianFanChatOptions.fromOptions(this.defaultOptions);
	}

	private ResponseEntity<ChatCompletion> doChatCompletion(ChatCompletionRequest request) {
		return this.qianFanApi.chatCompletionEntity(request);
	}

}
