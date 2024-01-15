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

package org.springframework.ai.autoconfigure.bedrock.anthropic;

import java.util.List;

import org.springframework.ai.bedrock.anthropic.api.AnthropicChatBedrockApi;
import org.springframework.ai.bedrock.anthropic.api.AnthropicChatBedrockApi.AnthropicChatModel;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Bedrock Anthropic.
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
@ConfigurationProperties(BedrockAnthropicChatProperties.CONFIG_PREFIX)
public class BedrockAnthropicChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.bedrock.anthropic.chat";

	/**
	 * Enable Bedrock Anthropic chat client. Disabled by default.
	 */
	private boolean enabled = false;

	/**
	 * The generative id to use. See the {@link AnthropicChatModel} for the supported
	 * models.
	 */
	private String model = AnthropicChatModel.CLAUDE_V2.id();

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
	 * Specify the maximum number of tokens to use in the generated response. Note that
	 * the models may stop before reaching this maximum. This parameter only specifies the
	 * absolute maximum number of tokens to generate. We recommend a limit of 4,000 tokens
	 * for optimal performance.
	 */
	private Integer maxTokensToSample = 300;

	/**
	 * Specify the number of token choices the generative uses to generate the next token.
	 */
	private Integer topK = 10;

	/**
	 * Configure up to four sequences that the generative recognizes. After a stop
	 * sequence, the generative stops generating further tokens. The returned text doesn't
	 * contain the stop sequence.
	 */
	private List<String> stopSequences = List.of("\n\nHuman:");

	/**
	 * The version of the generative to use. The default value is bedrock-2023-05-31.
	 */
	private String anthropicVersion = AnthropicChatBedrockApi.DEFAULT_ANTHROPIC_VERSION;

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

	public Integer getMaxTokensToSample() {
		return maxTokensToSample;
	}

	public void setMaxTokensToSample(Integer maxTokensToSample) {
		this.maxTokensToSample = maxTokensToSample;
	}

	public Integer getTopK() {
		return topK;
	}

	public void setTopK(Integer topK) {
		this.topK = topK;
	}

	public List<String> getStopSequences() {
		return stopSequences;
	}

	public void setStopSequences(List<String> stopSequences) {
		this.stopSequences = stopSequences;
	}

	public String getAnthropicVersion() {
		return anthropicVersion;
	}

	public void setAnthropicVersion(String anthropicVersion) {
		this.anthropicVersion = anthropicVersion;
	}

}
