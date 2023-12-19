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

package org.springframework.ai.bedrock.titan;

import java.util.List;

import reactor.core.publisher.Flux;

import org.springframework.ai.bedrock.MessageToPromptConverter;
import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi;
import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi.TitanChatRequest;
import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi.TitanChatResponse;
import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi.TitanChatResponseChunk;
import org.springframework.ai.client.AiClient;
import org.springframework.ai.client.AiResponse;
import org.springframework.ai.client.AiStreamClient;
import org.springframework.ai.client.Generation;
import org.springframework.ai.metadata.ChoiceMetadata;
import org.springframework.ai.metadata.Usage;
import org.springframework.ai.prompt.Prompt;

/**
 * @author Christian Tzolov
 * @since 0.8.0
 */
public class BedrockTitanChatClient implements AiClient, AiStreamClient {

	private final TitanChatBedrockApi chatApi;

	private Float temperature;

	private Float topP;

	private Integer maxTokenCount;

	private List<String> stopSequences;

	public BedrockTitanChatClient(TitanChatBedrockApi chatApi) {
		this.chatApi = chatApi;
	}

	public BedrockTitanChatClient withTemperature(Float temperature) {
		this.temperature = temperature;
		return this;
	}

	public BedrockTitanChatClient withTopP(Float topP) {
		this.topP = topP;
		return this;
	}

	public BedrockTitanChatClient withMaxTokenCount(Integer maxTokens) {
		this.maxTokenCount = maxTokens;
		return this;
	}

	public BedrockTitanChatClient withStopSequences(List<String> stopSequences) {
		this.stopSequences = stopSequences;
		return this;
	}

	@Override
	public AiResponse generate(Prompt prompt) {
		TitanChatResponse response = this.chatApi.chatCompletion(this.createRequest(prompt, false));
		List<Generation> generations = response.results().stream().map(result -> {
			return new Generation(result.outputText());
		}).toList();

		return new AiResponse(generations);
	}

	@Override
	public Flux<AiResponse> generateStream(Prompt prompt) {
		return this.chatApi.chatCompletionStream(this.createRequest(prompt, true)).map(chunk -> {

			Generation generation = new Generation(chunk.outputText());

			if (chunk.amazonBedrockInvocationMetrics() != null) {
				String completionReason = chunk.completionReason().name();
				generation = generation
					.withChoiceMetadata(ChoiceMetadata.from(completionReason, chunk.amazonBedrockInvocationMetrics()));
			}
			else if (chunk.inputTextTokenCount() != null && chunk.totalOutputTextTokenCount() != null) {
				String completionReason = chunk.completionReason().name();
				generation = generation.withChoiceMetadata(ChoiceMetadata.from(completionReason, extractUsage(chunk)));

			}
			return new AiResponse(List.of(generation));
		});
	}

	private TitanChatRequest createRequest(Prompt prompt, boolean stream) {
		final String promptValue = MessageToPromptConverter.create().toPrompt(prompt.getMessages());

		return TitanChatRequest.builder(promptValue)
			.withTemperature(this.temperature)
			.withTopP(this.topP)
			.withMaxTokenCount(this.maxTokenCount)
			.withStopSequences(this.stopSequences)
			.build();
	}

	private Usage extractUsage(TitanChatResponseChunk response) {
		return new Usage() {

			@Override
			public Long getPromptTokens() {
				return response.inputTextTokenCount().longValue();
			}

			@Override
			public Long getGenerationTokens() {
				return response.totalOutputTextTokenCount().longValue();
			}
		};
	}

}
