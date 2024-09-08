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

package org.springframework.ai.bedrock.jurassic2;

import org.springframework.ai.bedrock.MessageToPromptConverter;
import org.springframework.ai.bedrock.jurassic2.api.Ai21Jurassic2ChatBedrockApi;
import org.springframework.ai.bedrock.jurassic2.api.Ai21Jurassic2ChatBedrockApi.Ai21Jurassic2ChatRequest;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.util.Assert;

/**
 * Java {@link ChatModel} for the Bedrock Jurassic2 chat generative model.
 *
 * @author Ahmed Yousri
 * @since 1.0.0
 */
public class BedrockAi21Jurassic2ChatModel implements ChatModel {

	private final Ai21Jurassic2ChatBedrockApi chatApi;

	private final BedrockAi21Jurassic2ChatOptions defaultOptions;

	public BedrockAi21Jurassic2ChatModel(Ai21Jurassic2ChatBedrockApi chatApi, BedrockAi21Jurassic2ChatOptions options) {
		Assert.notNull(chatApi, "Ai21Jurassic2ChatBedrockApi must not be null");
		Assert.notNull(options, "BedrockAi21Jurassic2ChatOptions must not be null");

		this.chatApi = chatApi;
		this.defaultOptions = options;
	}

	public BedrockAi21Jurassic2ChatModel(Ai21Jurassic2ChatBedrockApi chatApi) {
		this(chatApi,
				BedrockAi21Jurassic2ChatOptions.builder()
					.withTemperature(0.8)
					.withTopP(0.9)
					.withMaxTokens(100)
					.build());
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		var request = createRequest(prompt);
		var response = this.chatApi.chatCompletion(request);

		return new ChatResponse(response.completions()
			.stream()
			.map(completion -> new Generation(completion.data().text())
				.withGenerationMetadata(ChatGenerationMetadata.from(completion.finishReason().reason(), null)))
			.toList());
	}

	private Ai21Jurassic2ChatRequest createRequest(Prompt prompt) {

		final String promptValue = MessageToPromptConverter.create().toPrompt(prompt.getInstructions());

		Ai21Jurassic2ChatRequest request = Ai21Jurassic2ChatRequest.builder(promptValue).build();

		if (prompt.getOptions() != null) {
			BedrockAi21Jurassic2ChatOptions updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(),
					ChatOptions.class, BedrockAi21Jurassic2ChatOptions.class);
			request = ModelOptionsUtils.merge(updatedRuntimeOptions, request, Ai21Jurassic2ChatRequest.class);
		}

		if (this.defaultOptions != null) {
			request = ModelOptionsUtils.merge(request, this.defaultOptions, Ai21Jurassic2ChatRequest.class);
		}

		return request;
	}

	public static Builder builder(Ai21Jurassic2ChatBedrockApi chatApi) {
		return new Builder(chatApi);
	}

	public static class Builder {

		private final Ai21Jurassic2ChatBedrockApi chatApi;

		private BedrockAi21Jurassic2ChatOptions options;

		public Builder(Ai21Jurassic2ChatBedrockApi chatApi) {
			this.chatApi = chatApi;
		}

		public Builder withOptions(BedrockAi21Jurassic2ChatOptions options) {
			this.options = options;
			return this;
		}

		public BedrockAi21Jurassic2ChatModel build() {
			return new BedrockAi21Jurassic2ChatModel(chatApi,
					options != null ? options : BedrockAi21Jurassic2ChatOptions.builder().build());
		}

	}

	@Override
	public ChatOptions getDefaultOptions() {
		return BedrockAi21Jurassic2ChatOptions.fromOptions(this.defaultOptions);
	}

}
