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

package org.springframework.ai.huggingface;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.huggingface.api.TextGenerationInferenceApi;
import org.springframework.ai.huggingface.invoker.ApiClient;
import org.springframework.ai.huggingface.model.AllOfGenerateResponseDetails;
import org.springframework.ai.huggingface.model.CompatGenerateRequest;
import org.springframework.ai.huggingface.model.GenerateParameters;
import org.springframework.ai.huggingface.model.GenerateResponse;

/**
 * An implementation of {@link ChatModel} that interfaces with HuggingFace Inference
 * Endpoints for text generation.
 *
 * @author Mark Pollack
 * @author Jihoon Kim
 */
public class HuggingfaceChatModel implements ChatModel {

	/**
	 * Token required for authenticating with the HuggingFace Inference API.
	 */
	private final String apiToken;

	/**
	 * Client for making API calls.
	 */
	private ApiClient apiClient = new ApiClient();

	/**
	 * Mapper for converting between Java objects and JSON.
	 */
	private ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * API for text generation inferences.
	 */
	private TextGenerationInferenceApi textGenApi = new TextGenerationInferenceApi();

	/**
	 * The maximum number of new tokens to be generated. Note: The total token size for
	 * the Mistral7b instruct model should be less than 1500.
	 */
	private int maxNewTokens = 1000;

	/**
	 * Constructs a new HuggingfaceChatModel with the specified API token and base path.
	 * @param apiToken The API token for HuggingFace.
	 * @param basePath The base path for API requests.
	 */
	public HuggingfaceChatModel(final String apiToken, String basePath) {
		this.apiToken = apiToken;
		this.apiClient.setBasePath(basePath);
		this.apiClient.addDefaultHeader("Authorization", "Bearer " + this.apiToken);
		this.textGenApi.setApiClient(this.apiClient);
	}

	/**
	 * Generate text based on the provided prompt.
	 * @param prompt The input prompt based on which text is to be generated.
	 * @return ChatResponse containing the generated text and other related details.
	 */
	@Override
	public ChatResponse call(Prompt prompt) {
		CompatGenerateRequest compatGenerateRequest = new CompatGenerateRequest();
		compatGenerateRequest.setInputs(prompt.getContents());
		GenerateParameters generateParameters = new GenerateParameters();
		// TODO - need to expose API to set parameters per call.
		generateParameters.setMaxNewTokens(this.maxNewTokens);
		compatGenerateRequest.setParameters(generateParameters);
		List<GenerateResponse> generateResponses = this.textGenApi.compatGenerate(compatGenerateRequest);
		List<Generation> generations = new ArrayList<>();
		for (GenerateResponse generateResponse : generateResponses) {
			String generatedText = generateResponse.getGeneratedText();
			AllOfGenerateResponseDetails allOfGenerateResponseDetails = generateResponse.getDetails();
			Map<String, Object> detailsMap = this.objectMapper.convertValue(allOfGenerateResponseDetails,
					new TypeReference<Map<String, Object>>() {

					});
			Generation generation = new Generation(new AssistantMessage(generatedText, detailsMap));
			generations.add(generation);
		}
		return new ChatResponse(generations);
	}

	/**
	 * Gets the maximum number of new tokens to be generated.
	 * @return The maximum number of new tokens.
	 */
	public int getMaxNewTokens() {
		return this.maxNewTokens;
	}

	/**
	 * Sets the maximum number of new tokens to be generated.
	 * @param maxNewTokens The maximum number of new tokens.
	 */
	public void setMaxNewTokens(int maxNewTokens) {
		this.maxNewTokens = maxNewTokens;
	}

}
