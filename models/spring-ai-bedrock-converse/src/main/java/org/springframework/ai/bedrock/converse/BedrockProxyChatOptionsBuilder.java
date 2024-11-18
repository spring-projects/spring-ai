/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.bedrock.converse;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.util.Assert;

/**
 * Builder for {@link FunctionCallingOptions}. Using the {@link FunctionCallingOptions}
 * permits options portability between different AI providers that support
 * function-calling.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 0.8.1
 */
public class BedrockProxyChatOptionsBuilder {

	private BedrockProxyChatOptions options;

	BedrockProxyChatOptionsBuilder() {
		this.options = new BedrockProxyChatOptions();
	}

	public BedrockProxyChatOptionsBuilder functionCallbacks(List<FunctionCallback> functionCallbacks) {
		this.options.setFunctionCallbacks(functionCallbacks);
		return this;
	}

	public BedrockProxyChatOptionsBuilder functionCallback(FunctionCallback functionCallback) {
		Assert.notNull(functionCallback, "FunctionCallback must not be null");
		this.options.getFunctionCallbacks().add(functionCallback);
		return this;
	}

	public BedrockProxyChatOptionsBuilder functions(Set<String> functions) {
		this.options.setFunctions(functions);
		return this;
	}

	public BedrockProxyChatOptionsBuilder function(String function) {
		Assert.notNull(function, "Function must not be null");
		var set = new HashSet<>(this.options.getFunctions());
		set.add(function);
		this.options.setFunctions(set);
		return this;
	}

	public BedrockProxyChatOptionsBuilder model(String model) {
		this.options.setModel(model);
		return this;
	}

	public BedrockProxyChatOptionsBuilder frequencyPenalty(Double frequencyPenalty) {
		this.options.setFrequencyPenalty(frequencyPenalty);
		return this;
	}

	public BedrockProxyChatOptionsBuilder maxTokens(Integer maxTokens) {
		this.options.setMaxTokens(maxTokens);
		return this;
	}

	public BedrockProxyChatOptionsBuilder presencePenalty(Double presencePenalty) {
		this.options.setPresencePenalty(presencePenalty);
		return this;
	}

	public BedrockProxyChatOptionsBuilder stopSequences(List<String> stopSequences) {
		this.options.setStopSequences(stopSequences);
		return this;
	}

	public BedrockProxyChatOptionsBuilder temperature(Double temperature) {
		this.options.setTemperature(temperature);
		return this;
	}

	public BedrockProxyChatOptionsBuilder topK(Integer topK) {
		this.options.setTopK(topK);
		return this;
	}

	public BedrockProxyChatOptionsBuilder topP(Double topP) {
		this.options.setTopP(topP);
		return this;
	}

	public BedrockProxyChatOptionsBuilder proxyToolCalls(Boolean proxyToolCalls) {
		this.options.setProxyToolCalls(proxyToolCalls);
		return this;
	}

	public BedrockProxyChatOptionsBuilder toolContext(Map<String, Object> context) {
		Assert.notNull(context, "Tool context must not be null");
		Map<String, Object> newContext = new HashMap<>(this.options.getToolContext());
		newContext.putAll(context);
		this.options.setToolContext(newContext);
		return this;
	}

	public BedrockProxyChatOptionsBuilder toolContext(String key, Object value) {
		Assert.notNull(key, "Key must not be null");
		Assert.notNull(value, "Value must not be null");
		Map<String, Object> newContext = new HashMap<>(this.options.getToolContext());
		newContext.put(key, value);
		this.options.setToolContext(newContext);
		return this;
	}

	public BedrockProxyChatOptionsBuilder additional(Map<String, Object> additional) {
		Assert.notNull(additional, "Additional must not be null");
		this.options.setAdditional(additional);
		return this;
	}

	public BedrockProxyChatOptionsBuilder additional(String key, Object value) {
		Assert.notNull(key, "Key must not be null");
		Assert.notNull(value, "Value must not be null");
		Map<String, Object> newAdditional = new HashMap<>(this.options.getAdditional());
		newAdditional.put(key, value);
		this.options.setAdditional(newAdditional);
		return this;
	}

	public BedrockProxyChatOptions build() {
		return this.options;
	}

}
