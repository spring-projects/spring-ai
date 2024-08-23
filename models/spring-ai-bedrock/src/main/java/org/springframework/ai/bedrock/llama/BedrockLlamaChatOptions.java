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
package org.springframework.ai.bedrock.llama;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.chat.prompt.ChatOptions;

import java.util.List;

/**
 * @author Christian Tzolov
 * @author Thomas Vitale
 */
@JsonInclude(Include.NON_NULL)
public class BedrockLlamaChatOptions implements ChatOptions {

	/**
	 * The temperature value controls the randomness of the generated text. Use a lower
	 * value to decrease randomness in the response.
	 */
	private @JsonProperty("temperature") Float temperature;

	/**
	 * The topP value controls the diversity of the generated text. Use a lower value to
	 * ignore less probable options. Set to 0 or 1.0 to disable.
	 */
	private @JsonProperty("top_p") Float topP;

	/**
	 * The maximum length of the generated text.
	 */
	private @JsonProperty("max_gen_len") Integer maxGenLen;

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private BedrockLlamaChatOptions options = new BedrockLlamaChatOptions();

		public Builder withTemperature(Float temperature) {
			this.options.setTemperature(temperature);
			return this;
		}

		public Builder withTopP(Float topP) {
			this.options.setTopP(topP);
			return this;
		}

		public Builder withMaxGenLen(Integer maxGenLen) {
			this.options.setMaxGenLen(maxGenLen);
			return this;
		}

		public BedrockLlamaChatOptions build() {
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
	@JsonIgnore
	public Integer getMaxTokens() {
		return getMaxGenLen();
	}

	@JsonIgnore
	public void setMaxTokens(Integer maxTokens) {
		setMaxGenLen(maxTokens);
	}

	public Integer getMaxGenLen() {
		return this.maxGenLen;
	}

	public void setMaxGenLen(Integer maxGenLen) {
		this.maxGenLen = maxGenLen;
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
	@JsonIgnore
	public List<String> getStopSequences() {
		return null;
	}

	@Override
	@JsonIgnore
	public Integer getTopK() {
		return null;
	}

	@Override
	public BedrockLlamaChatOptions copy() {
		return fromOptions(this);
	}

	public static BedrockLlamaChatOptions fromOptions(BedrockLlamaChatOptions fromOptions) {
		return builder().withTemperature(fromOptions.getTemperature())
			.withTopP(fromOptions.getTopP())
			.withMaxGenLen(fromOptions.getMaxGenLen())
			.build();
	}

}
