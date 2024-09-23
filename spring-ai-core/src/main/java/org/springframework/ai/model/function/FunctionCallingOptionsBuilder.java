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
package org.springframework.ai.model.function;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.ai.chat.prompt.ChatOptions;
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
public class FunctionCallingOptionsBuilder {

	private final PortableFunctionCallingOptions options;

	public FunctionCallingOptionsBuilder() {
		this.options = new PortableFunctionCallingOptions();
	}

	public FunctionCallingOptionsBuilder withFunctionCallbacks(List<FunctionCallback> functionCallbacks) {
		this.options.setFunctionCallbacks(functionCallbacks);
		return this;
	}

	public FunctionCallingOptionsBuilder withFunctionCallback(FunctionCallback functionCallback) {
		Assert.notNull(functionCallback, "FunctionCallback must not be null");
		this.options.getFunctionCallbacks().add(functionCallback);
		return this;
	}

	public FunctionCallingOptionsBuilder withFunctions(Set<String> functions) {
		this.options.setFunctions(functions);
		return this;
	}

	public FunctionCallingOptionsBuilder withFunction(String function) {
		Assert.notNull(function, "Function must not be null");
		this.options.getFunctions().add(function);
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

	public PortableFunctionCallingOptions build() {
		return this.options;
	}

	public static class PortableFunctionCallingOptions implements FunctionCallingOptions, ChatOptions {

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

		public static FunctionCallingOptionsBuilder builder() {
			return new FunctionCallingOptionsBuilder();
		}

		@Override
		public List<FunctionCallback> getFunctionCallbacks() {
			return this.functionCallbacks;
		}

		public void setFunctionCallbacks(List<FunctionCallback> functionCallbacks) {
			Assert.notNull(functionCallbacks, "FunctionCallbacks must not be null");
			this.functionCallbacks = functionCallbacks;
		}

		@Override
		public Set<String> getFunctions() {
			return this.functions;
		}

		public void setFunctions(Set<String> functions) {
			Assert.notNull(functions, "Functions must not be null");
			this.functions = functions;
		}

		@Override
		public String getModel() {
			return model;
		}

		public void setModel(String model) {
			this.model = model;
		}

		@Override
		public Double getFrequencyPenalty() {
			return frequencyPenalty;
		}

		public void setFrequencyPenalty(Double frequencyPenalty) {
			this.frequencyPenalty = frequencyPenalty;
		}

		@Override
		public Integer getMaxTokens() {
			return maxTokens;
		}

		public void setMaxTokens(Integer maxTokens) {
			this.maxTokens = maxTokens;
		}

		@Override
		public Double getPresencePenalty() {
			return presencePenalty;
		}

		public void setPresencePenalty(Double presencePenalty) {
			this.presencePenalty = presencePenalty;
		}

		@Override
		public List<String> getStopSequences() {
			return stopSequences;
		}

		public void setStopSequences(List<String> stopSequences) {
			this.stopSequences = stopSequences;
		}

		@Override
		public Double getTemperature() {
			return temperature;
		}

		public void setTemperature(Double temperature) {
			this.temperature = temperature;
		}

		@Override
		public Integer getTopK() {
			return topK;
		}

		public void setTopK(Integer topK) {
			this.topK = topK;
		}

		@Override
		public Double getTopP() {
			return topP;
		}

		public void setTopP(Double topP) {
			this.topP = topP;
		}

		@Override
		public Boolean getProxyToolCalls() {
			return proxyToolCalls;
		}

		public void setProxyToolCalls(Boolean proxyToolCalls) {
			this.proxyToolCalls = proxyToolCalls;
		}

		@Override
		public ChatOptions copy() {
			return new FunctionCallingOptionsBuilder().withModel(this.model)
				.withFrequencyPenalty(this.frequencyPenalty)
				.withMaxTokens(this.maxTokens)
				.withPresencePenalty(this.presencePenalty)
				.withStopSequences(this.stopSequences)
				.withTemperature(this.temperature)
				.withTopK(this.topK)
				.withTopP(this.topP)
				.withFunctions(this.functions)
				.withFunctionCallbacks(this.functionCallbacks)
				.withProxyToolCalls(this.proxyToolCalls)
				.build();
		}

	}

}
