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
package org.springframework.ai.bedrock.titan;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import reactor.core.publisher.Flux;

import org.springframework.ai.bedrock.MessageToPromptConverter;
import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi;
import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi.TitanChatRequest;
import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi.TitanChatResponse;
import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi.TitanChatResponseChunk;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.util.Assert;

/**
 * @author Christian Tzolov
 * @since 0.8.0
 */
public class BedrockTitanChatClient implements ChatClient, StreamingChatClient {

	private final TitanChatBedrockApi chatApi;

	private final BedrockTitanChatOptions defaultOptions;

	public BedrockTitanChatClient(TitanChatBedrockApi chatApi) {
		this(chatApi, BedrockTitanChatOptions.builder().withTemperature(0.8f).build());
	}

	public BedrockTitanChatClient(TitanChatBedrockApi chatApi, BedrockTitanChatOptions defaultOptions) {
		Assert.notNull(chatApi, "ChatApi must not be null");
		Assert.notNull(defaultOptions, "DefaultOptions must not be null");
		this.chatApi = chatApi;
		this.defaultOptions = defaultOptions;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		TitanChatResponse response = this.chatApi.chatCompletion(this.createRequest(prompt));
		List<Generation> generations = response.results().stream().map(result -> {
			return new Generation(UUID.randomUUID().toString(), 0, true, result.outputText(), Map.of(),
					ChatGenerationMetadata.NULL);
		}).toList();

		return new ChatResponse(generations);
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {

		AtomicReference<String> syntheticId = new AtomicReference<>(UUID.randomUUID().toString());

		return this.chatApi.chatCompletionStream(this.createRequest(prompt)).map(chunk -> {

			var text = chunk.outputText();
			boolean isCompleted = chunk.completionReason() != null;
			String completionReason = (chunk.completionReason() != null) ? chunk.completionReason().name() : "";
			ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.NULL;
			if (chunk.amazonBedrockInvocationMetrics() != null) {
				generationMetadata = ChatGenerationMetadata.from(completionReason,
						chunk.amazonBedrockInvocationMetrics());
			}
			else if (chunk.inputTextTokenCount() != null && chunk.totalOutputTextTokenCount() != null) {
				generationMetadata = ChatGenerationMetadata.from(completionReason, extractUsage(chunk));
			}

			Generation generation = new Generation(syntheticId.get(), 0, isCompleted, text, Map.of(),
					generationMetadata);

			return new ChatResponse(syntheticId.get(), List.of(generation), ChatResponseMetadata.NULL);
		});
	}

	/**
	 * Test access.
	 */
	TitanChatRequest createRequest(Prompt prompt) {
		final String promptValue = MessageToPromptConverter.create().toPrompt(prompt.getInstructions());

		var requestBuilder = TitanChatRequest.builder(promptValue);

		if (this.defaultOptions != null) {
			requestBuilder = update(requestBuilder, this.defaultOptions);
		}

		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ChatOptions runtimeOptions) {
				BedrockTitanChatOptions updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(runtimeOptions,
						ChatOptions.class, BedrockTitanChatOptions.class);

				requestBuilder = update(requestBuilder, updatedRuntimeOptions);
			}
			else {
				throw new IllegalArgumentException("Prompt options are not of type ChatOptions: "
						+ prompt.getOptions().getClass().getSimpleName());
			}
		}

		return requestBuilder.build();
	}

	private TitanChatRequest.Builder update(TitanChatRequest.Builder builder, BedrockTitanChatOptions options) {
		if (options.getTemperature() != null) {
			builder.withTemperature(options.getTemperature());
		}
		if (options.getTopP() != null) {
			builder.withTopP(options.getTopP());
		}
		if (options.getMaxTokenCount() != null) {
			builder.withMaxTokenCount(options.getMaxTokenCount());
		}
		if (options.getStopSequences() != null) {
			builder.withStopSequences(options.getStopSequences());
		}
		return builder;
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
