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
package org.springframework.ai.bedrock.llama;

import org.springframework.ai.bedrock.api.BedrockConverseApi;
import org.springframework.ai.bedrock.api.BedrockConverseApiUtils;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelDescription;
import org.springframework.util.Assert;

import reactor.core.publisher.Flux;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamOutput;

/**
 * Java {@link ChatModel} and {@link StreamingChatModel} for the Bedrock Llama chat
 * generative model.
 *
 * @author Christian Tzolov
 * @author Wei Jiang
 * @since 0.8.0
 */
public class BedrockLlamaChatModel implements ChatModel, StreamingChatModel {

	private final String modelId;

	private final BedrockConverseApi converseApi;

	private final BedrockLlamaChatOptions defaultOptions;

	public BedrockLlamaChatModel(BedrockConverseApi converseApi) {
		this(converseApi,
				BedrockLlamaChatOptions.builder().withTemperature(0.8f).withTopP(0.9f).withMaxGenLen(100).build());
	}

	public BedrockLlamaChatModel(BedrockConverseApi converseApi, BedrockLlamaChatOptions options) {
		this(LlamaChatModel.LLAMA3_70B_INSTRUCT_V1.id(), converseApi, options);
	}

	public BedrockLlamaChatModel(String modelId, BedrockConverseApi converseApi, BedrockLlamaChatOptions options) {
		Assert.notNull(modelId, "modelId must not be null.");
		Assert.notNull(converseApi, "BedrockConverseApi must not be null.");
		Assert.notNull(options, "BedrockLlamaChatOptions must not be null.");

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
		return BedrockLlamaChatOptions.fromOptions(this.defaultOptions);
	}

	/**
	 * Llama models version.
	 */
	public enum LlamaChatModel implements ModelDescription {

		/**
		 * meta.llama2-13b-chat-v1
		 */
		LLAMA2_13B_CHAT_V1("meta.llama2-13b-chat-v1"),

		/**
		 * meta.llama2-70b-chat-v1
		 */
		LLAMA2_70B_CHAT_V1("meta.llama2-70b-chat-v1"),

		/**
		 * meta.llama3-8b-instruct-v1:0
		 */
		LLAMA3_8B_INSTRUCT_V1("meta.llama3-8b-instruct-v1:0"),

		/**
		 * meta.llama3-70b-instruct-v1:0
		 */
		LLAMA3_70B_INSTRUCT_V1("meta.llama3-70b-instruct-v1:0");

		private final String id;

		/**
		 * @return The model id.
		 */
		public String id() {
			return id;
		}

		LlamaChatModel(String value) {
			this.id = value;
		}

		@Override
		public String getModelName() {
			return this.id;
		}

	}

}
