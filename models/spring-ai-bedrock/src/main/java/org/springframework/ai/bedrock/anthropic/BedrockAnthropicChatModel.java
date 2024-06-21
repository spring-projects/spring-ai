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
package org.springframework.ai.bedrock.anthropic;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamOutput;

import org.springframework.ai.bedrock.api.BedrockConverseApi;
import org.springframework.ai.bedrock.api.BedrockConverseApiUtils;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelDescription;
import org.springframework.util.Assert;

/**
 * Java {@link ChatModel} and {@link StreamingChatModel} for the Bedrock Anthropic chat
 * generative model.
 *
 * @author Christian Tzolov
 * @author Wei Jiang
 * @since 0.8.0
 */
public class BedrockAnthropicChatModel implements ChatModel, StreamingChatModel {

	private final String modelId;

	private final BedrockConverseApi converseApi;

	private final AnthropicChatOptions defaultOptions;

	public BedrockAnthropicChatModel(BedrockConverseApi converseApi) {
		this(converseApi, AnthropicChatOptions.builder().withTemperature(0.8f).withTopK(10).build());
	}

	public BedrockAnthropicChatModel(BedrockConverseApi converseApi, AnthropicChatOptions options) {
		this(AnthropicChatModel.CLAUDE_V2.id(), converseApi, options);
	}

	public BedrockAnthropicChatModel(String modelId, BedrockConverseApi converseApi, AnthropicChatOptions options) {
		Assert.notNull(modelId, "modelId must not be null.");
		Assert.notNull(converseApi, "BedrockConverseApi must not be null.");
		Assert.notNull(options, "AnthropicChatOptions must not be null.");

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
		return AnthropicChatOptions.fromOptions(this.defaultOptions);
	}

	/**
	 * Anthropic models version.
	 */
	public enum AnthropicChatModel implements ModelDescription {

		/**
		 * anthropic.claude-instant-v1
		 */
		CLAUDE_INSTANT_V1("anthropic.claude-instant-v1"),
		/**
		 * anthropic.claude-v2
		 */
		CLAUDE_V2("anthropic.claude-v2"),
		/**
		 * anthropic.claude-v2:1
		 */
		CLAUDE_V21("anthropic.claude-v2:1");

		private final String id;

		/**
		 * @return The model id.
		 */
		public String id() {
			return id;
		}

		AnthropicChatModel(String value) {
			this.id = value;
		}

		@Override
		public String getModelName() {
			return this.id;
		}

	}

}
