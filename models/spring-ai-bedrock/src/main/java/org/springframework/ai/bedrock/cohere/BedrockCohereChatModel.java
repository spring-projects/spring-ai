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

package org.springframework.ai.bedrock.cohere;

import java.util.List;

import reactor.core.publisher.Flux;

import org.springframework.ai.bedrock.BedrockUsage;
import org.springframework.ai.bedrock.MessageToPromptConverter;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi.CohereChatRequest;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi.CohereChatResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.util.Assert;

/**
 * A {@link ChatModel} implementation that uses the Cohere Chat API.
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
public class BedrockCohereChatModel implements ChatModel, StreamingChatModel {

	private final CohereChatBedrockApi chatApi;

	private final BedrockCohereChatOptions defaultOptions;

	public BedrockCohereChatModel(CohereChatBedrockApi chatApi) {
		this(chatApi, BedrockCohereChatOptions.builder().build());
	}

	public BedrockCohereChatModel(CohereChatBedrockApi chatApi, BedrockCohereChatOptions options) {
		Assert.notNull(chatApi, "CohereChatBedrockApi must not be null");
		Assert.notNull(options, "BedrockCohereChatOptions must not be null");

		this.chatApi = chatApi;
		this.defaultOptions = options;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		CohereChatResponse response = this.chatApi.chatCompletion(this.createRequest(prompt, false));
		List<Generation> generations = response.generations()
			.stream()
			.map(g -> new Generation(new AssistantMessage(g.text())))
			.toList();

		return new ChatResponse(generations);
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		return this.chatApi.chatCompletionStream(this.createRequest(prompt, true)).map(g -> {
			if (g.isFinished()) {
				String finishReason = g.finishReason().name();
				Usage usage = BedrockUsage.from(g.amazonBedrockInvocationMetrics());
				return new ChatResponse(List.of(new Generation(new AssistantMessage(""),
						ChatGenerationMetadata.builder().finishReason(finishReason).metadata("usage", usage).build())));
			}
			return new ChatResponse(List.of(new Generation(new AssistantMessage(g.text()))));
		});
	}

	/**
	 * Test access.
	 */
	CohereChatRequest createRequest(Prompt prompt, boolean stream) {
		final String promptValue = MessageToPromptConverter.create().toPrompt(prompt.getInstructions());

		var request = CohereChatRequest.builder(promptValue)
			.withTemperature(this.defaultOptions.getTemperature())
			.withTopP(this.defaultOptions.getTopP())
			.withTopK(this.defaultOptions.getTopK())
			.withMaxTokens(this.defaultOptions.getMaxTokens())
			.withStopSequences(this.defaultOptions.getStopSequences())
			.withReturnLikelihoods(this.defaultOptions.getReturnLikelihoods())
			.withStream(stream)
			.withNumGenerations(this.defaultOptions.getNumGenerations())
			.withLogitBias(this.defaultOptions.getLogitBias())
			.withTruncate(this.defaultOptions.getTruncate())
			.build();

		if (prompt.getOptions() != null) {
			BedrockCohereChatOptions updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(),
					ChatOptions.class, BedrockCohereChatOptions.class);
			request = ModelOptionsUtils.merge(updatedRuntimeOptions, request, CohereChatRequest.class);
		}

		return request;
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return BedrockCohereChatOptions.fromOptions(this.defaultOptions);
	}

}
