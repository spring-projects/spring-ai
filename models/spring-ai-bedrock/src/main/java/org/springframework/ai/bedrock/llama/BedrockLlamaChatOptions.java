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

package org.springframework.ai.bedrock.llama;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * Options for the Bedrock Llama Chat API.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 */
@JsonInclude(Include.NON_NULL)
public class BedrockLlamaChatOptions implements ChatOptions {

	/**
	 * The temperature value controls the randomness of the generated text. Use a lower
	 * value to decrease randomness in the response.
	 */
	private @JsonProperty("temperature") Double temperature;

	/**
	 * The topP value controls the diversity of the generated text. Use a lower value to
	 * ignore less probable options. Set to 0 or 1.0 to disable.
	 */
	private @JsonProperty("top_p") Double topP;

	/**
	 * The maximum length of the generated text.
	 */
	private @JsonProperty("max_gen_len") Integer maxGenLen;

	public static Builder builder() {
		return new Builder();
	}

	public static BedrockLlamaChatOptions fromOptions(BedrockLlamaChatOptions fromOptions) {
		return builder().temperature(fromOptions.getTemperature())
			.topP(fromOptions.getTopP())
			.maxGenLen(fromOptions.getMaxGenLen())
			.build();
	}

	@Override
	public Double getTemperature() {
		return this.temperature;
	}

	public void setTemperature(Double temperature) {
		this.temperature = temperature;
	}

	@Override
	public Double getTopP() {
		return this.topP;
	}

	public void setTopP(Double topP) {
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
	public Double getFrequencyPenalty() {
		return null;
	}

	@Override
	@JsonIgnore
	public Double getPresencePenalty() {
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

	public static class Builder {

		private BedrockLlamaChatOptions options = new BedrockLlamaChatOptions();

		public Builder temperature(Double temperature) {
			this.options.setTemperature(temperature);
			return this;
		}

		public Builder topP(Double topP) {
			this.options.setTopP(topP);
			return this;
		}

		public Builder maxGenLen(Integer maxGenLen) {
			this.options.setMaxGenLen(maxGenLen);
			return this;
		}

		public BedrockLlamaChatOptions build() {
			return this.options;
		}

	}

}
