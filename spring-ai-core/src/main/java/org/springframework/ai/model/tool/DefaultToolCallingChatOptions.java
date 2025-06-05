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
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
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

	private List<ToolCallback> toolCallbacks = new ArrayList<>();

	private Set<String> tools = new HashSet<>();

	private Map<String, Object> toolContext = new HashMap<>();

	@Nullable
	private Boolean toolCallReturnDirect;

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
	public List<ToolCallback> getToolCallbacks() {
		return List.copyOf(this.toolCallbacks);
	}

	@Override
	public void setToolCallbacks(List<ToolCallback> toolCallbacks) {
		Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
		Assert.noNullElements(toolCallbacks, "toolCallbacks cannot contain null elements");
		this.toolCallbacks = new ArrayList<>(toolCallbacks);
	}

	@Override
	public void setToolCallbacks(ToolCallback... toolCallbacks) {
		Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
		setToolCallbacks(List.of(toolCallbacks));
	}

	@Override
	public Set<String> getTools() {
		return Set.copyOf(this.tools);
	}

	@Override
	public void setTools(Set<String> tools) {
		Assert.notNull(tools, "tools cannot be null");
		Assert.noNullElements(tools, "tools cannot contain null elements");
		tools.forEach(tool -> Assert.hasText(tool, "tools cannot contain empty elements"));
		this.tools = new HashSet<>(tools);
	}

	@Override
	public void setTools(String... tools) {
		Assert.notNull(tools, "tools cannot be null");
		setTools(Set.of(tools));
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
	public Boolean getToolCallReturnDirect() {
		return this.toolCallReturnDirect;
	}

	@Override
	public void setToolCallReturnDirect(@Nullable Boolean toolCallReturnDirect) {
		this.toolCallReturnDirect = toolCallReturnDirect;
	}

	@Override
	public List<FunctionCallback> getFunctionCallbacks() {
		return getToolCallbacks().stream().map(FunctionCallback.class::cast).toList();
	}

	@Override
	public void setFunctionCallbacks(List<FunctionCallback> functionCallbacks) {
		throw new UnsupportedOperationException("Not supported. Call setToolCallbacks instead.");
	}

	@Override
	public Set<String> getFunctions() {
		return getTools();
	}

	@Override
	public void setFunctions(Set<String> functions) {
		setTools(functions);
	}

	@Override
	@Nullable
	public Boolean getProxyToolCalls() {
		return getToolCallReturnDirect();
	}

	@Override
	public void setProxyToolCalls(@Nullable Boolean proxyToolCalls) {
		setToolCallReturnDirect(proxyToolCalls != null && proxyToolCalls);
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
		options.setTools(getTools());
		options.setToolContext(getToolContext());
		options.setToolCallReturnDirect(getToolCallReturnDirect());
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

	/**
	 * Merge the given {@link ChatOptions} into this instance.
	 */
	public ToolCallingChatOptions merge(ChatOptions options) {
		ToolCallingChatOptions.Builder builder = ToolCallingChatOptions.builder();
		builder.model(StringUtils.hasText(options.getModel()) ? options.getModel() : this.getModel());
		builder.frequencyPenalty(
				options.getFrequencyPenalty() != null ? options.getFrequencyPenalty() : this.getFrequencyPenalty());
		builder.maxTokens(options.getMaxTokens() != null ? options.getMaxTokens() : this.getMaxTokens());
		builder.presencePenalty(
				options.getPresencePenalty() != null ? options.getPresencePenalty() : this.getPresencePenalty());
		builder.stopSequences(options.getStopSequences() != null ? new ArrayList<>(options.getStopSequences())
				: this.getStopSequences());
		builder.temperature(options.getTemperature() != null ? options.getTemperature() : this.getTemperature());
		builder.topK(options.getTopK() != null ? options.getTopK() : this.getTopK());
		builder.topP(options.getTopP() != null ? options.getTopP() : this.getTopP());

		if (options instanceof ToolCallingChatOptions toolOptions) {
			List<ToolCallback> toolCallbacks = new ArrayList<>(this.toolCallbacks);
			if (!CollectionUtils.isEmpty(toolOptions.getToolCallbacks())) {
				toolCallbacks.addAll(toolOptions.getToolCallbacks());
			}
			builder.toolCallbacks(toolCallbacks);

			Set<String> tools = new HashSet<>(this.tools);
			if (!CollectionUtils.isEmpty(toolOptions.getTools())) {
				tools.addAll(toolOptions.getTools());
			}
			builder.tools(tools);

			Map<String, Object> toolContext = new HashMap<>(this.toolContext);
			if (!CollectionUtils.isEmpty(toolOptions.getToolContext())) {
				toolContext.putAll(toolOptions.getToolContext());
			}
			builder.toolContext(toolContext);

			builder.toolCallReturnDirect(toolOptions.getToolCallReturnDirect() != null
					? toolOptions.getToolCallReturnDirect() : this.getToolCallReturnDirect());
		}
		else {
			builder.toolCallbacks(this.toolCallbacks);
			builder.tools(this.tools);
			builder.toolContext(this.toolContext);
			builder.toolCallReturnDirect(this.toolCallReturnDirect);
		}

		return builder.build();
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
		public ToolCallingChatOptions.Builder toolCallbacks(List<ToolCallback> toolCallbacks) {
			this.options.setToolCallbacks(toolCallbacks);
			return this;
		}

		@Override
		public ToolCallingChatOptions.Builder toolCallbacks(ToolCallback... toolCallbacks) {
			this.options.setToolCallbacks(toolCallbacks);
			return this;
		}

		@Override
		public ToolCallingChatOptions.Builder tools(Set<String> toolNames) {
			this.options.setTools(toolNames);
			return this;
		}

		@Override
		public ToolCallingChatOptions.Builder tools(String... toolNames) {
			this.options.setTools(toolNames);
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
		public ToolCallingChatOptions.Builder toolCallReturnDirect(@Nullable Boolean toolCallReturnDirect) {
			this.options.setToolCallReturnDirect(toolCallReturnDirect);
			return this;
		}

		@Override
		@Deprecated // Use toolCallbacks() instead
		public ToolCallingChatOptions.Builder functionCallbacks(List<FunctionCallback> functionCallbacks) {
			Assert.notNull(functionCallbacks, "functionCallbacks cannot be null");
			return toolCallbacks(functionCallbacks.stream().map(ToolCallback.class::cast).toList());
		}

		@Override
		@Deprecated // Use toolCallbacks() instead
		public ToolCallingChatOptions.Builder functionCallbacks(FunctionCallback... functionCallbacks) {
			Assert.notNull(functionCallbacks, "functionCallbacks cannot be null");
			return functionCallbacks(List.of(functionCallbacks));
		}

		@Override
		@Deprecated // Use tools() instead
		public ToolCallingChatOptions.Builder functions(Set<String> functions) {
			return tools(functions);
		}

		@Override
		@Deprecated // Use tools() instead
		public ToolCallingChatOptions.Builder function(String function) {
			return tools(function);
		}

		@Override
		@Deprecated // Use toolCallReturnDirect() instead
		public ToolCallingChatOptions.Builder proxyToolCalls(@Nullable Boolean proxyToolCalls) {
			return toolCallReturnDirect(proxyToolCalls != null && proxyToolCalls);
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
