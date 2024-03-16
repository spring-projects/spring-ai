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
package org.springframework.ai.autoconfigure.openai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest.ResponseFormat;
import org.springframework.ai.openai.api.OpenAiApi.FunctionTool;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class OpenAiChatOptionsProperties implements OpenAiChatOptions {

	public static final String DEFAULT_CHAT_MODEL = "gpt-3.5-turbo";

	private static final Double DEFAULT_TEMPERATURE = 0.7;

	private String model = DEFAULT_CHAT_MODEL;

	private Float temperature = DEFAULT_TEMPERATURE.floatValue();

	private Float frequencyPenalty;

	private ResponseFormat responseFormat;

	private Map<String, Integer> logitBias;

	private Integer maxTokens;

	private Integer n;

	private Float presencePenalty;

	private Integer seed;

	private String toolChoice;

	private String user;

	private Float topP;

	@NestedConfigurationProperty
	private List<FunctionTool> tools;

	@NestedConfigurationProperty
	private List<String> stop;

	@NestedConfigurationProperty
	private List<FunctionCallback> functionCallbacks = new ArrayList<>();

	@NestedConfigurationProperty
	private Set<String> functions = new HashSet<>();

	@Override
	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	@Override
	public Float getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	public void setFrequencyPenalty(Float frequencyPenalty) {
		this.frequencyPenalty = frequencyPenalty;
	}

	@Override
	public Map<String, Integer> getLogitBias() {
		return this.logitBias;
	}

	public void setLogitBias(Map<String, Integer> logitBias) {
		this.logitBias = logitBias;
	}

	@Override
	public Integer getMaxTokens() {
		return this.maxTokens;
	}

	public void setMaxTokens(Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	@Override
	public Integer getN() {
		return this.n;
	}

	public void setN(Integer n) {
		this.n = n;
	}

	@Override
	public Float getPresencePenalty() {
		return this.presencePenalty;
	}

	public void setPresencePenalty(Float presencePenalty) {
		this.presencePenalty = presencePenalty;
	}

	@Override
	public ResponseFormat getResponseFormat() {
		return this.responseFormat;
	}

	public void setResponseFormat(ResponseFormat responseFormat) {
		this.responseFormat = responseFormat;
	}

	@Override
	public Integer getSeed() {
		return this.seed;
	}

	public void setSeed(Integer seed) {
		this.seed = seed;
	}

	@Override
	public List<String> getStop() {
		return this.stop;
	}

	public void setStop(List<String> stop) {
		this.stop = stop;
	}

	@Override
	public List<FunctionTool> getTools() {
		return this.tools;
	}

	public void setTools(List<FunctionTool> tools) {
		this.tools = tools;
	}

	@Override
	public String getToolChoice() {
		return this.toolChoice;
	}

	public void setToolChoice(String toolChoice) {
		this.toolChoice = toolChoice;
	}

	@Override
	public String getUser() {
		return this.user;
	}

	public void setUser(String user) {
		this.user = user;
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
	public List<FunctionCallback> getFunctionCallbacks() {
		return this.functionCallbacks;
	}

	@Override
	public Set<String> getFunctions() {
		return this.functions;
	}

	public void setFunctions(Set<String> functions) {
		this.functions = functions;
	}

	@Override
	public Integer getTopK() {
		return null;
	}

}
