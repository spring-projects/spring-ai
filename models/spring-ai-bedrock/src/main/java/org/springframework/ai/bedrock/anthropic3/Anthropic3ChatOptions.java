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

package org.springframework.ai.bedrock.anthropic3;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * Options for the Anthropic 3 chat API.
 *
 * @author Ben Middleton
 * @author Thomas Vitale
 * @since 1.0.0
 */
@JsonInclude(Include.NON_NULL)
public class Anthropic3ChatOptions implements ChatOptions {

	// @formatter:off
	/**
	 * Controls the randomness of the output. Values can range over [0.0,1.0], inclusive. A value closer to 1.0 will
	 * produce responses that are more varied, while a value closer to 0.0 will typically result in less surprising
	 * responses from the generative. This value specifies default to be used by the backend while making the call to
	 * the generative.
	 */
	private @JsonProperty("temperature") Double temperature;

	/**
	 * Specify the maximum number of tokens to use in the generated response. Note that the models may stop before
	 * reaching this maximum. This parameter only specifies the absolute maximum number of tokens to generate. We
	 * recommend a limit of 4,000 tokens for optimal performance.
	 */
	private @JsonProperty("max_tokens") Integer maxTokens;

	/**
	 * Specify the number of token choices the generative uses to generate the next token.
	 */
	private @JsonProperty("top_k") Integer topK;

	/**
	 * The maximum cumulative probability of tokens to consider when sampling. The generative uses combined Top-k and
	 * nucleus sampling. Nucleus sampling considers the smallest set of tokens whose probability sum is at least topP.
	 */
	private @JsonProperty("top_p") Double topP;

	/**
	 * Configure up to four sequences that the generative recognizes. After a stop sequence, the generative stops
	 * generating further tokens. The returned text doesn't contain the stop sequence.
	 */
	private @JsonProperty("stop_sequences") List<String> stopSequences;

	/**
	 * The version of the generative to use. The default value is bedrock-2023-05-31.
	 */
	private @JsonProperty("anthropic_version") String anthropicVersion;
	// @formatter:on

	Anthropic3ChatOptions() {
	}

	/**
	 * Create a new {@link Anthropic3ChatOptions}.
	 * @return a new {@link Anthropic3ChatOptions}
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Create a new {@link Anthropic3ChatOptions} from the provided
	 * {@link Anthropic3ChatOptions}.
	 * @param fromOptions the options to copy
	 * @return a new {@link Anthropic3ChatOptions}
	 */
	public static Anthropic3ChatOptions fromOptions(Anthropic3ChatOptions fromOptions) {
		return builder().withTemperature(fromOptions.getTemperature())
			.withMaxTokens(fromOptions.getMaxTokens())
			.withTopK(fromOptions.getTopK())
			.withTopP(fromOptions.getTopP())
			.withStopSequences(fromOptions.getStopSequences())
			.withAnthropicVersion(fromOptions.getAnthropicVersion())
			.build();
	}

	/**
	 * Get the temperature.
	 * @return the temperature
	 */
	@Override
	public Double getTemperature() {
		return this.temperature;
	}

	/**
	 * Set the temperature.
	 * @param temperature the temperature
	 */
	public void setTemperature(Double temperature) {
		this.temperature = temperature;
	}

	/**
	 * Get the maximum number of tokens.
	 * @return the maximum number of tokens
	 */
	@Override
	public Integer getMaxTokens() {
		return this.maxTokens;
	}

	/**
	 * Set the maximum number of tokens.
	 * @param maxTokens the maximum number of tokens
	 */
	public void setMaxTokens(Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	/**
	 * Get the top k.
	 * @return the top k
	 */
	@Override
	public Integer getTopK() {
		return this.topK;
	}

	/**
	 * Set the top k.
	 * @param topK the top k
	 */
	public void setTopK(Integer topK) {
		this.topK = topK;
	}

	/**
	 * Get the top p.
	 * @return the top p
	 */
	@Override
	public Double getTopP() {
		return this.topP;
	}

	/**
	 * Set the top p.
	 * @param topP the top p
	 */
	public void setTopP(Double topP) {
		this.topP = topP;
	}

	/**
	 * Get the stop sequences.
	 * @return the stop sequences
	 */
	@Override
	public List<String> getStopSequences() {
		return this.stopSequences;
	}

	/**
	 * Set the stop sequences.
	 * @param stopSequences the stop sequences
	 */
	public void setStopSequences(List<String> stopSequences) {
		this.stopSequences = stopSequences;
	}

	/**
	 * Get the version of the generative to use.
	 * @return the version of the generative to use
	 */
	public String getAnthropicVersion() {
		return this.anthropicVersion;
	}

	/**
	 * Set the version of the generative to use.
	 * @param anthropicVersion the version of the generative to use
	 */
	public void setAnthropicVersion(String anthropicVersion) {
		this.anthropicVersion = anthropicVersion;
	}

	/**
	 * Get the model.
	 * @return the model
	 */
	@Override
	@JsonIgnore
	public String getModel() {
		return null;
	}

	/**
	 * Get the frequency penalty.
	 * @return the frequency penalty
	 */
	@Override
	@JsonIgnore
	public Double getFrequencyPenalty() {
		return null;
	}

	/**
	 * Get the presence penalty.
	 * @return the presence penalty
	 */
	@Override
	@JsonIgnore
	public Double getPresencePenalty() {
		return null;
	}

	/**
	 * Get the embedding dimensions.
	 * @return the embedding dimensions
	 */
	@Override
	public Anthropic3ChatOptions copy() {
		return fromOptions(this);
	}

	/**
	 * Builder for {@link Anthropic3ChatOptions}.
	 */
	public static final class Builder {

		private final Anthropic3ChatOptions options = new Anthropic3ChatOptions();

		private Builder() {
		}

		/**
		 * Set the temperature.
		 * @param temperature the temperature
		 * @return this {@link Builder} instance
		 */
		public Builder withTemperature(Double temperature) {
			this.options.setTemperature(temperature);
			return this;
		}

		/**
		 * Set the maximum number of tokens.
		 * @param maxTokens the maximum number of tokens
		 * @return this {@link Builder} instance
		 */
		public Builder withMaxTokens(Integer maxTokens) {
			this.options.setMaxTokens(maxTokens);
			return this;
		}

		/**
		 * Set the top k.
		 * @param topK the top k
		 * @return this {@link Builder} instance
		 */
		public Builder withTopK(Integer topK) {
			this.options.setTopK(topK);
			return this;
		}

		/**
		 * Set the top p.
		 * @param topP the top p
		 * @return this {@link Builder} instance
		 */
		public Builder withTopP(Double topP) {
			this.options.setTopP(topP);
			return this;
		}

		/**
		 * Set the stop sequences.
		 * @param stopSequences the stop sequences
		 * @return this {@link Builder} instance
		 */
		public Builder withStopSequences(List<String> stopSequences) {
			this.options.setStopSequences(stopSequences);
			return this;
		}

		/**
		 * Set the version of the generative to use.
		 * @param anthropicVersion the version of the generative to use
		 * @return this {@link Builder} instance
		 */
		public Builder withAnthropicVersion(String anthropicVersion) {
			this.options.setAnthropicVersion(anthropicVersion);
			return this;
		}

		/**
		 * Build the {@link Anthropic3ChatOptions}.
		 * @return the {@link Anthropic3ChatOptions}
		 */
		public Anthropic3ChatOptions build() {
			return this.options;
		}

	}

}
