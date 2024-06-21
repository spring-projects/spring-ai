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

import org.springframework.ai.bedrock.api.BedrockConverseApi;
import org.springframework.ai.bedrock.api.BedrockConverseApiUtils;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelDescription;
import org.springframework.util.Assert;

import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;

/**
 * Java {@link ChatModel} for the Bedrock Jurassic2 chat generative model.
 *
 * @author Ahmed Yousri
 * @author Wei Jiang
 * @since 1.0.0
 */
public class BedrockAi21Jurassic2ChatModel implements ChatModel {

	private final String modelId;

	private final BedrockConverseApi converseApi;

	private final BedrockAi21Jurassic2ChatOptions defaultOptions;

	public BedrockAi21Jurassic2ChatModel(BedrockConverseApi converseApi) {
		this(converseApi,
				BedrockAi21Jurassic2ChatOptions.builder()
					.withTemperature(0.8f)
					.withTopP(0.9f)
					.withMaxTokens(100)
					.build());
	}

	public BedrockAi21Jurassic2ChatModel(BedrockConverseApi converseApi, BedrockAi21Jurassic2ChatOptions options) {
		this(Ai21Jurassic2ChatModel.AI21_J2_MID_V1.id(), converseApi, options);
	}

	public BedrockAi21Jurassic2ChatModel(String modelId, BedrockConverseApi converseApi,
			BedrockAi21Jurassic2ChatOptions options) {
		Assert.notNull(modelId, "modelId must not be null.");
		Assert.notNull(converseApi, "BedrockConverseApi must not be null.");
		Assert.notNull(options, "BedrockAi21Jurassic2ChatOptions must not be null.");

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
	public ChatOptions getDefaultOptions() {
		return BedrockAi21Jurassic2ChatOptions.fromOptions(this.defaultOptions);
	}

	/**
	 * Ai21 Jurassic2 models version.
	 */
	public enum Ai21Jurassic2ChatModel implements ModelDescription {

		/**
		 * ai21.j2-mid-v1
		 */
		AI21_J2_MID_V1("ai21.j2-mid-v1"),

		/**
		 * ai21.j2-ultra-v1
		 */
		AI21_J2_ULTRA_V1("ai21.j2-ultra-v1");

		private final String id;

		/**
		 * @return The model id.
		 */
		public String id() {
			return id;
		}

		Ai21Jurassic2ChatModel(String value) {
			this.id = value;
		}

		@Override
		public String getModelName() {
			return this.id;
		}

	}

}
