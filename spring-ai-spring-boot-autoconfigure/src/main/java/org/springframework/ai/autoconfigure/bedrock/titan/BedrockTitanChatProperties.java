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

package org.springframework.ai.autoconfigure.bedrock.titan;

import java.util.List;

import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi.TitanChatModel;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bedrock Titan Chat autoconfiguration properties.
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
@ConfigurationProperties(BedrockTitanChatProperties.CONFIG_PREFIX)
public class BedrockTitanChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.bedrock.titan.chat";

	/**
	 * Enable Bedrock Titan Chat Client. False by default.
	 */
	private boolean enabled = false;

	/**
	 * Bedrock Titan Chat model name. Defaults to 'amazon.titan-text-express-v1'.
	 */
	private String model = TitanChatModel.TITAN_TEXT_EXPRESS_V1.id();

	/**
	 * (optional) Use a lower value to decrease randomness in the response. Defaults to
	 * 0.7.
	 */
	private Float temperature = 0.7f;

	/**
	 * (optional) The maximum cumulative probability of tokens to consider when sampling.
	 * The model uses combined Top-k and nucleus sampling. Nucleus sampling considers the
	 * smallest set of tokens whose probability sum is at least topP.
	 */
	private Float topP;

	/**
	 * (optional) Specify the maximum number of tokens to use in the generated response.
	 */
	private Integer maxTokenCount;

	/**
	 * (optional) Configure up to four sequences that the model recognizes. After a stop
	 * sequence, the model stops generating further tokens. The returned text doesn't
	 * contain the stop sequence.
	 */
	private List<String> stopSequences;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

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

	public Integer getMaxTokenCount() {
		return maxTokenCount;
	}

	public void setMaxTokenCount(Integer maxTokens) {
		this.maxTokenCount = maxTokens;
	}

	public List<String> getStopSequences() {
		return stopSequences;
	}

	public void setStopSequences(List<String> stopSequences) {
		this.stopSequences = stopSequences;
	}

}
