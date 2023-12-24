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

/**
 *
 * @author Christian Tzolov
 */
import java.util.List;
import java.util.Map;

import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest.ResponseFormat;

public class OpenAiOptionsBuilder {

	private OpenAiOptions options;

	public OpenAiOptionsBuilder() {
		this(new OpenAiOptions());
	}

	public OpenAiOptionsBuilder(OpenAiOptions options) {
		this.options = options;
	}

	public OpenAiOptionsBuilder withMessages(List<ChatCompletionMessage> messages) {
		this.options.messages = messages;
		return this;
	}

	public OpenAiOptionsBuilder withModel(String model) {
		this.options.model = model;
		return this;
	}

	public OpenAiOptionsBuilder withFrequencyPenalty(Float frequencyPenalty) {
		this.options.frequencyPenalty = frequencyPenalty;
		return this;
	}

	public OpenAiOptionsBuilder withLogitBias(Map<String, Object> logitBias) {
		this.options.logitBias = logitBias;
		return this;
	}

	public OpenAiOptionsBuilder withMaxTokens(Integer maxTokens) {
		this.options.maxTokens = maxTokens;
		return this;
	}

	public OpenAiOptionsBuilder withN(Integer n) {
		this.options.n = n;
		return this;
	}

	public OpenAiOptionsBuilder withPresencePenalty(Float presencePenalty) {
		this.options.presencePenalty = presencePenalty;
		return this;
	}

	public OpenAiOptionsBuilder withResponseFormat(ResponseFormat responseFormat) {
		this.options.responseFormat = responseFormat;
		return this;
	}

	public OpenAiOptionsBuilder withSeed(Integer seed) {
		this.options.seed = seed;
		return this;
	}

	public OpenAiOptionsBuilder withStop(String stop) {
		this.options.stop = stop;
		return this;
	}

	public OpenAiOptionsBuilder withStream(Boolean stream) {
		this.options.stream = stream;
		return this;
	}

	public OpenAiOptionsBuilder withTemperature(Float temperature) {
		this.options.temperature = temperature;
		return this;
	}

	public OpenAiOptionsBuilder withTopP(Float topP) {
		this.options.topP = topP;
		return this;
	}

	// Add similar methods for the commented out fields

	public OpenAiOptionsBuilder withUser(String user) {
		this.options.user = user;
		return this;
	}

	public OpenAiOptions build() {
		return this.options;
	}
}
