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

	private final DefaultFunctionCallingOptions functionCallingOptions = new DefaultFunctionCallingOptions();

	public FunctionCallingOptions.Builder model(String model) {
		this.functionCallingOptions.setModel(model);
		return this;
	}

	public FunctionCallingOptions.Builder frequencyPenalty(Double frequencyPenalty) {
		this.functionCallingOptions.setFrequencyPenalty(frequencyPenalty);
		return this;
	}

	public FunctionCallingOptions.Builder maxTokens(Integer maxTokens) {
		this.functionCallingOptions.setMaxTokens(maxTokens);
		return this;
	}

	public FunctionCallingOptions.Builder presencePenalty(Double presencePenalty) {
		this.functionCallingOptions.setPresencePenalty(presencePenalty);
		return this;
	}

	public FunctionCallingOptions.Builder stopSequences(List<String> stopSequences) {
		this.functionCallingOptions.setStopSequences(stopSequences);
		return this;
	}

	public FunctionCallingOptions.Builder temperature(Double temperature) {
		this.functionCallingOptions.setTemperature(temperature);
		return this;
	}

	public FunctionCallingOptions.Builder topK(Integer topK) {
		this.functionCallingOptions.setTopK(topK);
		return this;
	}

	public FunctionCallingOptions.Builder topP(Double topP) {
		this.functionCallingOptions.setTopP(topP);
		return this;
	}

	public FunctionCallingOptions.Builder functionCallbacks(List<FunctionCallback> functionCallbacks) {
		this.functionCallingOptions.setFunctionCallbacks(functionCallbacks);
		return this;
	}

	public FunctionCallingOptions.Builder functionCallbacks(FunctionCallback... functionCallbacks) {
		Assert.notNull(functionCallbacks, "FunctionCallbacks must not be null");
		this.functionCallingOptions.setFunctionCallbacks(List.of(functionCallbacks));
		return this;
	}

	public FunctionCallingOptions.Builder functions(Set<String> functions) {
		this.functionCallingOptions.setFunctions(functions);
		return this;
	}

	public FunctionCallingOptions.Builder function(String function) {
		Assert.notNull(function, "Function must not be null");
		var set = new HashSet<>(this.functionCallingOptions.getFunctions());
		set.add(function);
		this.functionCallingOptions.setFunctions(set);
		return this;
	}

	public FunctionCallingOptions.Builder proxyToolCalls(Boolean proxyToolCalls) {
		this.functionCallingOptions.setProxyToolCalls(proxyToolCalls);
		return this;
	}

	public FunctionCallingOptions.Builder toolContext(Map<String, Object> context) {
		Assert.notNull(context, "Tool context must not be null");
		Map<String, Object> newContext = new HashMap<>(this.functionCallingOptions.getToolContext());
		newContext.putAll(context);
		this.functionCallingOptions.setToolContext(newContext);
		return this;
	}

	public FunctionCallingOptions.Builder toolContext(String key, Object value) {
		Assert.notNull(key, "Key must not be null");
		Assert.notNull(value, "Value must not be null");
		Map<String, Object> newContext = new HashMap<>(this.functionCallingOptions.getToolContext());
		newContext.put(key, value);
		this.functionCallingOptions.setToolContext(newContext);
		return this;
	}

	public FunctionCallingOptions build() {
		return this.functionCallingOptions;
	}

}
