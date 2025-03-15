/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.model.tool;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default implementation of {@link ToolCallingChatOptions}.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class DefaultToolCallingChatOptions implements ToolCallingChatOptions {

	private List<FunctionCallback> toolCallbacks = new ArrayList<>();

	private Set<String> toolNames = new HashSet<>();

	private Map<String, Object> toolContext = new HashMap<>();

	@Nullable
	private Boolean internalToolExecutionEnabled;

	@Nullable
	private String model;

	@Nullable
	private Double frequencyPenalty;

	@Nullable
	private Integer maxTokens;

	@Nullable
	private Double presencePenalty;

	@Nullable
	private List<String> stopSequences;

	@Nullable
	private Double temperature;

	@Nullable
	private Integer topK;

	@Nullable
	private Double topP;

	@Override
	public List<FunctionCallback> getToolCallbacks() {
		return List.copyOf(this.toolCallbacks);
	}

	@Override
	public void setToolCallbacks(List<FunctionCallback> toolCallbacks) {
		Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
		Assert.noNullElements(toolCallbacks, "toolCallbacks cannot contain null elements");
		this.toolCallbacks = new ArrayList<>(toolCallbacks);
	}

	@Override
	public Set<String> getToolNames() {
		return Set.copyOf(this.toolNames);
	}

	@Override
	public void setToolNames(Set<String> toolNames) {
		Assert.notNull(toolNames, "toolNames cannot be null");
		Assert.noNullElements(toolNames, "toolNames cannot contain null elements");
		toolNames.forEach(toolName -> Assert.hasText(toolName, "toolNames cannot contain empty elements"));
		this.toolNames = new HashSet<>(toolNames);
	}

	@Override
	public Map<String, Object> getToolContext() {
		return Map.copyOf(this.toolContext);
	}

	@Override
	public void setToolContext(Map<String, Object> toolContext) {
		Assert.notNull(toolContext, "toolContext cannot be null");
		Assert.noNullElements(toolContext.keySet(), "toolContext cannot contain null keys");
		this.toolContext = new HashMap<>(toolContext);
	}

	@Override
	@Nullable
	public Boolean isInternalToolExecutionEnabled() {
		return this.internalToolExecutionEnabled;
	}

	@Override
	public void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
		this.internalToolExecutionEnabled = internalToolExecutionEnabled;
	}

	@Override
	public List<FunctionCallback> getFunctionCallbacks() {
		return getToolCallbacks();
	}

	@Override
	public void setFunctionCallbacks(List<FunctionCallback> functionCallbacks) {
		setToolCallbacks(functionCallbacks);
	}

	@Override
	public Set<String> getFunctions() {
		return getToolNames();
	}

	@Override
	public void setFunctions(Set<String> functions) {
		setToolNames(functions);
	}

	@Override
	@Nullable
	public Boolean getProxyToolCalls() {
		return isInternalToolExecutionEnabled() != null ? !isInternalToolExecutionEnabled() : null;
	}

	@Override
	public void setProxyToolCalls(@Nullable Boolean proxyToolCalls) {
		setInternalToolExecutionEnabled(proxyToolCalls == null || !proxyToolCalls);
	}

	@Override
	@Nullable
	public String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	@Override
	@Nullable
	public Double getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	public void setFrequencyPenalty(@Nullable Double frequencyPenalty) {
		this.frequencyPenalty = frequencyPenalty;
	}

	@Override
	@Nullable
	public Integer getMaxTokens() {
		return this.maxTokens;
	}

	public void setMaxTokens(@Nullable Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	@Override
	@Nullable
	public Double getPresencePenalty() {
		return this.presencePenalty;
	}

	public void setPresencePenalty(@Nullable Double presencePenalty) {
		this.presencePenalty = presencePenalty;
	}

	@Override
	@Nullable
	public List<String> getStopSequences() {
		return this.stopSequences;
	}

	public void setStopSequences(@Nullable List<String> stopSequences) {
		this.stopSequences = stopSequences;
	}

	@Override
	@Nullable
	public Double getTemperature() {
		return this.temperature;
	}

	public void setTemperature(@Nullable Double temperature) {
		this.temperature = temperature;
	}

	@Override
	@Nullable
	public Integer getTopK() {
		return this.topK;
	}

	public void setTopK(@Nullable Integer topK) {
		this.topK = topK;
	}

	@Override
	@Nullable
	public Double getTopP() {
		return this.topP;
	}

	public void setTopP(@Nullable Double topP) {
		this.topP = topP;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends ChatOptions> T copy() {
		DefaultToolCallingChatOptions options = new DefaultToolCallingChatOptions();
		options.setToolCallbacks(getToolCallbacks());
		options.setToolNames(getToolNames());
		options.setToolContext(getToolContext());
		options.setInternalToolExecutionEnabled(isInternalToolExecutionEnabled());
		options.setModel(getModel());
		options.setFrequencyPenalty(getFrequencyPenalty());
		options.setMaxTokens(getMaxTokens());
		options.setPresencePenalty(getPresencePenalty());
		options.setStopSequences(getStopSequences());
		options.setTemperature(getTemperature());
		options.setTopK(getTopK());
		options.setTopP(getTopP());
		return (T) options;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Default implementation of {@link ToolCallingChatOptions.Builder}.
	 */
	public static class Builder implements ToolCallingChatOptions.Builder {

		private final DefaultToolCallingChatOptions options = new DefaultToolCallingChatOptions();

		@Override
		public ToolCallingChatOptions.Builder toolCallbacks(List<FunctionCallback> toolCallbacks) {
			this.options.setToolCallbacks(toolCallbacks);
			return this;
		}

		@Override
		public ToolCallingChatOptions.Builder toolCallbacks(FunctionCallback... toolCallbacks) {
			Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
			this.options.setToolCallbacks(Arrays.asList(toolCallbacks));
			return this;
		}

		@Override
		public ToolCallingChatOptions.Builder toolNames(Set<String> toolNames) {
			this.options.setToolNames(toolNames);
			return this;
		}

		@Override
		public ToolCallingChatOptions.Builder toolNames(String... toolNames) {
			Assert.notNull(toolNames, "toolNames cannot be null");
			this.options.setToolNames(Set.of(toolNames));
			return this;
		}

		@Override
		public ToolCallingChatOptions.Builder toolContext(Map<String, Object> context) {
			this.options.setToolContext(context);
			return this;
		}

		@Override
		public ToolCallingChatOptions.Builder toolContext(String key, Object value) {
			Assert.hasText(key, "key cannot be null");
			Assert.notNull(value, "value cannot be null");
			Map<String, Object> updatedToolContext = new HashMap<>(this.options.getToolContext());
			updatedToolContext.put(key, value);
			this.options.setToolContext(updatedToolContext);
			return this;
		}

		@Override
		public ToolCallingChatOptions.Builder internalToolExecutionEnabled(
				@Nullable Boolean internalToolExecutionEnabled) {
			this.options.setInternalToolExecutionEnabled(internalToolExecutionEnabled);
			return this;
		}

		@Override
		@Deprecated // Use toolCallbacks() instead
		public ToolCallingChatOptions.Builder functionCallbacks(List<FunctionCallback> functionCallbacks) {
			return toolCallbacks(functionCallbacks);
		}

		@Override
		@Deprecated // Use toolCallbacks() instead
		public ToolCallingChatOptions.Builder functionCallbacks(FunctionCallback... functionCallbacks) {
			Assert.notNull(functionCallbacks, "functionCallbacks cannot be null");
			return functionCallbacks(List.of(functionCallbacks));
		}

		@Override
		@Deprecated // Use toolNames() instead
		public ToolCallingChatOptions.Builder functions(Set<String> functions) {
			return toolNames(functions);
		}

		@Override
		@Deprecated // Use toolNames() instead
		public ToolCallingChatOptions.Builder function(String function) {
			return toolNames(function);
		}

		@Override
		@Deprecated // Use internalToolExecutionEnabled() instead
		public ToolCallingChatOptions.Builder proxyToolCalls(@Nullable Boolean proxyToolCalls) {
			return internalToolExecutionEnabled(proxyToolCalls == null || !proxyToolCalls);
		}

		@Override
		public ToolCallingChatOptions.Builder model(@Nullable String model) {
			this.options.setModel(model);
			return this;
		}

		@Override
		public ToolCallingChatOptions.Builder frequencyPenalty(@Nullable Double frequencyPenalty) {
			this.options.setFrequencyPenalty(frequencyPenalty);
			return this;
		}

		@Override
		public ToolCallingChatOptions.Builder maxTokens(@Nullable Integer maxTokens) {
			this.options.setMaxTokens(maxTokens);
			return this;
		}

		@Override
		public ToolCallingChatOptions.Builder presencePenalty(@Nullable Double presencePenalty) {
			this.options.setPresencePenalty(presencePenalty);
			return this;
		}

		@Override
		public ToolCallingChatOptions.Builder stopSequences(@Nullable List<String> stopSequences) {
			this.options.setStopSequences(stopSequences);
			return this;
		}

		@Override
		public ToolCallingChatOptions.Builder temperature(@Nullable Double temperature) {
			this.options.setTemperature(temperature);
			return this;
		}

		@Override
		public ToolCallingChatOptions.Builder topK(@Nullable Integer topK) {
			this.options.setTopK(topK);
			return this;
		}

		@Override
		public ToolCallingChatOptions.Builder topP(@Nullable Double topP) {
			this.options.setTopP(topP);
			return this;
		}

		@Override
		public ToolCallingChatOptions build() {
			return this.options;
		}

	}

}
