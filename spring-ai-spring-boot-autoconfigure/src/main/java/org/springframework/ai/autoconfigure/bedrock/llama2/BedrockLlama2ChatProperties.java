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

package org.springframework.ai.autoconfigure.bedrock.llama2;

import org.springframework.ai.bedrock.llama2.api.Llama2ChatBedrockApi.Llama2ChatCompletionModel;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Bedrock Llama2.
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
@ConfigurationProperties(BedrockLlama2ChatProperties.CONFIG_PREFIX)
public class BedrockLlama2ChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.bedrock.llama2.chat";

	/**
	 * Enable Bedrock Llama2 chat client. Disabled by default.
	 */
	private boolean enabled = false;

	/**
	 * Controls the randomness of the output. Values can range over [0.0,1.0], inclusive.
	 * A value closer to 1.0 will produce responses that are more varied, while a value
	 * closer to 0.0 will typically result in less surprising responses from the model.
	 * This value specifies default to be used by the backend while making the call to the
	 * model.
	 */
	private Float temperature = 0.7f;

	/**
	 * The maximum cumulative probability of tokens to consider when sampling. The model
	 * uses combined Top-k and nucleus sampling. Nucleus sampling considers the smallest
	 * set of tokens whose probability sum is at least topP.
	 */
	private Float topP = null;

	/**
	 * Specify the maximum number of tokens to use in the generated response. The model
	 * truncates the response once the generated text exceeds maxGenLen.
	 */
	private Integer maxGenLen = 300;

	/**
	 * The model id to use. See the {@link Llama2ChatCompletionModel} for the supported
	 * models.
	 */
	private String model = Llama2ChatCompletionModel.LLAMA2_70B_CHAT_V1.id();

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

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

	public Integer getMaxGenLen() {
		return this.maxGenLen;
	}

	public void setMaxGenLen(Integer topK) {
		this.maxGenLen = topK;
	}

}
