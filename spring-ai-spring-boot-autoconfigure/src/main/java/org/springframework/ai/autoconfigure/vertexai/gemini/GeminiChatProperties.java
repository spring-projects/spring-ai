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

package org.springframework.ai.autoconfigure.vertexai.gemini;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Jingzhou Ou
 */
@ConfigurationProperties(GeminiChatProperties.CONFIG_PREFIX)
public class GeminiChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vertex.ai.gemini.chat";

	/**
	 * Controls the randomness of the output. Values can range over [0.0,1.0], inclusive.
	 * A value closer to 1.0 will produce responses that are more varied, while a value
	 * closer to 0.0 will typically result in less surprising responses from the model.
	 * This value specifies default to be used by the backend while making the call to the
	 * model.
	 */
	private Float temperature;

	/**
	 * The maximum cumulative probability of tokens to consider when sampling. The model
	 * uses combined Top-k and nucleus sampling. Nucleus sampling considers the smallest
	 * set of tokens whose probability sum is at least topP.
	 */
	private Float topP;

	/**
	 * The number of generated response messages to return.
	 */
	private Integer maxOutputTokens;

	/**
	 * The maximum number of tokens to consider when sampling. The model uses combined
	 * Top-k and nucleus sampling. Top-k sampling considers the set of topK most probable
	 * tokens.
	 */
	private Integer topK;

	public Float getTemperature() {
		return temperature;
	}

	public void setTemperature(Float temperature) {
		this.temperature = temperature;
	}

	public Float getTopP() {
		return topP;
	}

	public void setTopP(Float topP) {
		this.topP = topP;
	}

	public Integer getMaxOutputTokens() {
		return maxOutputTokens;
	}

	public void setMaxOutputTokens(Integer maxOutputTokens) {
		this.maxOutputTokens = maxOutputTokens;
	}

	public Integer getTopK() {
		return topK;
	}

	public void setTopK(Integer topK) {
		this.topK = topK;
	}

}
