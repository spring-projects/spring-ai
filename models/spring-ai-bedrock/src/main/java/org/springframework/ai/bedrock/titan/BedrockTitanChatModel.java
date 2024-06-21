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

import reactor.core.publisher.Flux;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamOutput;

import org.springframework.ai.bedrock.api.BedrockConverseApi;
import org.springframework.ai.bedrock.api.BedrockConverseApiUtils;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelDescription;
import org.springframework.util.Assert;

/**
 * Java {@link ChatModel} and {@link StreamingChatModel} for the Bedrock Titan chat
 * generative model.
 *
 * @author Christian Tzolov
 * @author Wei Jiang
 * @since 0.8.0
 */
public class BedrockTitanChatModel implements ChatModel, StreamingChatModel {

	private final String modelId;

	private final BedrockConverseApi converseApi;

	private final BedrockTitanChatOptions defaultOptions;

	public BedrockTitanChatModel(BedrockConverseApi converseApi) {
		this(converseApi, BedrockTitanChatOptions.builder().withTemperature(0.8f).build());
	}

	public BedrockTitanChatModel(BedrockConverseApi converseApi, BedrockTitanChatOptions defaultOptions) {
		this(TitanChatModel.TITAN_TEXT_EXPRESS_V1.id(), converseApi, defaultOptions);
	}

	public BedrockTitanChatModel(String modelId, BedrockConverseApi converseApi,
			BedrockTitanChatOptions defaultOptions) {
		Assert.notNull(modelId, "modelId must not be null.");
		Assert.notNull(converseApi, "BedrockConverseApi must not be null.");
		Assert.notNull(defaultOptions, "BedrockTitanChatOptions must not be null");

		this.modelId = modelId;
		this.converseApi = converseApi;
		this.defaultOptions = defaultOptions;
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
		return BedrockTitanChatOptions.fromOptions(this.defaultOptions);
	}

	/**
	 * Titan models version.
	 */
	public enum TitanChatModel implements ModelDescription {

		/**
		 * amazon.titan-text-lite-v1
		 */
		TITAN_TEXT_LITE_V1("amazon.titan-text-lite-v1"),

		/**
		 * amazon.titan-text-express-v1
		 */
		TITAN_TEXT_EXPRESS_V1("amazon.titan-text-express-v1"),

		/**
		 * amazon.titan-text-premier-v1:0
		 */
		TITAN_TEXT_PREMIER_V1("amazon.titan-text-premier-v1:0");

		private final String id;

		/**
		 * @return The model id.
		 */
		public String id() {
			return id;
		}

		TitanChatModel(String value) {
			this.id = value;
		}

		@Override
		public String getModelName() {
			return this.id;
		}

	}

}
