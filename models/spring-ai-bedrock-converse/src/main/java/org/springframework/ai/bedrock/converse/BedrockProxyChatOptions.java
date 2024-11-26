/*
* Copyright 2024 - 2024 the original author or authors.
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
package org.springframework.ai.bedrock.converse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class BedrockProxyChatOptions implements FunctionCallingOptions {

	private List<FunctionCallback> functionCallbacks = new ArrayList<>();

	private Set<String> functions = new HashSet<>();

	private String model;

	private Double frequencyPenalty;

	private Integer maxTokens;

	private Double presencePenalty;

	private List<String> stopSequences;

	private Double temperature;

	private Integer topK;

	private Double topP;

	private Boolean proxyToolCalls = false;

	private Map<String, Object> context = new HashMap<>();

	private Map<String, Object> additional = new HashMap<>();

	public static BedrockProxyChatOptionsBuilder builder() {
		return new BedrockProxyChatOptionsBuilder();
	}

	@Override
	public List<FunctionCallback> getFunctionCallbacks() {
		return Collections.unmodifiableList(this.functionCallbacks);
	}

	public void setFunctionCallbacks(List<FunctionCallback> functionCallbacks) {
		Assert.notNull(functionCallbacks, "FunctionCallbacks must not be null");
		this.functionCallbacks = new ArrayList<>(functionCallbacks);
	}

	@Override
	public Set<String> getFunctions() {
		return Collections.unmodifiableSet(this.functions);
	}

	public void setFunctions(Set<String> functions) {
		Assert.notNull(functions, "Functions must not be null");
		this.functions = new HashSet<>(functions);
	}

	@Override
	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	@Override
	public Double getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	public void setFrequencyPenalty(Double frequencyPenalty) {
		this.frequencyPenalty = frequencyPenalty;
	}

	@Override
	public Integer getMaxTokens() {
		return this.maxTokens;
	}

	public void setMaxTokens(Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	@Override
	public Double getPresencePenalty() {
		return this.presencePenalty;
	}

	public void setPresencePenalty(Double presencePenalty) {
		this.presencePenalty = presencePenalty;
	}

	@Override
	public List<String> getStopSequences() {
		return this.stopSequences;
	}

	public void setStopSequences(List<String> stopSequences) {
		this.stopSequences = stopSequences;
	}

	@Override
	public Double getTemperature() {
		return this.temperature;
	}

	public void setTemperature(Double temperature) {
		this.temperature = temperature;
	}

	@Override
	public Integer getTopK() {
		return this.topK;
	}

	public void setTopK(Integer topK) {
		this.topK = topK;
	}

	@Override
	public Double getTopP() {
		return this.topP;
	}

	public void setTopP(Double topP) {
		this.topP = topP;
	}

	@Override
	public Boolean getProxyToolCalls() {
		return this.proxyToolCalls;
	}

	public void setProxyToolCalls(Boolean proxyToolCalls) {
		this.proxyToolCalls = proxyToolCalls;
	}

	public Map<String, Object> getToolContext() {
		return Collections.unmodifiableMap(this.context);
	}

	public void setToolContext(Map<String, Object> context) {
		Assert.notNull(context, "Context must not be null");
		this.context = new HashMap<>(context);
	}

	public Map<String, Object> getAdditional() {
		return Collections.unmodifiableMap(this.additional);
	}

	public void setAdditional(Map<String, Object> additional) {
		Assert.notNull(additional, "Additional must not be null");
		this.additional = new HashMap<>(additional);
	}

	@Override
	public ChatOptions copy() {
		return new BedrockProxyChatOptionsBuilder().model(this.model)
			.frequencyPenalty(this.frequencyPenalty)
			.maxTokens(this.maxTokens)
			.presencePenalty(this.presencePenalty)
			.stopSequences(this.stopSequences != null ? new ArrayList<>(this.stopSequences) : null)
			.temperature(this.temperature)
			.topK(this.topK)
			.topP(this.topP)
			.functions(new HashSet<>(this.functions))
			.functionCallbacks(new ArrayList<>(this.functionCallbacks))
			.proxyToolCalls(this.proxyToolCalls)
			.toolContext(new HashMap<>(this.getToolContext()))
			.additional(new HashMap<>(this.additional))
			.build();
	}

	public BedrockProxyChatOptions merge(FunctionCallingOptions options) {

		var builder = builder().model(StringUtils.hasText(options.getModel()) ? options.getModel() : this.model)
			.frequencyPenalty(
					options.getFrequencyPenalty() != null ? options.getFrequencyPenalty() : this.frequencyPenalty)
			.maxTokens(options.getMaxTokens() != null ? options.getMaxTokens() : this.maxTokens)
			.presencePenalty(options.getPresencePenalty() != null ? options.getPresencePenalty() : this.presencePenalty)
			.stopSequences(options.getStopSequences() != null ? options.getStopSequences() : this.stopSequences)
			.temperature(options.getTemperature() != null ? options.getTemperature() : this.temperature)
			.topK(options.getTopK() != null ? options.getTopK() : this.topK)
			.topP(options.getTopP() != null ? options.getTopP() : this.topP)
			.proxyToolCalls(options.getProxyToolCalls() != null ? options.getProxyToolCalls() : this.proxyToolCalls);

		Set<String> functions = new HashSet<>();
		if (!CollectionUtils.isEmpty(this.functions)) {
			functions.addAll(this.functions);
		}
		if (!CollectionUtils.isEmpty(options.getFunctions())) {
			functions.addAll(options.getFunctions());
		}
		builder.functions(functions);

		List<FunctionCallback> functionCallbacks = new ArrayList<>();
		if (!CollectionUtils.isEmpty(this.functionCallbacks)) {
			functionCallbacks.addAll(this.functionCallbacks);
		}
		if (!CollectionUtils.isEmpty(options.getFunctionCallbacks())) {
			functionCallbacks.addAll(options.getFunctionCallbacks());
		}
		builder.functionCallbacks(functionCallbacks);

		Map<String, Object> context = new HashMap<>();
		if (!CollectionUtils.isEmpty(this.context)) {
			context.putAll(this.context);
		}
		if (!CollectionUtils.isEmpty(options.getToolContext())) {
			context.putAll(options.getToolContext());
		}
		builder.toolContext(context);

		Map<String, Object> additional = new HashMap<>();
		if (!CollectionUtils.isEmpty(this.additional)) {
			context.putAll(this.additional);
		}

		if (options instanceof BedrockProxyChatOptions bedrockProxyChatOptions) {
			if (!CollectionUtils.isEmpty(bedrockProxyChatOptions.getAdditional())) {
				additional.putAll(bedrockProxyChatOptions.getAdditional());
			}
		}
		builder.additional(additional);

		return builder.build();
	}

	public BedrockProxyChatOptions merge(ChatOptions options) {

		var builder = BedrockProxyChatOptions.builder()
			.model(StringUtils.hasText(options.getModel()) ? options.getModel() : this.model)
			.frequencyPenalty(
					options.getFrequencyPenalty() != null ? options.getFrequencyPenalty() : this.frequencyPenalty)
			.maxTokens(options.getMaxTokens() != null ? options.getMaxTokens() : this.maxTokens)
			.presencePenalty(options.getPresencePenalty() != null ? options.getPresencePenalty() : this.presencePenalty)
			.stopSequences(options.getStopSequences() != null ? options.getStopSequences() : this.stopSequences)
			.temperature(options.getTemperature() != null ? options.getTemperature() : this.temperature)
			.topK(options.getTopK() != null ? options.getTopK() : this.topK)
			.topP(options.getTopP() != null ? options.getTopP() : this.topP);

		return builder.build();
	}

}
