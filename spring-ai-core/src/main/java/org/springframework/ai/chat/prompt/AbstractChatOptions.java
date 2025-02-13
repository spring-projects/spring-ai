/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.chat.prompt;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.lang.Nullable;

/**
 * Abstract base class for {@link ChatOptions}, providing common implementation for its
 * methods.
 *
 * @author Alexandros Pappas
 */
public abstract class AbstractChatOptions implements ChatOptions {

	/**
	 * ID of the model to use.
	 */
	@JsonProperty("model")
	protected String model;

	/**
	 * Number between -2.0 and 2.0. Positive values penalize new tokens based on their
	 * existing frequency in the text so far, decreasing the model's likelihood to repeat
	 * the same line verbatim.
	 */
	@JsonProperty("frequency_penalty")
	protected Double frequencyPenalty;

	/**
	 * The maximum number of tokens to generate in the chat completion. The total length
	 * of input tokens and generated tokens is limited by the model's context length.
	 */
	@JsonProperty("max_tokens")
	protected Integer maxTokens;

	/**
	 * Number between -2.0 and 2.0. Positive values penalize new tokens based on whether
	 * they appear in the text so far, increasing the model's likelihood to talk about new
	 * topics.
	 */
	@JsonProperty("presence_penalty")
	protected Double presencePenalty;

	/**
	 * Sets when the LLM should stop. (e.g., ["\n\n\n"]) then when the LLM generates three
	 * consecutive line breaks it will terminate. Stop sequences are ignored until after
	 * the number of tokens that are specified in the Min tokens parameter are generated.
	 */
	@JsonProperty("stop_sequences")
	protected List<String> stopSequences;

	/**
	 * What sampling temperature to use, between 0 and 1. Higher values like 0.8 will make
	 * the output more random, while lower values like 0.2 will make it more focused and
	 * deterministic. We generally recommend altering this or top_p but not both.
	 */
	@JsonProperty("temperature")
	protected Double temperature;

	/**
	 * Reduces the probability of generating nonsense. A higher value (e.g. 100) will give
	 * more diverse answers, while a lower value (e.g. 10) will be more conservative.
	 * (Default: 40)
	 */
	@JsonProperty("top_k")
	protected Integer topK;

	/**
	 * An alternative to sampling with temperature, called nucleus sampling, where the
	 * model considers the results of the tokens with top_p probability mass. So 0.1 means
	 * only the tokens comprising the top 10% probability mass are considered. We
	 * generally recommend altering this or temperature but not both.
	 */
	@JsonProperty("top_p")
	protected Double topP;

	@Override
	public String getModel() {
		return this.model;
	}

	@Override
	public Double getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	@Override
	@Nullable
	public Integer getMaxTokens() {
		return this.maxTokens;
	}

	@Override
	public Double getPresencePenalty() {
		return this.presencePenalty;
	}

	@Override
	@JsonIgnore
	public List<String> getStopSequences() {
		return this.stopSequences;
	}

	@Override
	public Double getTemperature() {
		return this.temperature;
	}

	@Override
	public Integer getTopK() {
		return this.topK;
	}

	@Override
	@Nullable
	public Double getTopP() {
		return this.topP;
	}

	/**
	 * Generic Abstract Builder for {@link AbstractChatOptions}. Ensures fluent API in
	 * subclasses with proper typing.
	 */
	public abstract static class Builder<T extends AbstractChatOptions, B extends Builder<T, B>>
			implements ChatOptions.Builder {

		protected T options;

		protected String model;

		protected Double frequencyPenalty;

		protected Integer maxTokens;

		protected Double presencePenalty;

		protected List<String> stopSequences;

		protected Double temperature;

		protected Integer topK;

		protected Double topP;

		protected abstract B self();

		public Builder(T options) {
			this.options = options;
		}

		@Override
		public B model(String model) {
			this.model = model;
			return this.self();
		}

		@Override
		public B frequencyPenalty(Double frequencyPenalty) {
			this.frequencyPenalty = frequencyPenalty;
			return this.self();
		}

		@Override
		public B maxTokens(Integer maxTokens) {
			this.maxTokens = maxTokens;
			return this.self();
		}

		@Override
		public B presencePenalty(Double presencePenalty) {
			this.presencePenalty = presencePenalty;
			return this.self();
		}

		@Override
		public B stopSequences(List<String> stopSequences) {
			this.stopSequences = stopSequences;
			return this.self();
		}

		@Override
		public B temperature(Double temperature) {
			this.temperature = temperature;
			return this.self();
		}

		@Override
		public B topK(Integer topK) {
			this.topK = topK;
			return this.self();
		}

		@Override
		public B topP(Double topP) {
			this.topP = topP;
			return this.self();
		}

		@Override
		public abstract T build();

	}

}
