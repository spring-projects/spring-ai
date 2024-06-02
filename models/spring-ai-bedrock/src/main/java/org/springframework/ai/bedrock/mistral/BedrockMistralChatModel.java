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
package org.springframework.ai.bedrock.mistral;

import java.util.List;

import org.springframework.ai.bedrock.BedrockUsage;
import org.springframework.ai.bedrock.MessageToPromptConverter;
import org.springframework.ai.bedrock.mistral.api.MistralChatBedrockApi;
import org.springframework.ai.bedrock.mistral.api.MistralChatBedrockApi.MistralChatRequest;
import org.springframework.ai.bedrock.mistral.api.MistralChatBedrockApi.MistralChatResponse;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

import reactor.core.publisher.Flux;

/**
 * @author Wei Jiang
 * @since 1.0.0
 */
public class BedrockMistralChatModel implements ChatModel, StreamingChatModel {

	private final MistralChatBedrockApi chatApi;

	private final BedrockMistralChatOptions defaultOptions;

	/**
	 * The retry template used to retry the Bedrock API calls.
	 */
	private final RetryTemplate retryTemplate;

	public BedrockMistralChatModel(MistralChatBedrockApi chatApi) {
		this(chatApi, BedrockMistralChatOptions.builder().build());
	}

	public BedrockMistralChatModel(MistralChatBedrockApi chatApi, BedrockMistralChatOptions options) {
		this(chatApi, options, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	public BedrockMistralChatModel(MistralChatBedrockApi chatApi, BedrockMistralChatOptions options,
			RetryTemplate retryTemplate) {
		Assert.notNull(chatApi, "MistralChatBedrockApi must not be null");
		Assert.notNull(options, "BedrockMistralChatOptions must not be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");

		this.chatApi = chatApi;
		this.defaultOptions = options;
		this.retryTemplate = retryTemplate;
	}

	@Override
	public ChatResponse call(Prompt prompt) {

		MistralChatRequest request = createRequest(prompt);

		return this.retryTemplate.execute(ctx -> {
			MistralChatResponse response = this.chatApi.chatCompletion(request);

			List<Generation> generations = response.outputs().stream().map(g -> {
				return new Generation(g.text());
			}).toList();

			return new ChatResponse(generations);
		});
	}

	public Flux<ChatResponse> stream(Prompt prompt) {

		MistralChatRequest request = createRequest(prompt);

		return this.retryTemplate.execute(ctx -> {
			return this.chatApi.chatCompletionStream(request).map(g -> {
				List<Generation> generations = g.outputs().stream().map(output -> {
					Generation generation = new Generation(output.text());

					if (g.amazonBedrockInvocationMetrics() != null) {
						Usage usage = BedrockUsage.from(g.amazonBedrockInvocationMetrics());
						generation.withGenerationMetadata(ChatGenerationMetadata.from(output.stopReason(), usage));
					}

					return generation;
				}).toList();

				return new ChatResponse(generations);
			});
		});
	}

	/**
	 * Test access.
	 */
	MistralChatRequest createRequest(Prompt prompt) {
		final String promptValue = MessageToPromptConverter.create().toPrompt(prompt.getInstructions());

		var request = MistralChatRequest.builder(promptValue)
			.withTemperature(this.defaultOptions.getTemperature())
			.withTopP(this.defaultOptions.getTopP())
			.withTopK(this.defaultOptions.getTopK())
			.withMaxTokens(this.defaultOptions.getMaxTokens())
			.withStopSequences(this.defaultOptions.getStopSequences())
			.build();

		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ChatOptions runtimeOptions) {
				BedrockMistralChatOptions updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(runtimeOptions,
						ChatOptions.class, BedrockMistralChatOptions.class);
				request = ModelOptionsUtils.merge(updatedRuntimeOptions, request, MistralChatRequest.class);
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
		return defaultOptions;
	}

}
