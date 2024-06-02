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
package org.springframework.ai.bedrock.mistral;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * @author Wei Jiang
 * @since 1.0.0
 */
@JsonInclude(Include.NON_NULL)
public class BedrockMistralChatOptions implements ChatOptions {

	/**
	 * The temperature value controls the randomness of the generated text. Use a lower
	 * value to decrease randomness in the response.
	 */
	private @JsonProperty("temperature") Float temperature;

	/**
	 * (optional) The maximum cumulative probability of tokens to consider when sampling.
	 * The generative uses combined Top-k and nucleus sampling. Nucleus sampling considers
	 * the smallest set of tokens whose probability sum is at least topP.
	 */
	private @JsonProperty("top_p") Float topP;

	/**
	 * (optional) Specify the number of token choices the generative uses to generate the
	 * next token.
	 */
	private @JsonProperty("top_p") Integer topK;

	/**
	 * (optional) Specify the maximum number of tokens to use in the generated response.
	 */
	private @JsonProperty("max_tokens") Integer maxTokens;

	/**
	 * (optional) Configure up to four sequences that the generative recognizes. After a
	 * stop sequence, the generative stops generating further tokens. The returned text
	 * doesn't contain the stop sequence.
	 */
	private @JsonProperty("stop") List<String> stopSequences;

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private final BedrockMistralChatOptions options = new BedrockMistralChatOptions();

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

		public BedrockMistralChatOptions build() {
			return this.options;
		}

	}

	public void setTemperature(Float temperature) {
		this.temperature = temperature;
	}

	@Override
	public Float getTemperature() {
		return this.temperature;
	}

	public void setTopP(Float topP) {
		this.topP = topP;
	}

	@Override
	public Float getTopP() {
		return this.topP;
	}

	public void setTopK(Integer topK) {
		this.topK = topK;
	}

	@Override
	public Integer getTopK() {
		return this.topK;
	}

	public Integer getMaxTokens() {
		return maxTokens;
	}

	public void setMaxTokens(Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	public List<String> getStopSequences() {
		return stopSequences;
	}

	public void setStopSequences(List<String> stopSequences) {
		this.stopSequences = stopSequences;
	}

	public static BedrockMistralChatOptions fromOptions(BedrockMistralChatOptions fromOptions) {
		return builder().withTemperature(fromOptions.getTemperature())
			.withTopP(fromOptions.getTopP())
			.withTopK(fromOptions.getTopK())
			.withMaxTokens(fromOptions.getMaxTokens())
			.withStopSequences(fromOptions.getStopSequences())
			.build();
	}

}
