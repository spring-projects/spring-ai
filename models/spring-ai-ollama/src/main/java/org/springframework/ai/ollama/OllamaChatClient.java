/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.ollama;

import java.util.List;

import reactor.core.publisher.Flux;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaApi.Message.Role;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.util.StringUtils;

/**
 * {@link ChatClient} implementation for {@literal Ollama}.
 *
 * Ollama allows developers to run large language models and generate embeddings locally.
 * It supports open-source models available on [Ollama AI
 * Library](https://ollama.ai/library). - Llama 2 (7B parameters, 3.8GB size) - Mistral
 * (7B parameters, 4.1GB size)
 *
 * Please refer to the <a href="https://ollama.ai/">official Ollama website</a> for the
 * most up-to-date information on available models.
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
public class OllamaChatClient implements ChatClient, StreamingChatClient {

	/**
	 * Low-level Ollama API library.
	 */
	private final OllamaApi chatApi;

	/**
	 * Default options to be used for all chat requests.
	 */
	private OllamaOptions defaultOptions = OllamaOptions.create().withModel(OllamaOptions.DEFAULT_MODEL);

	public OllamaChatClient(OllamaApi chatApi) {
		this.chatApi = chatApi;
	}

	/**
	 * @deprecated Use {@link OllamaOptions#setModel} instead.
	 */
	@Deprecated
	public OllamaChatClient withModel(String model) {
		this.defaultOptions.setModel(model);
		return this;
	}

	public OllamaChatClient withDefaultOptions(OllamaOptions options) {
		this.defaultOptions = options;
		return this;
	}

	@Override
	public ChatResponse call(Prompt prompt) {

		OllamaApi.ChatResponse response = this.chatApi.chat(ollamaChatRequest(prompt, false));
		var generator = new Generation(response.message().content());
		if (response.promptEvalCount() != null && response.evalCount() != null) {
			generator = generator
				.withGenerationMetadata(ChatGenerationMetadata.from("unknown", extractUsage(response)));
		}
		return new ChatResponse(List.of(generator));
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {

		Flux<OllamaApi.ChatResponse> response = this.chatApi.streamingChat(ollamaChatRequest(prompt, true));

		return response.map(chunk -> {
			Generation generation = (chunk.message() != null) ? new Generation(chunk.message().content())
					: new Generation("");
			if (Boolean.TRUE.equals(chunk.done())) {
				generation = generation
					.withGenerationMetadata(ChatGenerationMetadata.from("unknown", extractUsage(chunk)));
			}
			return new ChatResponse(List.of(generation));
		});
	}

	private Usage extractUsage(OllamaApi.ChatResponse response) {
		return new Usage() {

			@Override
			public Long getPromptTokens() {
				return response.promptEvalCount().longValue();
			}

			@Override
			public Long getGenerationTokens() {
				return response.evalCount().longValue();
			}
		};
	}

	/**
	 * Package access for testing.
	 */
	OllamaApi.ChatRequest ollamaChatRequest(Prompt prompt, boolean stream) {

		List<OllamaApi.Message> ollamaMessages = prompt.getInstructions()
			.stream()
			.filter(message -> message.getMessageType() == MessageType.USER
					|| message.getMessageType() == MessageType.ASSISTANT
					|| message.getMessageType() == MessageType.SYSTEM)
			.map(m -> OllamaApi.Message.builder(toRole(m)).withContent(m.getContent()).build())
			.toList();

		// runtime options
		OllamaOptions runtimeOptions = null;
		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ChatOptions runtimeChatOptions) {
				runtimeOptions = ModelOptionsUtils.copyToTarget(runtimeChatOptions, ChatOptions.class,
						OllamaOptions.class);
			}
			else {
				throw new IllegalArgumentException("Prompt options are not of type ChatOptions: "
						+ prompt.getOptions().getClass().getSimpleName());
			}
		}

		OllamaOptions mergedOptions = ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions, OllamaOptions.class);

		// Override the model.
		if (!StringUtils.hasText(mergedOptions.getModel())) {
			throw new IllegalArgumentException("Model is not set!");
		}

		String model = mergedOptions.getModel();
		return OllamaApi.ChatRequest.builder(model)
			.withStream(stream)
			.withMessages(ollamaMessages)
			.withOptions(mergedOptions)
			.build();
	}

	private OllamaApi.Message.Role toRole(Message message) {

		switch (message.getMessageType()) {
			case USER:
				return Role.USER;
			case ASSISTANT:
				return Role.ASSISTANT;
			case SYSTEM:
				return Role.SYSTEM;
			default:
				throw new IllegalArgumentException("Unsupported message type: " + message.getMessageType());
		}
	}

}