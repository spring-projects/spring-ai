/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.mistral;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * @author Ricken Bazolo
 * @author Christian Tzolov
 * @since 0.8.1
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MistralAiChatOptions implements ChatOptions {

	/**
	 * ID of the model to use
	 */
	private @JsonProperty("model") String model;

	/**
	 * What sampling temperature to use, between 0.0 and 1.0. Higher values like 0.8 will
	 * make the output more random, while lower values like 0.2 will make it more focused
	 * and deterministic. We generally recommend altering this or top_p but not both.
	 */
	private @JsonProperty("temperature") Float temperature;

	/**
	 * Nucleus sampling, where the model considers the results of the tokens with top_p
	 * probability mass. So 0.1 means only the tokens comprising the top 10% probability
	 * mass are considered. We generally recommend altering this or temperature but not
	 * both.
	 */
	private @JsonProperty("top_p") Float topP;

	/**
	 * The maximum number of tokens to generate in the completion. The token count of your
	 * prompt plus max_tokens cannot exceed the model's context length.
	 */
	private @JsonProperty("max_tokens") Integer maxTokens;

	/**
	 * Whether to inject a safety prompt before all conversations.
	 */
	private @JsonProperty("safe_prompt") Boolean safePrompt;

	/**
	 * The seed to use for random sampling. If set, different calls will generate
	 * deterministic results.
	 */
	private @JsonProperty("random_seed") Integer randomSeed;

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private final MistralAiChatOptions options = new MistralAiChatOptions();

		public Builder withModel(String model) {
			this.options.setModel(model);
			return this;
		}

		public Builder withMaxToken(Integer maxTokens) {
			this.options.setMaxTokens(maxTokens);
			return this;
		}

		public Builder withSafePrompt(Boolean safePrompt) {
			this.options.setSafePrompt(safePrompt);
			return this;
		}

		public Builder withRandomSeed(Integer randomSeed) {
			this.options.setRandomSeed(randomSeed);
			return this;
		}

		public Builder withTemperature(Float temperature) {
			this.options.setTemperature(temperature);
			return this;
		}

		public Builder withTopP(Float topP) {
			this.options.setTopP(topP);
			return this;
		}

		public MistralAiChatOptions build() {
			return this.options;
		}

	}

	public String getModel() {
		return this.model;
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

	public Boolean getSafePrompt() {
		return this.safePrompt;
	}

	public void setSafePrompt(Boolean safePrompt) {
		this.safePrompt = safePrompt;
	}

	public Integer getRandomSeed() {
		return this.randomSeed;
	}

	public void setRandomSeed(Integer randomSeed) {
		this.randomSeed = randomSeed;
	}

	@Override
	public Float getTemperature() {
		return this.temperature;
	}

	@Override
	public void setTemperature(Float temperature) {
		this.temperature = temperature;
	}

	@Override
	public Float getTopP() {
		return this.topP;
	}

	@Override
	public void setTopP(Float topP) {
		this.topP = topP;
	}

	@Override
	@JsonIgnore
	public Integer getTopK() {
		throw new UnsupportedOperationException("Unsupported option: 'TopK'");
	}

	@Override
	@JsonIgnore
	public void setTopK(Integer topK) {
		throw new UnsupportedOperationException("Unsupported option: 'TopK'");
	}

}
