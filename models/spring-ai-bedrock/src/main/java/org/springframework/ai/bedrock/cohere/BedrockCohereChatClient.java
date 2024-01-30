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

package org.springframework.ai.bedrock.cohere;

import java.util.List;

import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import reactor.core.publisher.Flux;

import org.springframework.ai.bedrock.BedrockUsage;
import org.springframework.ai.bedrock.MessageToPromptConverter;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi.CohereChatRequest;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi.CohereChatResponse;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi.CohereChatRequest.LogitBias;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi.CohereChatRequest.ReturnLikelihoods;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi.CohereChatRequest.Truncate;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * @author Christian Tzolov
 * @since 0.8.0
 */
public class BedrockCohereChatClient implements ChatClient, StreamingChatClient {

	private final CohereChatBedrockApi chatApi;

	private Float temperature;

	private Float topP;

	private Integer topK;

	private Integer maxTokens;

	private List<String> stopSequences;

	private ReturnLikelihoods returnLikelihoods;

	private Integer numGenerations;

	private LogitBias logitBias;

	private Truncate truncate;

	public BedrockCohereChatClient(CohereChatBedrockApi chatApi) {
		this.chatApi = chatApi;
	}

	public BedrockCohereChatClient withTemperature(Float temperature) {
		this.temperature = temperature;
		return this;
	}

	public BedrockCohereChatClient withTopP(Float topP) {
		this.topP = topP;
		return this;
	}

	public BedrockCohereChatClient withTopK(Integer topK) {
		this.topK = topK;
		return this;
	}

	public BedrockCohereChatClient withMaxTokens(Integer maxTokens) {
		this.maxTokens = maxTokens;
		return this;
	}

	public BedrockCohereChatClient withStopSequences(List<String> stopSequences) {
		this.stopSequences = stopSequences;
		return this;
	}

	public BedrockCohereChatClient withReturnLikelihoods(ReturnLikelihoods returnLikelihoods) {
		this.returnLikelihoods = returnLikelihoods;
		return this;
	}

	public BedrockCohereChatClient withNumGenerations(Integer numGenerations) {
		this.numGenerations = numGenerations;
		return this;
	}

	public BedrockCohereChatClient withLogitBias(LogitBias logitBias) {
		this.logitBias = logitBias;
		return this;
	}

	public BedrockCohereChatClient withTruncate(Truncate truncate) {
		this.truncate = truncate;
		return this;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		CohereChatResponse response = this.chatApi.chatCompletion(this.createRequest(prompt, false));
		List<Generation> generations = response.generations().stream().map(g -> {
			return new Generation(g.text());
		}).toList();

		return new ChatResponse(generations);
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		return this.chatApi.chatCompletionStream(this.createRequest(prompt, true)).map(g -> {
			if (g.isFinished()) {
				String finishReason = g.finishReason().name();
				Usage usage = BedrockUsage.from(g.amazonBedrockInvocationMetrics());
				return new ChatResponse(List
					.of(new Generation("").withGenerationMetadata(ChatGenerationMetadata.from(finishReason, usage))));
			}
			return new ChatResponse(List.of(new Generation(g.text())));
		});
	}

	private CohereChatRequest createRequest(Prompt prompt, boolean stream) {
		final String promptValue = MessageToPromptConverter.create().toPrompt(prompt.getInstructions());

		return CohereChatRequest.builder(promptValue)
			.withTemperature(this.temperature)
			.withTopP(this.topP)
			.withTopK(this.topK)
			.withMaxTokens(this.maxTokens)
			.withStopSequences(this.stopSequences)
			.withReturnLikelihoods(this.returnLikelihoods)
			.withStream(stream)
			.withNumGenerations(this.numGenerations)
			.withLogitBias(this.logitBias)
			.withTruncate(this.truncate)
			.build();
	}

}
