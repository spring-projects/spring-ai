/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.vertexai.palm2;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * @author Christian Tzolov
 */
@JsonInclude(Include.NON_NULL)
public class VertexAiChatOptions implements ChatOptions {

	// @formatter:off
	/**
	 * Controls the randomness of the output. Values can range over [0.0,1.0], inclusive.
	 * A value closer to 1.0 will produce responses that are more varied, while a value
	 * closer to 0.0 will typically result in less surprising responses from the
	 * generative. This value specifies default to be used by the backend while making the
	 * call to the generative.
	 */
	private @JsonProperty("temperature") Float temperature;

	/**
	 * The number of generated response messages to return. This value must be between [1,
	 * 8], inclusive. Defaults to 1.
	 */
	private @JsonProperty("candidateCount") Integer candidateCount;

	/**
	 * The maximum cumulative probability of tokens to consider when sampling. The
	 * generative uses combined Top-k and nucleus sampling. Nucleus sampling considers the
	 * smallest set of tokens whose probability sum is at least topP.
	 */
	private @JsonProperty("topP") Float topP;

	/**
	 * The maximum number of tokens to consider when sampling. The generative uses
	 * combined Top-k and nucleus sampling. Top-k sampling considers the set of topK most
	 * probable tokens.
	 */
	private @JsonProperty("topK") Integer topK;
	// @formatter:on

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private VertexAiChatOptions options = new VertexAiChatOptions();

		public Builder withTemperature(Float temperature) {
			this.options.temperature = temperature;
			return this;
		}

		public Builder withCandidateCount(Integer candidateCount) {
			this.options.candidateCount = candidateCount;
			return this;
		}

		public Builder withTopP(Float topP) {
			this.options.topP = topP;
			return this;
		}

		public Builder withTopK(Integer topK) {
			this.options.topK = topK;
			return this;
		}

		public VertexAiChatOptions build() {
			return this.options;
		}

	}

	@Override
	public Float getTemperature() {
		return this.temperature;
	}

	@Override
	public void setTemperature(Float temperature) {
		this.temperature = temperature;
	}

	public Integer getCandidateCount() {
		return this.candidateCount;
	}

	public void setCandidateCount(Integer candidateCount) {
		this.candidateCount = candidateCount;
	}

	@Override
	public Float getTopP() {
		return this.topP;
	}

	@Override
	public void setTopP(Float topP) {
		this.topP = topP;
	}

	@Override
	public Integer getTopK() {
		return this.topK;
	}

	@Override
	public void setTopK(Integer topK) {
		this.topK = topK;
	}

}
