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
package org.springframework.ai.bedrock.titan;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import org.springframework.ai.chat.prompt.ChatOptions;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Java {@link ChatOptions} for the Bedrock Titan chat generative model chat options.
 * https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-titan-text.html
 *
 * @author Christian Tzolov
 * @author Wei Jiang
 * @since 0.8.0
 */
@JsonInclude(Include.NON_NULL)
public class BedrockTitanChatOptions implements ChatOptions {

	/**
	 * The Titan chat model text generation config.
	 */
	private @JsonProperty("textGenerationConfig") TextGenerationConfig textGenerationConfig = new TextGenerationConfig();

	@JsonInclude(Include.NON_NULL)
	public static class TextGenerationConfig {

		// @formatter:off
		/**
		 * The temperature value controls the randomness of the generated text.
		 */
		private @JsonProperty(value = "temperature") Float temperature;

		/**
		 * The topP value controls the diversity of the generated text. Use a lower value to ignore less probable options.
		 */
		private @JsonProperty("topP") Float topP;

		/**
		 * Maximum number of tokens to generate.
		 */
		private @JsonProperty("maxTokenCount") Integer maxTokenCount;

		/**
		 * A list of tokens that the model should stop generating after.
		 */
		private @JsonProperty("stopSequences") List<String> stopSequences;
		// @formatter:on

	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private BedrockTitanChatOptions options = new BedrockTitanChatOptions();

		public Builder withTemperature(Float temperature) {
			this.options.textGenerationConfig.temperature = temperature;
			return this;
		}

		public Builder withTopP(Float topP) {
			this.options.textGenerationConfig.topP = topP;
			return this;
		}

		public Builder withMaxTokenCount(Integer maxTokenCount) {
			this.options.textGenerationConfig.maxTokenCount = maxTokenCount;
			return this;
		}

		public Builder withStopSequences(List<String> stopSequences) {
			this.options.textGenerationConfig.stopSequences = stopSequences;
			return this;
		}

		public BedrockTitanChatOptions build() {
			return this.options;
		}

	}

	@JsonIgnore
	public Float getTemperature() {
		return this.textGenerationConfig.temperature;
	}

	public void setTemperature(Float temperature) {
		this.textGenerationConfig.temperature = temperature;
	}

	@JsonIgnore
	public Float getTopP() {
		return this.textGenerationConfig.topP;
	}

	public void setTopP(Float topP) {
		this.textGenerationConfig.topP = topP;
	}

	public Integer getMaxTokenCount() {
		return this.textGenerationConfig.maxTokenCount;
	}

	@JsonIgnore
	public void setMaxTokenCount(Integer maxTokenCount) {
		this.textGenerationConfig.maxTokenCount = maxTokenCount;
	}

	@JsonIgnore
	public List<String> getStopSequences() {
		return this.textGenerationConfig.stopSequences;
	}

	public void setStopSequences(List<String> stopSequences) {
		this.textGenerationConfig.stopSequences = stopSequences;
	}

	@Override
	@JsonIgnore
	public Integer getTopK() {
		throw new UnsupportedOperationException("Bedrock Titan Chat does not support the 'TopK' option.");
	}

	public void setTopK(Integer topK) {
		throw new UnsupportedOperationException("Bedrock Titan Chat does not support the 'TopK' option.'");
	}

	public static BedrockTitanChatOptions fromOptions(BedrockTitanChatOptions fromOptions) {
		return builder().withTemperature(fromOptions.getTemperature())
			.withTopP(fromOptions.getTopP())
			.withMaxTokenCount(fromOptions.getMaxTokenCount())
			.withStopSequences(fromOptions.getStopSequences())
			.build();
	}

}
