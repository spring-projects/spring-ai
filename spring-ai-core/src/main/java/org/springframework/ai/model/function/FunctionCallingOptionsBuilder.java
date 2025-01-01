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

package org.springframework.ai.model.function;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Builder for {@link FunctionCallingOptions}. Using the {@link FunctionCallingOptions}
 * permits options portability between different AI providers that support
 * function-calling.
 *
 * @deprecated Use {@link FunctionCallingOptions.Builder} instead.
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 0.8.1
 */
@Deprecated(forRemoval = true, since = "1.0.0-M5")
public class FunctionCallingOptionsBuilder {

	private final PortableFunctionCallingOptions options;

	public FunctionCallingOptionsBuilder() {
		this.options = new PortableFunctionCallingOptions();
	}

	public FunctionCallingOptionsBuilder withFunctionCallbacks(List<FunctionCallback> functionCallbacks) {
		this.options.setFunctionCallbacks(functionCallbacks);
		return this;
	}

	public FunctionCallingOptionsBuilder withFunctionCallbacks(FunctionCallback... functionCallbacks) {
		Assert.notNull(functionCallbacks, "FunctionCallbacks must not be null");
		this.options.setFunctionCallbacks(List.of(functionCallbacks));
		return this;
	}

	public FunctionCallingOptionsBuilder withFunctions(Set<String> functions) {
		this.options.setFunctions(functions);
		return this;
	}

	public FunctionCallingOptionsBuilder withFunction(String function) {
		Assert.notNull(function, "Function must not be null");
		var set = new HashSet<>(this.options.getFunctions());
		set.add(function);
		this.options.setFunctions(set);
		return this;
	}

	public FunctionCallingOptionsBuilder withModel(String model) {
		this.options.setModel(model);
		return this;
	}

	public FunctionCallingOptionsBuilder withFrequencyPenalty(Double frequencyPenalty) {
		this.options.setFrequencyPenalty(frequencyPenalty);
		return this;
	}

	public FunctionCallingOptionsBuilder withMaxTokens(Integer maxTokens) {
		this.options.setMaxTokens(maxTokens);
		return this;
	}

	public FunctionCallingOptionsBuilder withPresencePenalty(Double presencePenalty) {
		this.options.setPresencePenalty(presencePenalty);
		return this;
	}

	public FunctionCallingOptionsBuilder withStopSequences(List<String> stopSequences) {
		this.options.setStopSequences(stopSequences);
		return this;
	}

	public FunctionCallingOptionsBuilder withTemperature(Double temperature) {
		this.options.setTemperature(temperature);
		return this;
	}

	public FunctionCallingOptionsBuilder withTopK(Integer topK) {
		this.options.setTopK(topK);
		return this;
	}

	public FunctionCallingOptionsBuilder withTopP(Double topP) {
		this.options.setTopP(topP);
		return this;
	}

	public FunctionCallingOptionsBuilder withProxyToolCalls(Boolean proxyToolCalls) {
		this.options.setProxyToolCalls(proxyToolCalls);
		return this;
	}

	public FunctionCallingOptionsBuilder withToolContext(Map<String, Object> context) {
		Assert.notNull(context, "Tool context must not be null");
		Map<String, Object> newContext = new HashMap<>(this.options.getToolContext());
		newContext.putAll(context);
		this.options.setToolContext(newContext);
		return this;
	}

	public FunctionCallingOptionsBuilder withToolContext(String key, Object value) {
		Assert.notNull(key, "Key must not be null");
		Assert.notNull(value, "Value must not be null");
		Map<String, Object> newContext = new HashMap<>(this.options.getToolContext());
		newContext.put(key, value);
		this.options.setToolContext(newContext);
		return this;
	}

	public PortableFunctionCallingOptions build() {
		return this.options;
	}

	/**
	 * @deprecated use {@link DefaultFunctionCallingOptions} instead.
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public static class PortableFunctionCallingOptions implements FunctionCallingOptions {

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

		public static FunctionCallingOptionsBuilder builder() {
			return new FunctionCallingOptionsBuilder();
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

		@Override
		public ChatOptions copy() {
			return new FunctionCallingOptionsBuilder().withModel(this.model)
				.withFrequencyPenalty(this.frequencyPenalty)
				.withMaxTokens(this.maxTokens)
				.withPresencePenalty(this.presencePenalty)
				.withStopSequences(this.stopSequences != null ? new ArrayList<>(this.stopSequences) : null)
				.withTemperature(this.temperature)
				.withTopK(this.topK)
				.withTopP(this.topP)
				.withFunctions(new HashSet<>(this.functions))
				.withFunctionCallbacks(new ArrayList<>(this.functionCallbacks))
				.withProxyToolCalls(this.proxyToolCalls)
				.withToolContext(new HashMap<>(this.getToolContext()))
				.build();
		}

		public PortableFunctionCallingOptions merge(FunctionCallingOptions options) {

			var builder = PortableFunctionCallingOptions.builder()
				.withModel(StringUtils.hasText(options.getModel()) ? options.getModel() : this.model)
				.withFrequencyPenalty(
						options.getFrequencyPenalty() != null ? options.getFrequencyPenalty() : this.frequencyPenalty)
				.withMaxTokens(options.getMaxTokens() != null ? options.getMaxTokens() : this.maxTokens)
				.withPresencePenalty(
						options.getPresencePenalty() != null ? options.getPresencePenalty() : this.presencePenalty)
				.withStopSequences(options.getStopSequences() != null ? options.getStopSequences() : this.stopSequences)
				.withTemperature(options.getTemperature() != null ? options.getTemperature() : this.temperature)
				.withTopK(options.getTopK() != null ? options.getTopK() : this.topK)
				.withTopP(options.getTopP() != null ? options.getTopP() : this.topP)
				.withProxyToolCalls(
						options.getProxyToolCalls() != null ? options.getProxyToolCalls() : this.proxyToolCalls);

			Set<String> functions = new HashSet<>();
			if (!CollectionUtils.isEmpty(this.functions)) {
				functions.addAll(this.functions);
			}
			if (!CollectionUtils.isEmpty(options.getFunctions())) {
				functions.addAll(options.getFunctions());
			}
			builder.withFunctions(functions);

			List<FunctionCallback> functionCallbacks = new ArrayList<>();
			if (!CollectionUtils.isEmpty(this.functionCallbacks)) {
				functionCallbacks.addAll(this.functionCallbacks);
			}
			if (!CollectionUtils.isEmpty(options.getFunctionCallbacks())) {
				functionCallbacks.addAll(options.getFunctionCallbacks());
			}
			builder.withFunctionCallbacks(functionCallbacks);

			Map<String, Object> context = new HashMap<>();
			if (!CollectionUtils.isEmpty(this.context)) {
				context.putAll(this.context);
			}
			if (!CollectionUtils.isEmpty(options.getToolContext())) {
				context.putAll(options.getToolContext());
			}
			builder.withToolContext(context);

			return builder.build();
		}

		public PortableFunctionCallingOptions merge(ChatOptions options) {

			var builder = PortableFunctionCallingOptions.builder()
				.withModel(StringUtils.hasText(options.getModel()) ? options.getModel() : this.model)
				.withFrequencyPenalty(
						options.getFrequencyPenalty() != null ? options.getFrequencyPenalty() : this.frequencyPenalty)
				.withMaxTokens(options.getMaxTokens() != null ? options.getMaxTokens() : this.maxTokens)
				.withPresencePenalty(
						options.getPresencePenalty() != null ? options.getPresencePenalty() : this.presencePenalty)
				.withStopSequences(options.getStopSequences() != null ? options.getStopSequences() : this.stopSequences)
				.withTemperature(options.getTemperature() != null ? options.getTemperature() : this.temperature)
				.withTopK(options.getTopK() != null ? options.getTopK() : this.topK)
				.withTopP(options.getTopP() != null ? options.getTopP() : this.topP);

			return builder.build();
		}

	}

}
