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
package org.springframework.ai.bedrock.cohere;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi.CohereChatRequest.LogitBias;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi.CohereChatRequest.ReturnLikelihoods;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi.CohereChatRequest.Truncate;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 0.8.0
 */
@JsonInclude(Include.NON_NULL)
public class BedrockCohereChatOptions implements ChatOptions {

	// @formatter:off
	/**
	 * (optional) Use a lower value to decrease randomness in the response. Defaults to
	 * 0.7.
	 */
	@JsonProperty("temperature") Float temperature;
	/**
	 * (optional) The maximum cumulative probability of tokens to consider when sampling.
	 * The generative uses combined Top-k and nucleus sampling. Nucleus sampling considers
	 * the smallest set of tokens whose probability sum is at least topP.
	 */
	@JsonProperty("p") Float topP;
	/**
	 * (optional) Specify the number of token choices the generative uses to generate the
	 * next token.
	 */
	@JsonProperty("k") Integer topK;
	/**
	 * (optional) Specify the maximum number of tokens to use in the generated response.
	 */
	@JsonProperty("max_tokens") Integer maxTokens;
	/**
	 * (optional) Configure up to four sequences that the generative recognizes. After a
	 * stop sequence, the generative stops generating further tokens. The returned text
	 * doesn't contain the stop sequence.
	 */
	@JsonProperty("stop_sequences") List<String> stopSequences;
	/**
	 * (optional) Specify how and if the token likelihoods are returned with the response.
	 */
	@JsonProperty("return_likelihoods") ReturnLikelihoods returnLikelihoods;
	/**
	 * (optional) The maximum number of generations that the generative should return.
	 */
	@JsonProperty("num_generations") Integer numGenerations;
	/**
	 * Prevents the model from generating unwanted tokens or incentivize the model to include desired tokens.
	 */
	@JsonProperty("logit_bias") LogitBias logitBias;
	/**
	 * (optional) Specifies how the API handles inputs longer than the maximum token
	 * length.
	 */
	@JsonProperty("truncate") Truncate truncate;
	// @formatter:on

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private final BedrockCohereChatOptions options = new BedrockCohereChatOptions();

		public Builder withTemperature(Float temperature) {
			this.options.setTemperature(temperature);
			return this;
		}

		public Builder withTopP(Float topP) {
			this.options.setTopP(topP);
			return this;
		}

		public Builder withTopK(Integer topK) {
			this.options.setTopK(topK);
			return this;
		}

		public Builder withMaxTokens(Integer maxTokens) {
			this.options.setMaxTokens(maxTokens);
			return this;
		}

		public Builder withStopSequences(List<String> stopSequences) {
			this.options.setStopSequences(stopSequences);
			return this;
		}

		public Builder withReturnLikelihoods(ReturnLikelihoods returnLikelihoods) {
			this.options.setReturnLikelihoods(returnLikelihoods);
			return this;
		}

		public Builder withNumGenerations(Integer numGenerations) {
			this.options.setNumGenerations(numGenerations);
			return this;
		}

		public Builder withLogitBias(LogitBias logitBias) {
			this.options.setLogitBias(logitBias);
			return this;
		}

		public Builder withTruncate(Truncate truncate) {
			this.options.setTruncate(truncate);
			return this;
		}

		public BedrockCohereChatOptions build() {
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
	public Float getTopP() {
		return this.topP;
	}

	public void setTopP(Float topP) {
		this.topP = topP;
	}

	@Override
	public Integer getTopK() {
		return this.topK;
	}

	public void setTopK(Integer topK) {
		this.topK = topK;
	}

	@Override
	public Integer getMaxTokens() {
		return this.maxTokens;
	}

	public void setMaxTokens(Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	@Override
	public List<String> getStopSequences() {
		return this.stopSequences;
	}

	public void setStopSequences(List<String> stopSequences) {
		this.stopSequences = stopSequences;
	}

	public ReturnLikelihoods getReturnLikelihoods() {
		return this.returnLikelihoods;
	}

	public void setReturnLikelihoods(ReturnLikelihoods returnLikelihoods) {
		this.returnLikelihoods = returnLikelihoods;
	}

	public Integer getNumGenerations() {
		return this.numGenerations;
	}

	public void setNumGenerations(Integer numGenerations) {
		this.numGenerations = numGenerations;
	}

	public LogitBias getLogitBias() {
		return this.logitBias;
	}

	public void setLogitBias(LogitBias logitBias) {
		this.logitBias = logitBias;
	}

	public Truncate getTruncate() {
		return this.truncate;
	}

	public void setTruncate(Truncate truncate) {
		this.truncate = truncate;
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
	public BedrockCohereChatOptions copy() {
		return fromOptions(this);
	}

	public static BedrockCohereChatOptions fromOptions(BedrockCohereChatOptions fromOptions) {
		return builder().withTemperature(fromOptions.getTemperature())
			.withTopP(fromOptions.getTopP())
			.withTopK(fromOptions.getTopK())
			.withMaxTokens(fromOptions.getMaxTokens())
			.withStopSequences(fromOptions.getStopSequences())
			.withReturnLikelihoods(fromOptions.getReturnLikelihoods())
			.withNumGenerations(fromOptions.getNumGenerations())
			.withLogitBias(fromOptions.getLogitBias())
			.withTruncate(fromOptions.getTruncate())
			.build();
	}

}
