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

package org.springframework.ai.bedrock.llama2;

import java.util.List;

import reactor.core.publisher.Flux;

import org.springframework.ai.bedrock.MessageToPromptConverter;
import org.springframework.ai.bedrock.llama2.api.Llama2ChatBedrockApi;
import org.springframework.ai.bedrock.llama2.api.Llama2ChatBedrockApi.Llama2ChatRequest;
import org.springframework.ai.bedrock.llama2.api.Llama2ChatBedrockApi.Llama2ChatResponse;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.util.Assert;

/**
 * Java {@link ChatClient} and {@link StreamingChatClient} for the Bedrock Llama2 chat
 * generative.
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
public class BedrockLlama2ChatClient implements ChatClient, StreamingChatClient {

	private final Llama2ChatBedrockApi chatApi;

	private final BedrockLlama2ChatOptions defaultOptions;

	public BedrockLlama2ChatClient(Llama2ChatBedrockApi chatApi) {
		this(chatApi,
				BedrockLlama2ChatOptions.builder().withTemperature(0.8f).withTopP(0.9f).withMaxGenLen(100).build());
	}

	public BedrockLlama2ChatClient(Llama2ChatBedrockApi chatApi, BedrockLlama2ChatOptions options) {
		Assert.notNull(chatApi, "Llama2ChatBedrockApi must not be null");
		Assert.notNull(options, "BedrockLlama2ChatOptions must not be null");

		this.chatApi = chatApi;
		this.defaultOptions = options;
	}

	@Override
	public ChatResponse call(Prompt prompt) {

		var request = createRequest(prompt);

		Llama2ChatResponse response = this.chatApi.chatCompletion(request);

		return new ChatResponse(List.of(new Generation(response.generation()).withGenerationMetadata(
				ChatGenerationMetadata.from(response.stopReason().name(), extractUsage(response)))));
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {

		var request = createRequest(prompt);

		Flux<Llama2ChatResponse> fluxResponse = this.chatApi.chatCompletionStream(request);

		return fluxResponse.map(response -> {
			String stopReason = response.stopReason() != null ? response.stopReason().name() : null;
			return new ChatResponse(List.of(new Generation(response.generation())
				.withGenerationMetadata(ChatGenerationMetadata.from(stopReason, extractUsage(response)))));
		});
	}

	private Usage extractUsage(Llama2ChatResponse response) {
		return new Usage() {

			@Override
			public Long getPromptTokens() {
				return response.promptTokenCount().longValue();
			}

			@Override
			public Long getGenerationTokens() {
				return response.generationTokenCount().longValue();
			}
		};
	}

	/**
	 * Accessible for testing.
	 */
	Llama2ChatRequest createRequest(Prompt prompt) {

		final String promptValue = MessageToPromptConverter.create().toPrompt(prompt.getInstructions());

		Llama2ChatRequest request = Llama2ChatRequest.builder(promptValue).build();

		if (this.defaultOptions != null) {
			request = ModelOptionsUtils.merge(request, this.defaultOptions, Llama2ChatRequest.class);
		}

		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ChatOptions runtimeOptions) {
				BedrockLlama2ChatOptions updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(runtimeOptions,
						ChatOptions.class, BedrockLlama2ChatOptions.class);

				request = ModelOptionsUtils.merge(updatedRuntimeOptions, request, Llama2ChatRequest.class);
			}
			else {
				throw new IllegalArgumentException("Prompt options are not of type ChatOptions: "
						+ prompt.getOptions().getClass().getSimpleName());
			}
		}

		return request;
	}

}
