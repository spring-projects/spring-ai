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

package org.springframework.ai.model.function;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.util.Assert;

/**
 * Default implementation of {@link FunctionCallingOptions.Builder}.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 */
public class DefaultFunctionCallingOptionsBuilder implements FunctionCallingOptions.Builder {

	private final DefaultFunctionCallingOptions options;

	public DefaultFunctionCallingOptionsBuilder() {
		this.options = new DefaultFunctionCallingOptions();
	}

	// Function calling specific methods
	@Override
	public FunctionCallingOptions.Builder functionCallbacks(List<FunctionCallback> functionCallbacks) {
		this.options.setFunctionCallbacks(functionCallbacks);
		return this;
	}

	@Override
	public FunctionCallingOptions.Builder functionCallbacks(FunctionCallback... functionCallbacks) {
		Assert.notNull(functionCallbacks, "FunctionCallbacks must not be null");
		this.options.setFunctionCallbacks(List.of(functionCallbacks));
		return this;
	}

	@Override
	public FunctionCallingOptions.Builder functions(Set<String> functions) {
		this.options.setFunctions(functions);
		return this;
	}

	@Override
	public FunctionCallingOptions.Builder function(String function) {
		Assert.notNull(function, "Function must not be null");
		var set = new HashSet<>(this.options.getFunctions());
		set.add(function);
		this.options.setFunctions(set);
		return this;
	}

	@Override
	public FunctionCallingOptions.Builder proxyToolCalls(Boolean proxyToolCalls) {
		this.options.setProxyToolCalls(proxyToolCalls);
		return this;
	}

	@Override
	public FunctionCallingOptions.Builder toolContext(Map<String, Object> context) {
		Assert.notNull(context, "Tool context must not be null");
		Map<String, Object> newContext = new HashMap<>(this.options.getToolContext());
		newContext.putAll(context);
		this.options.setToolContext(newContext);
		return this;
	}

	@Override
	public FunctionCallingOptions.Builder toolContext(String key, Object value) {
		Assert.notNull(key, "Key must not be null");
		Assert.notNull(value, "Value must not be null");
		Map<String, Object> newContext = new HashMap<>(this.options.getToolContext());
		newContext.put(key, value);
		this.options.setToolContext(newContext);
		return this;
	}

	// ChatOptions.Builder methods with covariant return type
	@Override
	public FunctionCallingOptions.Builder model(String model) {
		this.options.setModel(model);
		return this;
	}

	@Override
	public FunctionCallingOptions.Builder frequencyPenalty(Double frequencyPenalty) {
		this.options.setFrequencyPenalty(frequencyPenalty);
		return this;
	}

	@Override
	public FunctionCallingOptions.Builder maxTokens(Integer maxTokens) {
		this.options.setMaxTokens(maxTokens);
		return this;
	}

	@Override
	public FunctionCallingOptions.Builder presencePenalty(Double presencePenalty) {
		this.options.setPresencePenalty(presencePenalty);
		return this;
	}

	@Override
	public FunctionCallingOptions.Builder stopSequences(List<String> stop) {
		this.options.setStopSequences(stop);
		return this;
	}

	@Override
	public FunctionCallingOptions.Builder temperature(Double temperature) {
		this.options.setTemperature(temperature);
		return this;
	}

	@Override
	public FunctionCallingOptions.Builder topK(Integer topK) {
		this.options.setTopK(topK);
		return this;
	}

	@Override
	public FunctionCallingOptions.Builder topP(Double topP) {
		this.options.setTopP(topP);
		return this;
	}

	@Override
	public FunctionCallingOptions build() {
		return this.options.copy();
	}

}
