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

package org.springframework.ai.openai.api;

import java.util.List;
import java.util.Map;

import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;
import org.springframework.ai.openai.api.OpenAiApi.FunctionTool;
import org.springframework.ai.openai.api.OpenAiApi.ResponseFormat;
import org.springframework.ai.openai.api.OpenAiApi.ToolChoice;

public class ChatCompletionRequestBuilder {

	private List<ChatCompletionMessage> messages;

	private String model;

	private Float frequencyPenalty;

	private Map<String, Integer> logitBias;

	private Integer maxTokens;

	private Integer n;

	private Float presencePenalty;

	private ResponseFormat responseFormat;

	private Integer seed;

	private List<String> stop;

	private Boolean stream = false;

	private Float temperature;

	private Float topP;

	private List<FunctionTool> tools;

	private ToolChoice toolChoice;

	private String user;

	public static ChatCompletionRequestBuilder builder() {
		return new ChatCompletionRequestBuilder();
	}

	public ChatCompletionRequestBuilder withMessages(List<ChatCompletionMessage> messages) {
		this.messages = messages;
		return this;
	}

	public ChatCompletionRequestBuilder withModel(String model) {
		this.model = model;
		return this;
	}

	public ChatCompletionRequestBuilder withFrequencyPenalty(Float frequencyPenalty) {
		this.frequencyPenalty = frequencyPenalty;
		return this;
	}

	public ChatCompletionRequestBuilder withLogitBias(Map<String, Integer> logitBias) {
		this.logitBias = logitBias;
		return this;
	}

	public ChatCompletionRequestBuilder withMaxTokens(Integer maxTokens) {
		this.maxTokens = maxTokens;
		return this;
	}

	public ChatCompletionRequestBuilder withN(Integer n) {
		this.n = n;
		return this;
	}

	public ChatCompletionRequestBuilder withPresencePenalty(Float presencePenalty) {
		this.presencePenalty = presencePenalty;
		return this;
	}

	public ChatCompletionRequestBuilder withResponseFormat(ResponseFormat responseFormat) {
		this.responseFormat = responseFormat;
		return this;
	}

	public ChatCompletionRequestBuilder withSeed(Integer seed) {
		this.seed = seed;
		return this;
	}

	public ChatCompletionRequestBuilder withStop(List<String> stop) {
		this.stop = stop;
		return this;
	}

	public ChatCompletionRequestBuilder withStream(Boolean stream) {
		this.stream = stream;
		return this;
	}

	public ChatCompletionRequestBuilder withTemperature(Float temperature) {
		this.temperature = temperature;
		return this;
	}

	public ChatCompletionRequestBuilder withTopP(Float topP) {
		this.topP = topP;
		return this;
	}

	public ChatCompletionRequestBuilder withTools(List<FunctionTool> tools) {
		this.tools = tools;
		return this;
	}

	public ChatCompletionRequestBuilder withToolChoice(ToolChoice toolChoice) {
		this.toolChoice = toolChoice;
		return this;
	}

	public ChatCompletionRequestBuilder withUser(String user) {
		this.user = user;
		return this;
	}

	public ChatCompletionRequest build() {
		return new ChatCompletionRequest(messages, model, frequencyPenalty, logitBias, maxTokens, n, presencePenalty,
				responseFormat, seed, stop, stream, temperature, topP, tools, toolChoice, user);
	}

}
