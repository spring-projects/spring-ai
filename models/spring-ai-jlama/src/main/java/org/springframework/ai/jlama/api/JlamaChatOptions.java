/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.jlama.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * Helper class for creating strongly-typed Jlama options.
 *
 * @author chabinhwang
 */
@JsonInclude(Include.NON_NULL)
public class JlamaChatOptions implements ChatOptions {

	@JsonProperty("model")
	private String model;

	@JsonProperty("frequencyPenalty")
	private Double frequencyPenalty;

	@JsonProperty("maxTokens")
	private Integer maxTokens;

	@JsonProperty("presencePenalty")
	private Double presencePenalty;

	@JsonProperty("stopSequences")
	private List<String> stopSequences;

	@JsonProperty("temperature")
	private Double temperature;

	@JsonProperty("topK")
	private Integer topK;

	@JsonProperty("topP")
	private Double topP;

	@JsonProperty("seed")
	private Long seed;

	public JlamaChatOptions() {
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	@Override
	public Double getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	public void setFrequencyPenalty(Double frequencyPenalty) {
		this.frequencyPenalty = frequencyPenalty;
	}

	@Override
	public Integer getMaxTokens() {
		return this.maxTokens;
	}

	public void setMaxTokens(Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	@Override
	public Double getPresencePenalty() {
		return this.presencePenalty;
	}

	public void setPresencePenalty(Double presencePenalty) {
		this.presencePenalty = presencePenalty;
	}

	@Override
	public List<String> getStopSequences() {
		return this.stopSequences;
	}

	public void setStopSequences(List<String> stopSequences) {
		this.stopSequences = stopSequences;
	}

	@Override
	public Double getTemperature() {
		return this.temperature;
	}

	public void setTemperature(Double temperature) {
		this.temperature = temperature;
	}

	@Override
	public Integer getTopK() {
		return this.topK;
	}

	public void setTopK(Integer topK) {
		this.topK = topK;
	}

	@Override
	public Double getTopP() {
		return this.topP;
	}

	public void setTopP(Double topP) {
		this.topP = topP;
	}

	public Long getSeed() {
		return this.seed;
	}

	public void setSeed(Long seed) {
		this.seed = seed;
	}

	@Override
	public <T extends ChatOptions> T copy() {
		return (T) builder().model(this.model)
			.frequencyPenalty(this.frequencyPenalty)
			.maxTokens(this.maxTokens)
			.presencePenalty(this.presencePenalty)
			.stopSequences(this.stopSequences != null ? new ArrayList<>(this.stopSequences) : null)
			.temperature(this.temperature)
			.topK(this.topK)
			.topP(this.topP)
			.seed(this.seed)
			.build();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		JlamaChatOptions that = (JlamaChatOptions) o;
		return Objects.equals(this.model, that.model) && Objects.equals(this.frequencyPenalty, that.frequencyPenalty)
				&& Objects.equals(this.maxTokens, that.maxTokens)
				&& Objects.equals(this.presencePenalty, that.presencePenalty)
				&& Objects.equals(this.stopSequences, that.stopSequences)
				&& Objects.equals(this.temperature, that.temperature) && Objects.equals(this.topK, that.topK)
				&& Objects.equals(this.topP, that.topP) && Objects.equals(this.seed, that.seed);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.frequencyPenalty, this.maxTokens, this.presencePenalty, this.stopSequences,
				this.temperature, this.topK, this.topP, this.seed);
	}

	@Override
	public String toString() {
		return "JlamaChatOptions{" + "model='" + this.model + '\'' + ", frequencyPenalty=" + this.frequencyPenalty
				+ ", maxTokens=" + this.maxTokens + ", presencePenalty=" + this.presencePenalty + ", stopSequences="
				+ String.valueOf(this.stopSequences) + ", temperature=" + this.temperature + ", topK=" + this.topK
				+ ", topP=" + this.topP + ", seed=" + this.seed + '}';
	}

	public static class Builder implements ChatOptions.Builder {

		private final JlamaChatOptions options = new JlamaChatOptions();

		public Builder model(String model) {
			this.options.setModel(model);
			return this;
		}

		public Builder frequencyPenalty(Double frequencyPenalty) {
			this.options.setFrequencyPenalty(frequencyPenalty);
			return this;
		}

		public Builder maxTokens(Integer maxTokens) {
			this.options.setMaxTokens(maxTokens);
			return this;
		}

		public Builder presencePenalty(Double presencePenalty) {
			this.options.setPresencePenalty(presencePenalty);
			return this;
		}

		public Builder stopSequences(List<String> stopSequences) {
			this.options.setStopSequences(stopSequences);
			return this;
		}

		public Builder temperature(Double temperature) {
			this.options.setTemperature(temperature);
			return this;
		}

		public Builder topK(Integer topK) {
			this.options.setTopK(topK);
			return this;
		}

		public Builder topP(Double topP) {
			this.options.setTopP(topP);
			return this;
		}

		public Builder seed(Long seed) {
			this.options.setSeed(seed);
			return this;
		}

		public JlamaChatOptions build() {
			return this.options;
		}

	}

}
