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

package org.springframework.ai.bedrock.anthropic3;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import reactor.core.publisher.Flux;

import org.springframework.ai.bedrock.anthropic3.api.Anthropic3ChatBedrockApi;
import org.springframework.ai.bedrock.anthropic3.api.Anthropic3ChatBedrockApi.AnthropicChatRequest;
import org.springframework.ai.bedrock.anthropic3.api.Anthropic3ChatBedrockApi.AnthropicChatResponse;
import org.springframework.ai.bedrock.anthropic3.api.Anthropic3ChatBedrockApi.AnthropicChatStreamingResponse.StreamingType;
import org.springframework.ai.bedrock.anthropic3.api.Anthropic3ChatBedrockApi.ChatCompletionMessage;
import org.springframework.ai.bedrock.anthropic3.api.Anthropic3ChatBedrockApi.ChatCompletionMessage.Role;
import org.springframework.ai.bedrock.anthropic3.api.Anthropic3ChatBedrockApi.MediaContent;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.util.CollectionUtils;

/**
 * Java {@link ChatModel} and {@link StreamingChatModel} for the Bedrock Anthropic chat
 * generative.
 *
 * @author Ben Middleton
 * @author Christian Tzolov
 * @author Wei Jiang
 * @author Alexandros Pappas
 * @since 1.0.0
 */
public class BedrockAnthropic3ChatModel implements ChatModel, StreamingChatModel {

	private final Anthropic3ChatBedrockApi anthropicChatApi;

	private final Anthropic3ChatOptions defaultOptions;

	public BedrockAnthropic3ChatModel(Anthropic3ChatBedrockApi chatApi) {
		this(chatApi,
				Anthropic3ChatOptions.builder()
					.temperature(0.8)
					.maxTokens(500)
					.topK(10)
					.anthropicVersion(Anthropic3ChatBedrockApi.DEFAULT_ANTHROPIC_VERSION)
					.build());
	}

	public BedrockAnthropic3ChatModel(Anthropic3ChatBedrockApi chatApi, Anthropic3ChatOptions options) {
		this.anthropicChatApi = chatApi;
		this.defaultOptions = options;
	}

	@Override
	public ChatResponse call(Prompt prompt) {

		AnthropicChatRequest request = createRequest(prompt);

		AnthropicChatResponse response = this.anthropicChatApi.chatCompletion(request);

		List<Generation> generations = response.content()
			.stream()
			.map(content -> new Generation(new AssistantMessage(content.text()),
					ChatGenerationMetadata.builder().finishReason(response.stopReason()).build()))
			.toList();

		ChatResponseMetadata metadata = ChatResponseMetadata.builder()
			.id(response.id())
			.model(response.model())
			.usage(extractUsage(response))
			.build();

		return new ChatResponse(generations, metadata);
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {

		AnthropicChatRequest request = createRequest(prompt);

		Flux<Anthropic3ChatBedrockApi.AnthropicChatStreamingResponse> fluxResponse = this.anthropicChatApi
			.chatCompletionStream(request);

		AtomicReference<Integer> inputTokens = new AtomicReference<>(0);
		return fluxResponse.map(response -> {
			if (response.type() == StreamingType.MESSAGE_START) {
				inputTokens.set(response.message().usage().inputTokens());
			}
			String content = response.type() == StreamingType.CONTENT_BLOCK_DELTA ? response.delta().text() : "";
			ChatGenerationMetadata chatGenerationMetadata = null;
			if (response.type() == StreamingType.MESSAGE_DELTA) {
				chatGenerationMetadata = ChatGenerationMetadata.builder()
					.finishReason(response.delta().stopReason())
					.metadata("usage",
							new Anthropic3ChatBedrockApi.AnthropicUsage(inputTokens.get(),
									response.usage().outputTokens()))
					.build();
			}
			return new ChatResponse(List.of(new Generation(new AssistantMessage(content), chatGenerationMetadata)));
		});
	}

	protected Usage extractUsage(AnthropicChatResponse response) {
		return new DefaultUsage(response.usage().inputTokens().longValue(),
				response.usage().outputTokens().longValue());
	}

	/**
	 * Accessible for testing.
	 */
	AnthropicChatRequest createRequest(Prompt prompt) {

		AnthropicChatRequest request = AnthropicChatRequest.builder(toAnthropicMessages(prompt))
			.system(toAnthropicSystemContext(prompt))
			.build();

		if (this.defaultOptions != null) {
			request = ModelOptionsUtils.merge(request, this.defaultOptions, AnthropicChatRequest.class);
		}

		if (prompt.getOptions() != null) {
			Anthropic3ChatOptions updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(),
					ChatOptions.class, Anthropic3ChatOptions.class);
			request = ModelOptionsUtils.merge(updatedRuntimeOptions, request, AnthropicChatRequest.class);
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
			.map(Message::getText)
			.collect(Collectors.joining(System.lineSeparator()));
	}

	/**
	 * Extracts list of messages from prompt.
	 * @param prompt The prompt.
	 * @return The list of {@link ChatCompletionMessage}.
	 */
	private List<ChatCompletionMessage> toAnthropicMessages(Prompt prompt) {

		return prompt.getInstructions()
			.stream()
			.filter(m -> m.getMessageType() == MessageType.USER || m.getMessageType() == MessageType.ASSISTANT)
			.map(message -> {
				List<MediaContent> contents = new ArrayList<>(List.of(new MediaContent(message.getText())));
				if (message instanceof UserMessage userMessage) {
					if (!CollectionUtils.isEmpty(userMessage.getMedia())) {
						List<MediaContent> mediaContent = userMessage.getMedia()
							.stream()
							.map(media -> new MediaContent(media.getMimeType().toString(),
									this.fromMediaData(media.getData())))
							.toList();
						contents.addAll(mediaContent);
					}
				}
				return new ChatCompletionMessage(contents, Role.valueOf(message.getMessageType().name()));
			})
			.toList();
	}

	private String fromMediaData(Object mediaData) {
		if (mediaData instanceof byte[] bytes) {
			return Base64.getEncoder().encodeToString(bytes);
		}
		else if (mediaData instanceof String text) {
			return text;
		}
		else {
			throw new IllegalArgumentException("Unsupported media data type: " + mediaData.getClass().getSimpleName());
		}
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return Anthropic3ChatOptions.fromOptions(this.defaultOptions);
	}

}
