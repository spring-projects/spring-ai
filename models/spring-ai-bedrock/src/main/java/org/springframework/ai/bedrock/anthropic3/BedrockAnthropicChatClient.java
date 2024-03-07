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
package org.springframework.ai.bedrock.anthropic3;

import org.springframework.ai.bedrock.anthropic3.api.AnthropicChatBedrockApi;
import org.springframework.ai.bedrock.anthropic3.api.AnthropicChatBedrockApi.AnthropicChatRequest;
import org.springframework.ai.bedrock.anthropic3.api.AnthropicChatBedrockApi.AnthropicChatResponse;
import org.springframework.ai.bedrock.anthropic3.api.AnthropicChatBedrockApi.AnthropicChatStreamingResponse.StreamingType;
import org.springframework.ai.bedrock.anthropic3.api.AnthropicChatBedrockApi.AnthropicMessage;
import org.springframework.ai.bedrock.anthropic3.api.AnthropicChatBedrockApi.ChatCompletionMessage;
import org.springframework.ai.bedrock.anthropic3.api.AnthropicChatBedrockApi.ChatCompletionMessage.Role;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Java {@link ChatClient} and {@link StreamingChatClient} for the Bedrock Anthropic chat
 * generative.
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
public class BedrockAnthropicChatClient implements ChatClient, StreamingChatClient {

	private final AnthropicChatBedrockApi anthropicChatApi;

	private final AnthropicChatOptions defaultOptions;

	public BedrockAnthropicChatClient(AnthropicChatBedrockApi chatApi) {
		this(chatApi,
				AnthropicChatOptions.builder()
					.withTemperature(0.8f)
					.withMaxTokens(500)
					.withTopK(10)
					.withAnthropicVersion(AnthropicChatBedrockApi.DEFAULT_ANTHROPIC_VERSION)
					.build());
	}

	public BedrockAnthropicChatClient(AnthropicChatBedrockApi chatApi, AnthropicChatOptions options) {
		this.anthropicChatApi = chatApi;
		this.defaultOptions = options;
	}

	@Override
	public ChatResponse call(Prompt prompt) {

		AnthropicChatRequest request = createRequest(prompt);

		AnthropicChatResponse response = this.anthropicChatApi.chatCompletion(request);

		return new ChatResponse(List.of(new Generation(response.content().get(0).text())));
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {

		AnthropicChatRequest request = createRequest(prompt);

		Flux<AnthropicChatBedrockApi.AnthropicChatStreamingResponse> fluxResponse = this.anthropicChatApi
			.chatCompletionStream(request);

		AtomicReference<Integer> inputTokens = new AtomicReference<>(0);
		return fluxResponse.map(response -> {
			if (response.type() == StreamingType.MESSAGE_START) {
				inputTokens.set(response.message().usage().inputTokens());
			}
			String content = response.type() == StreamingType.CONTENT_BLOCK_DELTA ? response.delta().text() : "";

			var generation = new Generation(content);

			if (response.type() == StreamingType.MESSAGE_DELTA) {
				generation = generation.withGenerationMetadata(ChatGenerationMetadata
					.from(response.delta().stopReason(), new AnthropicChatBedrockApi.AnthropicUsage(inputTokens.get(),
							response.usage().outputTokens())));
			}

			return new ChatResponse(List.of(generation));
		});
	}

	/**
	 * Accessible for testing.
	 */
	AnthropicChatRequest createRequest(Prompt prompt) {

		AnthropicChatRequest request = AnthropicChatRequest.builder(toAnthropicMessages(prompt))
			.withSystem(toAnthropicSystemContext(prompt))
			.build();

		if (this.defaultOptions != null) {
			request = ModelOptionsUtils.merge(request, this.defaultOptions, AnthropicChatRequest.class);
		}

		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ChatOptions runtimeOptions) {
				AnthropicChatOptions updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(runtimeOptions,
						ChatOptions.class, AnthropicChatOptions.class);
				request = ModelOptionsUtils.merge(updatedRuntimeOptions, request, AnthropicChatRequest.class);
			}
			else {
				throw new IllegalArgumentException("Prompt options are not of type ChatOptions: "
						+ prompt.getOptions().getClass().getSimpleName());
			}
		}

		return request;
	}

	/**
	 * Extracts system context from prompt.
	 * @param prompt The prompt.
	 * @return The system context.
	 */
	private String toAnthropicSystemContext(Prompt prompt) {

		return prompt.getInstructions()
			.stream()
			.filter(m -> m.getMessageType() == MessageType.SYSTEM)
			.map(Message::getContent)
			.collect(Collectors.joining("\n"));
	}

	/**
	 * Extracts list of messages from prompt.
	 * @param prompt The prompt.
	 * @return The list of {@link ChatCompletionMesssage}.
	 */
	private List<ChatCompletionMessage> toAnthropicMessages(Prompt prompt) {

		return prompt.getInstructions()
			.stream()
			.filter(m -> m.getMessageType() == MessageType.USER || m.getMessageType() == MessageType.ASSISTANT)
			.map(message -> new ChatCompletionMessage(
					List.of(new AnthropicMessage(AnthropicMessage.Type.TEXT, message.getContent())),
					Role.valueOf(message.getMessageType().getValue().toUpperCase())))
			.toList();
	}

}
