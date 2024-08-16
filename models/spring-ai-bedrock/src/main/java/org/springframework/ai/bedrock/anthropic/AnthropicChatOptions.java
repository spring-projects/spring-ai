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
package org.springframework.ai.bedrock.anthropic;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import org.springframework.ai.chat.prompt.ChatOptions;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Christian Tzolov
 * @author Thomas Vitale
 */
@JsonInclude(Include.NON_NULL)
public class AnthropicChatOptions implements ChatOptions {

	// @formatter:off
	/**
	 * Controls the randomness of the output. Values can range over [0.0,1.0], inclusive. A value closer to 1.0 will
	 * produce responses that are more varied, while a value closer to 0.0 will typically result in less surprising
	 * responses from the generative. This value specifies default to be used by the backend while making the call to
	 * the generative.
	 */
	private @JsonProperty("temperature") Float temperature;

	/**
	 * Specify the maximum number of tokens to use in the generated response. Note that the models may stop before
	 * reaching this maximum. This parameter only specifies the absolute maximum number of tokens to generate. We
	 * recommend a limit of 4,000 tokens for optimal performance.
	 */
	private @JsonProperty("max_tokens_to_sample") Integer maxTokensToSample;

	/**
	 * Specify the number of token choices the generative uses to generate the next token.
	 */
	private @JsonProperty("top_k") Integer topK;

	/**
	 * The maximum cumulative probability of tokens to consider when sampling. The generative uses combined Top-k and
	 * nucleus sampling. Nucleus sampling considers the smallest set of tokens whose probability sum is at least topP.
	 */
	private @JsonProperty("top_p") Float topP;

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

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private final AnthropicChatOptions options = new AnthropicChatOptions();

		public Builder withTemperature(Float temperature) {
			this.options.setTemperature(temperature);
			return this;
		}

		public Builder withMaxTokensToSample(Integer maxTokensToSample) {
			this.options.setMaxTokensToSample(maxTokensToSample);
			return this;
		}

		public Builder withTopK(Integer topK) {
			this.options.setTopK(topK);
			return this;
		}

		public Builder withTopP(Float topP) {
			this.options.setTopP(topP);
			return this;
		}

		public Builder withStopSequences(List<String> stopSequences) {
			this.options.setStopSequences(stopSequences);
			return this;
		}

		public Builder withAnthropicVersion(String anthropicVersion) {
			this.options.setAnthropicVersion(anthropicVersion);
			return this;
		}

		public AnthropicChatOptions build() {
			return this.options;
		}

	}

	@Override
	public Float getTemperature() {
		return this.temperature;
	}

	public void setTemperature(Float temperature) {
		this.temperature = temperature;
	}

	@Override
	@JsonIgnore
	public Integer getMaxTokens() {
		return getMaxTokensToSample();
	}

	@JsonIgnore
	public void setMaxTokens(Integer maxTokens) {
		setMaxTokensToSample(maxTokens);
	}

	public Integer getMaxTokensToSample() {
		return this.maxTokensToSample;
	}

	public void setMaxTokensToSample(Integer maxTokensToSample) {
		this.maxTokensToSample = maxTokensToSample;
	}

	@Override
	public Integer getTopK() {
		return this.topK;
	}

	public void setTopK(Integer topK) {
		this.topK = topK;
	}

	@Override
	public Float getTopP() {
		return this.topP;
	}

	public void setTopP(Float topP) {
		this.topP = topP;
	}

	@Override
	public List<String> getStopSequences() {
		return this.stopSequences;
	}

	public void setStopSequences(List<String> stopSequences) {
		this.stopSequences = stopSequences;
	}

	public String getAnthropicVersion() {
		return this.anthropicVersion;
	}

	public void setAnthropicVersion(String anthropicVersion) {
		this.anthropicVersion = anthropicVersion;
	}

	@Override
	@JsonIgnore
	public String getModel() {
		return null;
	}

	@Override
	@JsonIgnore
	public Float getFrequencyPenalty() {
		return null;
	}

	@Override
	@JsonIgnore
	public Float getPresencePenalty() {
		return null;
	}

	@Override
	public AnthropicChatOptions copy() {
		return fromOptions(this);
	}

	public static AnthropicChatOptions fromOptions(AnthropicChatOptions fromOptions) {
		return builder().withTemperature(fromOptions.getTemperature())
			.withMaxTokensToSample(fromOptions.getMaxTokensToSample())
			.withTopK(fromOptions.getTopK())
			.withTopP(fromOptions.getTopP())
			.withStopSequences(fromOptions.getStopSequences())
			.withAnthropicVersion(fromOptions.getAnthropicVersion())
			.build();
	}

}
