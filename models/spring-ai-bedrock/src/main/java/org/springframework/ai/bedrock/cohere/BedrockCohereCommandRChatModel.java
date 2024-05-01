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
package org.springframework.ai.bedrock.cohere;

import java.util.List;

import org.springframework.ai.bedrock.BedrockUsage;
import org.springframework.ai.bedrock.MessageToPromptConverter;
import org.springframework.ai.bedrock.cohere.api.CohereCommandRChatBedrockApi;
import org.springframework.ai.bedrock.cohere.api.CohereCommandRChatBedrockApi.CohereCommandRChatRequest;
import org.springframework.ai.bedrock.cohere.api.CohereCommandRChatBedrockApi.CohereCommandRChatResponse;
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
public class BedrockCohereCommandRChatModel implements ChatModel, StreamingChatModel {

	private final CohereCommandRChatBedrockApi chatApi;

	private final BedrockCohereCommandRChatOptions defaultOptions;

	/**
	 * The retry template used to retry the Bedrock API calls.
	 */
	private final RetryTemplate retryTemplate;

	public BedrockCohereCommandRChatModel(CohereCommandRChatBedrockApi chatApi) {
		this(chatApi, BedrockCohereCommandRChatOptions.builder().build());
	}

	public BedrockCohereCommandRChatModel(CohereCommandRChatBedrockApi chatApi,
			BedrockCohereCommandRChatOptions options) {
		this(chatApi, options, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	public BedrockCohereCommandRChatModel(CohereCommandRChatBedrockApi chatApi,
			BedrockCohereCommandRChatOptions options, RetryTemplate retryTemplate) {
		Assert.notNull(chatApi, "CohereCommandRChatBedrockApi must not be null");
		Assert.notNull(options, "BedrockCohereCommandRChatOptions must not be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");

		this.chatApi = chatApi;
		this.defaultOptions = options;
		this.retryTemplate = retryTemplate;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		CohereCommandRChatRequest request = this.createRequest(prompt);

		return this.retryTemplate.execute(ctx -> {
			CohereCommandRChatResponse response = this.chatApi.chatCompletion(request);

			Generation generation = new Generation(response.text());

			return new ChatResponse(List.of(generation));
		});
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		CohereCommandRChatRequest request = this.createRequest(prompt);

		return this.retryTemplate.execute(ctx -> {
			return this.chatApi.chatCompletionStream(request).map(g -> {
				if (g.isFinished()) {
					String finishReason = g.finishReason().name();
					Usage usage = BedrockUsage.from(g.amazonBedrockInvocationMetrics());
					return new ChatResponse(List.of(new Generation("")
						.withGenerationMetadata(ChatGenerationMetadata.from(finishReason, usage))));
				}
				return new ChatResponse(List.of(new Generation(g.text())));
			});
		});
	}

	CohereCommandRChatRequest createRequest(Prompt prompt) {
		final String promptValue = MessageToPromptConverter.create().toPrompt(prompt.getInstructions());

		var request = CohereCommandRChatRequest.builder(promptValue)
			.withSearchQueriesOnly(this.defaultOptions.getSearchQueriesOnly())
			.withPreamble(this.defaultOptions.getPreamble())
			.withMaxTokens(this.defaultOptions.getMaxTokens())
			.withTemperature(this.defaultOptions.getTemperature())
			.withTopP(this.defaultOptions.getTopP())
			.withTopK(this.defaultOptions.getTopK())
			.withPromptTruncation(this.defaultOptions.getPromptTruncation())
			.withFrequencyPenalty(this.defaultOptions.getFrequencyPenalty())
			.withPresencePenalty(this.defaultOptions.getPresencePenalty())
			.withSeed(this.defaultOptions.getSeed())
			.withReturnPrompt(this.defaultOptions.getReturnPrompt())
			.withStopSequences(this.defaultOptions.getStopSequences())
			.withRawPrompting(this.defaultOptions.getRawPrompting())
			.build();

		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ChatOptions runtimeOptions) {
				BedrockCohereCommandRChatOptions updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(runtimeOptions,
						ChatOptions.class, BedrockCohereCommandRChatOptions.class);
				request = ModelOptionsUtils.merge(updatedRuntimeOptions, request, CohereCommandRChatRequest.class);
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
		return BedrockCohereCommandRChatOptions.fromOptions(defaultOptions);
	}

}
