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
package org.springframework.ai.bedrock.anthropic3;

import reactor.core.publisher.Flux;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamOutput;

import org.springframework.ai.bedrock.api.BedrockConverseApi;
import org.springframework.ai.bedrock.api.BedrockConverseApiUtils;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelDescription;
import org.springframework.util.Assert;

/**
 * Java {@link ChatModel} and {@link StreamingChatModel} for the Bedrock Anthropic3 chat
 * generative model.
 *
 * @author Ben Middleton
 * @author Christian Tzolov
 * @author Wei Jiang
 * @since 1.0.0
 */
public class BedrockAnthropic3ChatModel implements ChatModel, StreamingChatModel {

	private final String modelId;

	private final BedrockConverseApi converseApi;

	private final Anthropic3ChatOptions defaultOptions;

	public BedrockAnthropic3ChatModel(BedrockConverseApi converseApi) {
		this(converseApi,
				Anthropic3ChatOptions.builder().withTemperature(0.8f).withMaxTokens(500).withTopK(10).build());
	}

	public BedrockAnthropic3ChatModel(BedrockConverseApi converseApi, Anthropic3ChatOptions options) {
		this(Anthropic3ChatModel.CLAUDE_V3_SONNET.id(), converseApi, options);
	}

	public BedrockAnthropic3ChatModel(String modelId, BedrockConverseApi converseApi, Anthropic3ChatOptions options) {
		Assert.notNull(modelId, "modelId must not be null.");
		Assert.notNull(converseApi, "BedrockConverseApi must not be null.");
		Assert.notNull(options, "Anthropic3ChatOptions must not be null.");

		this.modelId = modelId;
		this.converseApi = converseApi;
		this.defaultOptions = options;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		Assert.notNull(prompt, "Prompt must not be null.");

		var request = BedrockConverseApiUtils.createConverseRequest(modelId, prompt, defaultOptions);

		ConverseResponse response = this.converseApi.converse(request);

		return BedrockConverseApiUtils.convertConverseResponse(response);
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		Assert.notNull(prompt, "Prompt must not be null.");

		var request = BedrockConverseApiUtils.createConverseStreamRequest(modelId, prompt, defaultOptions);

		Flux<ConverseStreamOutput> fluxResponse = this.converseApi.converseStream(request);

		return fluxResponse.map(output -> BedrockConverseApiUtils.convertConverseStreamOutput(output));
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return Anthropic3ChatOptions.fromOptions(this.defaultOptions);
	}

	/**
	 * Anthropic3 models version.
	 */
	public enum Anthropic3ChatModel implements ModelDescription {

		/**
		 * anthropic.claude-3-sonnet-20240229-v1:0
		 */
		CLAUDE_V3_SONNET("anthropic.claude-3-sonnet-20240229-v1:0"),
		/**
		 * anthropic.claude-3-haiku-20240307-v1:0
		 */
		CLAUDE_V3_HAIKU("anthropic.claude-3-haiku-20240307-v1:0"),
		/**
		 * anthropic.claude-3-opus-20240229-v1:0
		 */
		CLAUDE_V3_OPUS("anthropic.claude-3-opus-20240229-v1:0");

		private final String id;

		/**
		 * @return The model id.
		 */
		public String id() {
			return id;
		}

		Anthropic3ChatModel(String value) {
			this.id = value;
		}

		@Override
		public String getModelName() {
			return this.id;
		}

	}

}
