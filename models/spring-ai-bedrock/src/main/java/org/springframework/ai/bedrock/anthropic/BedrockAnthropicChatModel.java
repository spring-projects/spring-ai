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

package org.springframework.ai.bedrock.anthropic;

import java.util.List;

import reactor.core.publisher.Flux;

import org.springframework.ai.bedrock.MessageToPromptConverter;
import org.springframework.ai.bedrock.anthropic.api.AnthropicChatBedrockApi;
import org.springframework.ai.bedrock.anthropic.api.AnthropicChatBedrockApi.AnthropicChatRequest;
import org.springframework.ai.bedrock.anthropic.api.AnthropicChatBedrockApi.AnthropicChatResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;

/**
 * Java {@link ChatModel} and {@link StreamingChatModel} for the Bedrock Anthropic chat
 * generative.
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
public class BedrockAnthropicChatModel implements ChatModel, StreamingChatModel {

	private final AnthropicChatBedrockApi anthropicChatApi;

	private final AnthropicChatOptions defaultOptions;

	public BedrockAnthropicChatModel(AnthropicChatBedrockApi chatApi) {
		this(chatApi,
				AnthropicChatOptions.builder()
					.temperature(0.8)
					.maxTokensToSample(500)
					.topK(10)
					.anthropicVersion(AnthropicChatBedrockApi.DEFAULT_ANTHROPIC_VERSION)
					.build());
	}

	public BedrockAnthropicChatModel(AnthropicChatBedrockApi chatApi, AnthropicChatOptions options) {
		this.anthropicChatApi = chatApi;
		this.defaultOptions = options;
	}

	@Override
	public ChatResponse call(Prompt prompt) {

		AnthropicChatRequest request = createRequest(prompt);

		AnthropicChatResponse response = this.anthropicChatApi.chatCompletion(request);

		return new ChatResponse(List.of(new Generation(new AssistantMessage(response.completion()))));
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {

		AnthropicChatRequest request = createRequest(prompt);

		Flux<AnthropicChatResponse> fluxResponse = this.anthropicChatApi.chatCompletionStream(request);

		return fluxResponse.map(response -> {
			String stopReason = response.stopReason() != null ? response.stopReason() : null;
			ChatGenerationMetadata chatGenerationMetadata = null;
			if (response.amazonBedrockInvocationMetrics() != null) {
				chatGenerationMetadata = ChatGenerationMetadata.builder()
					.finishReason(stopReason)
					.metadata("metrics", response.amazonBedrockInvocationMetrics())
					.build();
			}
			return new ChatResponse(
					List.of(new Generation(new AssistantMessage(response.completion()), chatGenerationMetadata)));
		});
	}

	/**
	 * Accessible for testing.
	 */
	AnthropicChatRequest createRequest(Prompt prompt) {

		// Related to: https://github.com/spring-projects/spring-ai/issues/404
		final String promptValue = MessageToPromptConverter.create("\n").toPrompt(prompt.getInstructions());

		AnthropicChatRequest request = AnthropicChatRequest.builder(promptValue).build();

		if (this.defaultOptions != null) {
			request = ModelOptionsUtils.merge(request, this.defaultOptions, AnthropicChatRequest.class);
		}

		if (prompt.getOptions() != null) {
			AnthropicChatOptions updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(),
					ChatOptions.class, AnthropicChatOptions.class);
			request = ModelOptionsUtils.merge(updatedRuntimeOptions, request, AnthropicChatRequest.class);
		}

		return request;
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return AnthropicChatOptions.fromOptions(this.defaultOptions);
	}

}
