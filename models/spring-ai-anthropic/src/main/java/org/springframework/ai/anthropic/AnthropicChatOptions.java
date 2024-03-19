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
package org.springframework.ai.anthropic;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionRequest;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * The options to be used when sending a chat request to the Anthropic API.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
@JsonInclude(Include.NON_NULL)
public class AnthropicChatOptions implements ChatOptions {

	// @formatter:off
	private @JsonProperty("model") String model;
	private @JsonProperty("max_tokens") Integer maxTokens;
	private @JsonProperty("metadata") ChatCompletionRequest.Metadata metadata;
	private @JsonProperty("stop_sequences") List<String> stopSequences;
	private @JsonProperty("temperature") Float temperature;
	private @JsonProperty("top_p") Float topP;
	private @JsonProperty("top_k") Integer topK;
	// @formatter:on

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private final AnthropicChatOptions options = new AnthropicChatOptions();

		public Builder withModel(String model) {
			this.options.model = model;
			return this;
		}

		public Builder withMaxTokens(Integer maxTokens) {
			this.options.maxTokens = maxTokens;
			return this;
		}

		public Builder withMetadata(ChatCompletionRequest.Metadata metadata) {
			this.options.metadata = metadata;
			return this;
		}

		public Builder withStopSequences(List<String> stopSequences) {
			this.options.stopSequences = stopSequences;
			return this;
		}

		public Builder withTemperature(Float temperature) {
			this.options.temperature = temperature;
			return this;
		}

		public Builder withTopP(Float topP) {
			this.options.topP = topP;
			return this;
		}

		public Builder withTopK(Integer topK) {
			this.options.topK = topK;
			return this;
		}

		public AnthropicChatOptions build() {
			return this.options;
		}

	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public Integer getMaxTokens() {
		return this.maxTokens;
	}

	public void setMaxTokens(Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	public ChatCompletionRequest.Metadata getMetadata() {
		return this.metadata;
	}

	public void setMetadata(ChatCompletionRequest.Metadata metadata) {
		this.metadata = metadata;
	}

	public List<String> getStopSequences() {
		return this.stopSequences;
	}

	public void setStopSequences(List<String> stopSequences) {
		this.stopSequences = stopSequences;
	}

	@Override
	public Float getTemperature() {
		return this.temperature;
	}

	public void setTemperature(Float temperature) {
		this.temperature = temperature;
	}

	@Override
	public Float getTopP() {
		return this.topP;
	}

	public void setTopP(Float topP) {
		this.topP = topP;
	}

	public Integer getTopK() {
		return this.topK;
	}

	public void setTopK(Integer topK) {
		this.topK = topK;
	}

}
