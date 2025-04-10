/*
 * Copyright 2024-2025 the original author or authors.
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

package org.springframework.ai.model.function;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Default implementation of {@link FunctionCallingOptions}.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @deprecated in favor of {@link DefaultToolCallingChatOptions}.
 */
@Deprecated
public class DefaultFunctionCallingOptions implements FunctionCallingOptions {

	private List<FunctionCallback> functionCallbacks = new ArrayList<>();

	private Set<String> functions = new HashSet<>();

	private Boolean proxyToolCalls = false;

	private Map<String, Object> context = new HashMap<>();

	private String model;

	private Double frequencyPenalty;

	private Integer maxTokens;

	private Double presencePenalty;

	private List<String> stopSequences;

	private Double temperature;

	private Integer topK;

	private Double topP;

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
		return this.stopSequences != null ? Collections.unmodifiableList(this.stopSequences) : null;
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
	@SuppressWarnings("unchecked")
	public <T extends ChatOptions> T copy() {
		DefaultFunctionCallingOptions copy = new DefaultFunctionCallingOptions();
		copy.setModel(this.getModel());
		copy.setFrequencyPenalty(this.getFrequencyPenalty());
		copy.setMaxTokens(this.getMaxTokens());
		copy.setPresencePenalty(this.getPresencePenalty());
		copy.setStopSequences(this.getStopSequences() != null ? new ArrayList<>(this.getStopSequences()) : null);
		copy.setTemperature(this.getTemperature());
		copy.setTopK(this.getTopK());
		copy.setTopP(this.getTopP());
		copy.setFunctions(new HashSet<>(this.functions));
		copy.setFunctionCallbacks(new ArrayList<>(this.functionCallbacks));
		copy.setProxyToolCalls(this.proxyToolCalls);
		copy.setToolContext(new HashMap<>(this.getToolContext()));
		return (T) copy;
	}

	public FunctionCallingOptions merge(ChatOptions options) {
		Builder builder = FunctionCallingOptions.builder();
		builder.model(StringUtils.hasText(options.getModel()) ? options.getModel() : this.getModel())
			.frequencyPenalty(
					options.getFrequencyPenalty() != null ? options.getFrequencyPenalty() : this.getFrequencyPenalty())
			.maxTokens(options.getMaxTokens() != null ? options.getMaxTokens() : this.getMaxTokens())
			.presencePenalty(
					options.getPresencePenalty() != null ? options.getPresencePenalty() : this.getPresencePenalty())
			.stopSequences(options.getStopSequences() != null ? options.getStopSequences() : this.getStopSequences())
			.temperature(options.getTemperature() != null ? options.getTemperature() : this.getTemperature())
			.topK(options.getTopK() != null ? options.getTopK() : this.getTopK())
			.topP(options.getTopP() != null ? options.getTopP() : this.getTopP());

		// Try to get function-specific properties if options is a FunctionCallingOptions
		if (options instanceof FunctionCallingOptions functionOptions) {
			builder.proxyToolCalls(functionOptions.getProxyToolCalls() != null ? functionOptions.getProxyToolCalls()
					: this.proxyToolCalls);

			Set<String> functions = new HashSet<>();
			if (!CollectionUtils.isEmpty(this.functions)) {
				functions.addAll(this.functions);
			}
			if (!CollectionUtils.isEmpty(functionOptions.getFunctions())) {
				functions.addAll(functionOptions.getFunctions());
			}
			builder.functions(functions);

			List<FunctionCallback> functionCallbacks = new ArrayList<>();
			if (!CollectionUtils.isEmpty(this.functionCallbacks)) {
				functionCallbacks.addAll(this.functionCallbacks);
			}
			if (!CollectionUtils.isEmpty(functionOptions.getFunctionCallbacks())) {
				functionCallbacks.addAll(functionOptions.getFunctionCallbacks());
			}
			builder.functionCallbacks(functionCallbacks);

			Map<String, Object> context = new HashMap<>();
			if (!CollectionUtils.isEmpty(this.context)) {
				context.putAll(this.context);
			}
			if (!CollectionUtils.isEmpty(functionOptions.getToolContext())) {
				context.putAll(functionOptions.getToolContext());
			}
			builder.toolContext(context);
		}
		else {
			// If not a FunctionCallingOptions, preserve current function-specific
			// properties
			builder.proxyToolCalls(this.proxyToolCalls);
			builder.functions(new HashSet<>(this.functions));
			builder.functionCallbacks(new ArrayList<>(this.functionCallbacks));
			builder.toolContext(new HashMap<>(this.context));
		}

		return builder.build();
	}

}
