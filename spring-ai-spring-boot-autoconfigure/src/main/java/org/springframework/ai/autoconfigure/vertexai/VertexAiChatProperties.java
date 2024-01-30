/*
 * Copyright 2023 the original author or authors.
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

package org.springframework.ai.autoconfigure.vertexai;

import org.springframework.ai.vertex.api.VertexAiApi;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(VertexAiChatProperties.CONFIG_PREFIX)
public class VertexAiChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vertex.ai.chat";

	/**
	 * Controls the randomness of the output. Values can range over [0.0,1.0], inclusive.
	 * A value closer to 1.0 will produce responses that are more varied, while a value
	 * closer to 0.0 will typically result in less surprising responses from the
	 * generative. This value specifies default to be used by the backend while making the
	 * call to the generative.
	 */
	private Float temperature = 0.7f;

	/**
	 * The maximum cumulative probability of tokens to consider when sampling. The
	 * generative uses combined Top-k and nucleus sampling. Nucleus sampling considers the
	 * smallest set of tokens whose probability sum is at least topP.
	 */
	private Float topP = null;

	/**
	 * The number of generated response messages to return. This value must be between [1,
	 * 8], inclusive. Defaults to 1.
	 */
	private Integer candidateCount = 1;

	/**
	 * The maximum number of tokens to consider when sampling. The generative uses
	 * combined Top-k and nucleus sampling. Top-k sampling considers the set of topK most
	 * probable tokens.
	 */
	private Integer topK = 20;

	/**
	 * Vertex AI PaLM API generative name. Defaults to chat-bison-001
	 */
	private String model = VertexAiApi.DEFAULT_GENERATE_MODEL;

	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public Float getTemperature() {
		return this.temperature;
	}

	public void setTemperature(Float temperature) {
		this.temperature = temperature;
	}

	public Float getTopP() {
		return this.topP;
	}

	public void setTopP(Float topP) {
		this.topP = topP;
	}

	public Integer getCandidateCount() {
		return this.candidateCount;
	}

	public void setCandidateCount(Integer candidateCount) {
		this.candidateCount = candidateCount;
	}

	public Integer getTopK() {
		return this.topK;
	}

	public void setTopK(Integer topK) {
		this.topK = topK;
	}

}
