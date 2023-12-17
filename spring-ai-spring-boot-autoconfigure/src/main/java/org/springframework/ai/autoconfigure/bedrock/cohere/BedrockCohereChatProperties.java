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

package org.springframework.ai.autoconfigure.bedrock.cohere;

import java.util.List;

import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi.CohereChatRequest.ReturnLikelihoods;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi.CohereChatRequest.Truncate;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bedrock Cohere Chat autoconfiguration properties.
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
@ConfigurationProperties(BedrockCohereChatProperties.CONFIG_PREFIX)
public class BedrockCohereChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.bedrock.cohere.chat";

	/**
	 * Enable Bedrock Cohere Chat Client. False by default.
	 */
	private boolean enabled = false;

	/**
	 * Bedrock Cohere Chat model name. Defaults to 'cohere-command-v14'.
	 */
	private String model = CohereChatBedrockApi.CohereChatModel.COHERE_COMMAND_V14.id();

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
	 * (optional) Specify the number of token choices the model uses to generate the next
	 * token.
	 */
	private Integer topK;

	/**
	 * (optional) Specify the maximum number of tokens to use in the generated response.
	 */
	private Integer maxTokens;

	/**
	 * (optional) Configure up to four sequences that the model recognizes. After a stop
	 * sequence, the model stops generating further tokens. The returned text doesn't
	 * contain the stop sequence.
	 */
	private List<String> stopSequences;

	/**
	 * (optional) Specify how and if the token likelihoods are returned with the response.
	 */
	private ReturnLikelihoods returnLikelihoods;

	/**
	 * (optional) The maximum number of generations that the model should return.
	 */
	private Integer numGenerations;

	/**
	 * LogitBias prevents the model from generating unwanted tokens or incentivize the
	 * model to include desired tokens. The token likelihoods.
	 */
	private String logitBiasToken;

	/**
	 * LogitBias prevents the model from generating unwanted tokens or incentivize the
	 * model to include desired tokens. A float between -10 and 10.
	 */
	private Float logitBiasBias;

	/**
	 * (optional) Specifies how the API handles inputs longer than the maximum token
	 * length.
	 */
	private Truncate truncate;

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

	public Integer getTopK() {
		return topK;
	}

	public void setTopK(Integer topK) {
		this.topK = topK;
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

	public ReturnLikelihoods getReturnLikelihoods() {
		return returnLikelihoods;
	}

	public void setReturnLikelihoods(ReturnLikelihoods returnLikelihoods) {
		this.returnLikelihoods = returnLikelihoods;
	}

	public Integer getNumGenerations() {
		return numGenerations;
	}

	public void setNumGenerations(Integer numGenerations) {
		this.numGenerations = numGenerations;
	}

	public String getLogitBiasToken() {
		return logitBiasToken;
	}

	public void setLogitBiasToken(String logitBiasToken) {
		this.logitBiasToken = logitBiasToken;
	}

	public Float getLogitBiasBias() {
		return logitBiasBias;
	}

	public void setLogitBiasBias(Float logitBiasBias) {
		this.logitBiasBias = logitBiasBias;
	}

	public Truncate getTruncate() {
		return truncate;
	}

	public void setTruncate(Truncate truncate) {
		this.truncate = truncate;
	}

}
