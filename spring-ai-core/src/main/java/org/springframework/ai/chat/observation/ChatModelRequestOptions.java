/*
 * Copyright 2024 the original author or authors.
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
package org.springframework.ai.chat.observation;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.List;

/**
 * Represents client-side options for chat model requests.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class ChatModelRequestOptions implements ChatOptions {

	private final String model;

	@Nullable
	private final Float frequencyPenalty;

	@Nullable
	private final Integer maxTokens;

	@Nullable
	private final Float presencePenalty;

	@Nullable
	private final List<String> stopSequences;

	@Nullable
	private final Float temperature;

	@Nullable
	private final Integer topK;

	@Nullable
	private final Float topP;

	ChatModelRequestOptions(Builder builder) {
		Assert.hasText(builder.model, "model cannot be null or empty");

		this.model = builder.model;
		this.frequencyPenalty = builder.frequencyPenalty;
		this.maxTokens = builder.maxTokens;
		this.presencePenalty = builder.presencePenalty;
		this.stopSequences = builder.stopSequences;
		this.temperature = builder.temperature;
		this.topK = builder.topK;
		this.topP = builder.topP;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private String model;

		@Nullable
		private Float frequencyPenalty;

		@Nullable
		private Integer maxTokens;

		@Nullable
		private Float presencePenalty;

		@Nullable
		private List<String> stopSequences;

		@Nullable
		private Float temperature;

		@Nullable
		private Integer topK;

		@Nullable
		private Float topP;

		private Builder() {
		}

		public Builder model(String model) {
			this.model = model;
			return this;
		}

		public Builder frequencyPenalty(@Nullable Float frequencyPenalty) {
			this.frequencyPenalty = frequencyPenalty;
			return this;
		}

		public Builder maxTokens(@Nullable Integer maxTokens) {
			this.maxTokens = maxTokens;
			return this;
		}

		public Builder presencePenalty(@Nullable Float presencePenalty) {
			this.presencePenalty = presencePenalty;
			return this;
		}

		public Builder stopSequences(@Nullable List<String> stopSequences) {
			this.stopSequences = stopSequences;
			return this;
		}

		public Builder temperature(@Nullable Float temperature) {
			this.temperature = temperature;
			return this;
		}

		public Builder topK(@Nullable Integer topK) {
			this.topK = topK;
			return this;
		}

		public Builder topP(@Nullable Float topP) {
			this.topP = topP;
			return this;
		}

		public ChatModelRequestOptions build() {
			return new ChatModelRequestOptions(this);
		}

	}

	public String getModel() {
		return this.model;
	}

	@Nullable
	public Float getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	@Nullable
	public Integer getMaxTokens() {
		return this.maxTokens;
	}

	@Nullable
	public Float getPresencePenalty() {
		return this.presencePenalty;
	}

	@Nullable
	public List<String> getStopSequences() {
		return this.stopSequences;
	}

	@Override
	@Nullable
	public Float getTemperature() {
		return this.temperature;
	}

	@Override
	@Nullable
	public Integer getTopK() {
		return this.topK;
	}

	@Override
	@Nullable
	public Float getTopP() {
		return this.topP;
	}

	@Override
	public ChatOptions copy() {
		return builder().model(this.model)
			.frequencyPenalty(this.frequencyPenalty)
			.maxTokens(this.maxTokens)
			.presencePenalty(this.presencePenalty)
			.stopSequences(this.stopSequences != null ? List.copyOf(this.stopSequences) : null)
			.temperature(this.temperature)
			.topK(this.topK)
			.topP(this.topP)
			.build();
	}

}
