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

package org.springframework.ai.bedrock.anthropic;

import java.util.List;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import reactor.core.publisher.Flux;

import org.springframework.ai.bedrock.MessageToPromptConverter;
import org.springframework.ai.bedrock.anthropic.api.AnthropicChatBedrockApi;
import org.springframework.ai.bedrock.anthropic.api.AnthropicChatBedrockApi.AnthropicChatRequest;
import org.springframework.ai.bedrock.anthropic.api.AnthropicChatBedrockApi.AnthropicChatResponse;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Java {@link ChatClient} and {@link StreamingChatClient} for the Bedrock Anthropic chat
 * generative.
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
public class BedrockAnthropicChatClient implements ChatClient, StreamingChatClient {

	private final AnthropicChatBedrockApi anthropicChatApi;

	private Float temperature = 0.8f;

	private Float topP;

	private Integer maxTokensToSample = 500;

	private Integer topK = 10;

	private List<String> stopSequences;

	private String anthropicVersion = AnthropicChatBedrockApi.DEFAULT_ANTHROPIC_VERSION;

	public BedrockAnthropicChatClient(AnthropicChatBedrockApi chatApi) {
		this.anthropicChatApi = chatApi;
	}

	public BedrockAnthropicChatClient withTemperature(Float temperature) {
		this.temperature = temperature;
		return this;
	}

	public BedrockAnthropicChatClient withMaxTokensToSample(Integer maxTokensToSample) {
		this.maxTokensToSample = maxTokensToSample;
		return this;
	}

	public BedrockAnthropicChatClient withTopK(Integer topK) {
		this.topK = topK;
		return this;
	}

	public BedrockAnthropicChatClient withTopP(Float tpoP) {
		this.topP = tpoP;
		return this;
	}

	public BedrockAnthropicChatClient withStopSequences(List<String> stopSequences) {
		this.stopSequences = stopSequences;
		return this;
	}

	public BedrockAnthropicChatClient withAnthropicVersion(String anthropicVersion) {
		this.anthropicVersion = anthropicVersion;
		return this;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		final String promptValue = MessageToPromptConverter.create().toPrompt(prompt.getInstructions());

		AnthropicChatRequest request = AnthropicChatRequest.builder(promptValue)
			.withTemperature(this.temperature)
			.withMaxTokensToSample(this.maxTokensToSample)
			.withTopK(this.topK)
			.withTopP(this.topP)
			.withStopSequences(this.stopSequences)
			.withAnthropicVersion(this.anthropicVersion)
			.build();

		AnthropicChatResponse response = this.anthropicChatApi.chatCompletion(request);

		return new ChatResponse(List.of(new Generation(response.completion())));
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {

		final String promptValue = MessageToPromptConverter.create().toPrompt(prompt.getInstructions());

		AnthropicChatRequest request = AnthropicChatRequest.builder(promptValue)
			.withTemperature(this.temperature)
			.withMaxTokensToSample(this.maxTokensToSample)
			.withTopK(this.topK)
			.withTopP(this.topP)
			.withStopSequences(this.stopSequences)
			.withAnthropicVersion(this.anthropicVersion)
			.build();

		Flux<AnthropicChatResponse> fluxResponse = this.anthropicChatApi.chatCompletionStream(request);

		return fluxResponse.map(response -> {
			String stopReason = response.stopReason() != null ? response.stopReason() : null;
			var generation = new Generation(response.completion());
			if (response.amazonBedrockInvocationMetrics() != null) {
				generation = generation.withGenerationMetadata(
						ChatGenerationMetadata.from(stopReason, response.amazonBedrockInvocationMetrics()));
			}
			return new ChatResponse(List.of(generation));
		});
	}

}
