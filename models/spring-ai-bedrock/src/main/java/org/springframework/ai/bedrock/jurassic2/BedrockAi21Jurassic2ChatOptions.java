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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.chat.prompt.ChatOptions;

import java.util.List;

/**
 * Request body for the /complete endpoint of the Jurassic-2 API.
 *
 * @author Ahmed Yousri
 * @author Thomas Vitale
 * @since 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BedrockAi21Jurassic2ChatOptions implements ChatOptions {

	/**
	 * The text which the model is requested to continue.
	 */
	@JsonProperty("prompt")
	private String prompt;

	/**
	 * Number of completions to sample and return.
	 */
	@JsonProperty("numResults")
	private Integer numResults;

	/**
	 * The maximum number of tokens to generate per result.
	 */
	@JsonProperty("maxTokens")
	private Integer maxTokens;

	/**
	 * The minimum number of tokens to generate per result.
	 */
	@JsonProperty("minTokens")
	private Integer minTokens;

	/**
	 * Modifies the distribution from which tokens are sampled.
	 */
	@JsonProperty("temperature")
	private Float temperature;

	/**
	 * Sample tokens from the corresponding top percentile of probability mass.
	 */
	@JsonProperty("topP")
	private Float topP;

	/**
	 * Return the top-K (topKReturn) alternative tokens.
	 */
	@JsonProperty("topKReturn")
	private Integer topK;

	/**
	 * Stops decoding if any of the strings is generated.
	 */
	@JsonProperty("stopSequences")
	private List<String> stopSequences;

	/**
	 * Penalty object for frequency.
	 */
	@JsonProperty("frequencyPenalty")
	private Penalty frequencyPenaltyOptions;

	/**
	 * Penalty object for presence.
	 */
	@JsonProperty("presencePenalty")
	private Penalty presencePenaltyOptions;

	/**
	 * Penalty object for count.
	 */
	@JsonProperty("countPenalty")
	private Penalty countPenaltyOptions;

	// Getters and setters

	/**
	 * Gets the prompt text for the model to continue.
	 * @return The prompt text.
	 */
	public String getPrompt() {
		return prompt;
	}

	/**
	 * Sets the prompt text for the model to continue.
	 * @param prompt The prompt text.
	 */
	public void setPrompt(String prompt) {
		this.prompt = prompt;
	}

	/**
	 * Gets the number of completions to sample and return.
	 * @return The number of results.
	 */
	public Integer getNumResults() {
		return numResults;
	}

	/**
	 * Sets the number of completions to sample and return.
	 * @param numResults The number of results.
	 */
	public void setNumResults(Integer numResults) {
		this.numResults = numResults;
	}

	/**
	 * Gets the maximum number of tokens to generate per result.
	 * @return The maximum number of tokens.
	 */
	@Override
	public Integer getMaxTokens() {
		return maxTokens;
	}

	/**
	 * Sets the maximum number of tokens to generate per result.
	 * @param maxTokens The maximum number of tokens.
	 */
	public void setMaxTokens(Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	/**
	 * Gets the minimum number of tokens to generate per result.
	 * @return The minimum number of tokens.
	 */
	public Integer getMinTokens() {
		return minTokens;
	}

	/**
	 * Sets the minimum number of tokens to generate per result.
	 * @param minTokens The minimum number of tokens.
	 */
	public void setMinTokens(Integer minTokens) {
		this.minTokens = minTokens;
	}

	/**
	 * Gets the temperature for modifying the token sampling distribution.
	 * @return The temperature.
	 */
	@Override
	public Float getTemperature() {
		return temperature;
	}

	/**
	 * Sets the temperature for modifying the token sampling distribution.
	 * @param temperature The temperature.
	 */
	public void setTemperature(Float temperature) {
		this.temperature = temperature;
	}

	/**
	 * Gets the topP parameter for sampling tokens from the top percentile of probability
	 * mass.
	 * @return The topP parameter.
	 */
	@Override
	public Float getTopP() {
		return topP;
	}

	/**
	 * Sets the topP parameter for sampling tokens from the top percentile of probability
	 * mass.
	 * @param topP The topP parameter.
	 */
	public void setTopP(Float topP) {
		this.topP = topP;
	}

	/**
	 * Gets the top-K (topKReturn) alternative tokens to return.
	 * @return The top-K parameter. (topKReturn)
	 */
	@Override
	public Integer getTopK() {
		return topK;
	}

	/**
	 * Sets the top-K (topKReturn) alternative tokens to return.
	 * @param topK The top-K parameter (topKReturn).
	 */
	public void setTopK(Integer topK) {
		this.topK = topK;
	}

	/**
	 * Gets the stop sequences for stopping decoding if any of the strings is generated.
	 * @return The stop sequences.
	 */
	@Override
	public List<String> getStopSequences() {
		return stopSequences;
	}

	/**
	 * Sets the stop sequences for stopping decoding if any of the strings is generated.
	 * @param stopSequences The stop sequences.
	 */
	public void setStopSequences(List<String> stopSequences) {
		this.stopSequences = stopSequences;
	}

	@Override
	@JsonIgnore
	public Float getFrequencyPenalty() {
		return getFrequencyPenaltyOptions() != null ? getFrequencyPenaltyOptions().scale() : null;
	}

	@JsonIgnore
	public void setFrequencyPenalty(Float frequencyPenalty) {
		if (frequencyPenalty != null) {
			setFrequencyPenaltyOptions(Penalty.builder().scale(frequencyPenalty).build());
		}
	}

	/**
	 * Gets the frequency penalty object.
	 * @return The frequency penalty object.
	 */
	public Penalty getFrequencyPenaltyOptions() {
		return frequencyPenaltyOptions;
	}

	/**
	 * Sets the frequency penalty object.
	 * @param frequencyPenaltyOptions The frequency penalty object.
	 */
	public void setFrequencyPenaltyOptions(Penalty frequencyPenaltyOptions) {
		this.frequencyPenaltyOptions = frequencyPenaltyOptions;
	}

	@Override
	@JsonIgnore
	public Float getPresencePenalty() {
		return getPresencePenaltyOptions() != null ? getPresencePenaltyOptions().scale() : null;
	}

	@JsonIgnore
	public void setPresencePenalty(Float presencePenalty) {
		if (presencePenalty != null) {
			setPresencePenaltyOptions(Penalty.builder().scale(presencePenalty).build());
		}
	}

	/**
	 * Gets the presence penalty object.
	 * @return The presence penalty object.
	 */
	public Penalty getPresencePenaltyOptions() {
		return presencePenaltyOptions;
	}

	/**
	 * Sets the presence penalty object.
	 * @param presencePenaltyOptions The presence penalty object.
	 */
	public void setPresencePenaltyOptions(Penalty presencePenaltyOptions) {
		this.presencePenaltyOptions = presencePenaltyOptions;
	}

	/**
	 * Gets the count penalty object.
	 * @return The count penalty object.
	 */
	public Penalty getCountPenaltyOptions() {
		return countPenaltyOptions;
	}

	/**
	 * Sets the count penalty object.
	 * @param countPenaltyOptions The count penalty object.
	 */
	public void setCountPenaltyOptions(Penalty countPenaltyOptions) {
		this.countPenaltyOptions = countPenaltyOptions;
	}

	@Override
	@JsonIgnore
	public String getModel() {
		return null;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private final BedrockAi21Jurassic2ChatOptions request = new BedrockAi21Jurassic2ChatOptions();

		public Builder withPrompt(String prompt) {
			request.setPrompt(prompt);
			return this;
		}

		public Builder withNumResults(Integer numResults) {
			request.setNumResults(numResults);
			return this;
		}

		public Builder withMaxTokens(Integer maxTokens) {
			request.setMaxTokens(maxTokens);
			return this;
		}

		public Builder withMinTokens(Integer minTokens) {
			request.setMinTokens(minTokens);
			return this;
		}

		public Builder withTemperature(Float temperature) {
			request.setTemperature(temperature);
			return this;
		}

		public Builder withTopP(Float topP) {
			request.setTopP(topP);
			return this;
		}

		public Builder withStopSequences(List<String> stopSequences) {
			request.setStopSequences(stopSequences);
			return this;
		}

		public Builder withTopK(Integer topKReturn) {
			request.setTopK(topKReturn);
			return this;
		}

		public Builder withFrequencyPenaltyOptions(BedrockAi21Jurassic2ChatOptions.Penalty frequencyPenalty) {
			request.setFrequencyPenaltyOptions(frequencyPenalty);
			return this;
		}

		public Builder withPresencePenaltyOptions(BedrockAi21Jurassic2ChatOptions.Penalty presencePenalty) {
			request.setPresencePenaltyOptions(presencePenalty);
			return this;
		}

		public Builder withCountPenaltyOptions(BedrockAi21Jurassic2ChatOptions.Penalty countPenalty) {
			request.setCountPenaltyOptions(countPenalty);
			return this;
		}

		public BedrockAi21Jurassic2ChatOptions build() {
			return request;
		}

	}

	/**
	 * Penalty object for frequency, presence, and count penalties.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Penalty(@JsonProperty("scale") Float scale, @JsonProperty("applyToNumbers") Boolean applyToNumbers,
			@JsonProperty("applyToPunctuations") Boolean applyToPunctuations,
			@JsonProperty("applyToStopwords") Boolean applyToStopwords,
			@JsonProperty("applyToWhitespaces") Boolean applyToWhitespaces,
			@JsonProperty("applyToEmojis") Boolean applyToEmojis) {

		public static Builder builder() {
			return new Builder();
		}

		public static class Builder {

			private Float scale;

			// can't keep it null due to modelOptionsUtils#mapToClass convert null to
			// false
			private Boolean applyToNumbers = true;

			private Boolean applyToPunctuations = true;

			private Boolean applyToStopwords = true;

			private Boolean applyToWhitespaces = true;

			private Boolean applyToEmojis = true;

			public Builder scale(Float scale) {
				this.scale = scale;
				return this;
			}

			public Builder applyToNumbers(Boolean applyToNumbers) {
				this.applyToNumbers = applyToNumbers;
				return this;
			}

			public Builder applyToPunctuations(Boolean applyToPunctuations) {
				this.applyToPunctuations = applyToPunctuations;
				return this;
			}

			public Builder applyToStopwords(Boolean applyToStopwords) {
				this.applyToStopwords = applyToStopwords;
				return this;
			}

			public Builder applyToWhitespaces(Boolean applyToWhitespaces) {
				this.applyToWhitespaces = applyToWhitespaces;
				return this;
			}

			public Builder applyToEmojis(Boolean applyToEmojis) {
				this.applyToEmojis = applyToEmojis;
				return this;
			}

			public Penalty build() {
				return new Penalty(scale, applyToNumbers, applyToPunctuations, applyToStopwords, applyToWhitespaces,
						applyToEmojis);
			}

		}
	}

	@Override
	public BedrockAi21Jurassic2ChatOptions copy() {
		return fromOptions(this);
	}

	public static BedrockAi21Jurassic2ChatOptions fromOptions(BedrockAi21Jurassic2ChatOptions fromOptions) {
		return builder().withPrompt(fromOptions.getPrompt())
			.withNumResults(fromOptions.getNumResults())
			.withMaxTokens(fromOptions.getMaxTokens())
			.withMinTokens(fromOptions.getMinTokens())
			.withTemperature(fromOptions.getTemperature())
			.withTopP(fromOptions.getTopP())
			.withTopK(fromOptions.getTopK())
			.withStopSequences(fromOptions.getStopSequences())
			.withFrequencyPenaltyOptions(fromOptions.getFrequencyPenaltyOptions())
			.withPresencePenaltyOptions(fromOptions.getPresencePenaltyOptions())
			.withCountPenaltyOptions(fromOptions.getCountPenaltyOptions())
			.build();
	}

}
