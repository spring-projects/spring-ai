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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Default implementation of {@link FunctionCallingOptions}.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 */
public class DefaultFunctionCallingOptions extends DefaultChatOptions implements FunctionCallingOptions {

	private List<FunctionCallback> functionCallbacks = new ArrayList<>();

	private Set<String> functions = new HashSet<>();

	private Boolean proxyToolCalls = false;

	private Map<String, Object> context = new HashMap<>();

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
	public FunctionCallingOptions copy() {
		return FunctionCallingOptions.builder()
			.model(this.getModel())
			.frequencyPenalty(this.getFrequencyPenalty())
			.maxTokens(this.getMaxTokens())
			.presencePenalty(this.getPresencePenalty())
			.stopSequences(this.getStopSequences() != null ? new ArrayList<>(this.getStopSequences()) : null)
			.temperature(this.getTemperature())
			.topK(this.getTopK())
			.topP(this.getTopP())
			.functions(new HashSet<>(this.functions))
			.functionCallbacks(new ArrayList<>(this.functionCallbacks))
			.proxyToolCalls(this.proxyToolCalls)
			.toolContext(new HashMap<>(this.getToolContext()))
			.build();
	}

	public FunctionCallingOptions merge(FunctionCallingOptions options) {

		var builder = FunctionCallingOptions.builder()
			.model(StringUtils.hasText(options.getModel()) ? options.getModel() : this.getModel())
			.frequencyPenalty(
					options.getFrequencyPenalty() != null ? options.getFrequencyPenalty() : this.getFrequencyPenalty())
			.maxTokens(options.getMaxTokens() != null ? options.getMaxTokens() : this.getMaxTokens())
			.presencePenalty(
					options.getPresencePenalty() != null ? options.getPresencePenalty() : this.getPresencePenalty())
			.stopSequences(options.getStopSequences() != null ? options.getStopSequences() : this.getStopSequences())
			.temperature(options.getTemperature() != null ? options.getTemperature() : this.getTemperature())
			.topK(options.getTopK() != null ? options.getTopK() : this.getTopK())
			.topP(options.getTopP() != null ? options.getTopP() : this.getTopP());

		builder.proxyToolCalls(options.getProxyToolCalls() != null ? options.getProxyToolCalls() : this.proxyToolCalls);

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

		return builder.build();
	}

	public FunctionCallingOptions merge(ChatOptions options) {

		var builder = FunctionCallingOptions.builder()
			.model(StringUtils.hasText(options.getModel()) ? options.getModel() : this.getModel())
			.frequencyPenalty(
					options.getFrequencyPenalty() != null ? options.getFrequencyPenalty() : this.getFrequencyPenalty())
			.maxTokens(options.getMaxTokens() != null ? options.getMaxTokens() : this.getMaxTokens())
			.presencePenalty(
					options.getPresencePenalty() != null ? options.getPresencePenalty() : this.getPresencePenalty())
			.stopSequences(options.getStopSequences() != null ? options.getStopSequences() : this.getStopSequences())
			.temperature(options.getTemperature() != null ? options.getTemperature() : this.getTemperature())
			.topK(options.getTopK() != null ? options.getTopK() : this.getTopK())
			.topP(options.getTopP() != null ? options.getTopP() : this.getTopP());

		return builder.build();
	}

}
