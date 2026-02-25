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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.DefaultChatOptionsBuilder;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;

/**
 * Default implementation of {@link ToolCallingChatOptions}.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class DefaultToolCallingChatOptions implements ToolCallingChatOptions {

	private List<ToolCallback> toolCallbacks = new ArrayList<>();

	private Set<String> toolNames = new HashSet<>();

	private Map<String, Object> toolContext = new HashMap<>();

	private @Nullable Boolean internalToolExecutionEnabled;

	private @Nullable String model;

	private @Nullable Double frequencyPenalty;

	private @Nullable Integer maxTokens;

	private @Nullable Double presencePenalty;

	private @Nullable List<String> stopSequences;

	private @Nullable Double temperature;

	private @Nullable Integer topK;

	private @Nullable Double topP;

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
	public @Nullable Boolean getInternalToolExecutionEnabled() {
		return this.internalToolExecutionEnabled;
	}

	@Override
	public void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
		this.internalToolExecutionEnabled = internalToolExecutionEnabled;
	}

	@Override
	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	@Override
	public @Nullable Double getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	public void setFrequencyPenalty(@Nullable Double frequencyPenalty) {
		this.frequencyPenalty = frequencyPenalty;
	}

	@Override
	public @Nullable Integer getMaxTokens() {
		return this.maxTokens;
	}

	public void setMaxTokens(@Nullable Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	@Override
	public @Nullable Double getPresencePenalty() {
		return this.presencePenalty;
	}

	public void setPresencePenalty(@Nullable Double presencePenalty) {
		this.presencePenalty = presencePenalty;
	}

	@Override
	public @Nullable List<String> getStopSequences() {
		return this.stopSequences;
	}

	public void setStopSequences(@Nullable List<String> stopSequences) {
		this.stopSequences = stopSequences;
	}

	@Override
	public @Nullable Double getTemperature() {
		return this.temperature;
	}

	public void setTemperature(@Nullable Double temperature) {
		this.temperature = temperature;
	}

	@Override
	public @Nullable Integer getTopK() {
		return this.topK;
	}

	public void setTopK(@Nullable Integer topK) {
		this.topK = topK;
	}

	@Override
	public @Nullable Double getTopP() {
		return this.topP;
	}

	public void setTopP(@Nullable Double topP) {
		this.topP = topP;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends ChatOptions> T copy() {
		return (T) mutate().build();
	}

	@Override
	public ToolCallingChatOptions.Builder<?> mutate() {
		return DefaultToolCallingChatOptions.builder()
			.model(getModel())
			.frequencyPenalty(getFrequencyPenalty())
			.maxTokens(getMaxTokens())
			.presencePenalty(getPresencePenalty())
			.stopSequences(getStopSequences())
			.temperature(getTemperature())
			.topK(getTopK())
			.topP(getTopP())
			.toolCallbacks(getToolCallbacks())
			.toolNames(getToolNames())
			.toolContext(getToolContext())
			.internalToolExecutionEnabled(getInternalToolExecutionEnabled());
	}

	public static Builder<?> builder() {
		return new Builder<>();
	}

	/**
	 * Default implementation of {@link ToolCallingChatOptions.Builder}.
	 */
	public static class Builder<B extends Builder<B>> extends DefaultChatOptionsBuilder<B>
			implements ToolCallingChatOptions.Builder<B> {

		protected List<ToolCallback> toolCallbacks = new ArrayList<>();

		protected Set<String> toolNames = new HashSet<>();

		protected Map<String, Object> toolContext = new HashMap<>();

		protected @Nullable Boolean internalToolExecutionEnabled;

		@Override
		public B toolCallbacks(@Nullable List<ToolCallback> toolCallbacks) {
			if (toolCallbacks == null) {
				this.toolCallbacks.clear();
			}
			else {
				this.toolCallbacks = new ArrayList<>(toolCallbacks);
			}
			return self();
		}

		@Override
		public B toolCallbacks(ToolCallback... toolCallbacks) {
			Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
			this.toolCallbacks.addAll(Arrays.asList(toolCallbacks));
			return self();
		}

		@Override
		public B toolNames(@Nullable Set<String> toolNames) {
			if (toolNames != null) {
				this.toolNames = toolNames;
			}
			else {
				this.toolNames.clear();
			}
			return self();
		}

		@Override
		public B toolNames(String... toolNames) {
			Assert.notNull(toolNames, "toolNames cannot be null");
			this.toolNames.addAll(Set.of(toolNames));
			return self();
		}

		@Override
		public B toolContext(@Nullable Map<String, Object> context) {
			if (context != null) {
				this.toolContext.putAll(context);
			}
			else {
				this.toolContext.clear();
			}
			return self();
		}

		@Override
		public B toolContext(String key, Object value) {
			Assert.hasText(key, "key cannot be null");
			Assert.notNull(value, "value cannot be null");
			this.toolContext.put(key, value);
			return self();
		}

		@Override
		public B internalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
			this.internalToolExecutionEnabled = internalToolExecutionEnabled;
			return self();
		}

		@Override
		public ToolCallingChatOptions build() {
			DefaultToolCallingChatOptions options = new DefaultToolCallingChatOptions();
			options.setToolCallbacks(this.toolCallbacks);
			options.setToolNames(this.toolNames);
			options.setToolContext(this.toolContext);
			options.setInternalToolExecutionEnabled(this.internalToolExecutionEnabled);

			options.setModel(this.model);
			options.setFrequencyPenalty(this.frequencyPenalty);
			options.setMaxTokens(this.maxTokens);
			options.setPresencePenalty(this.presencePenalty);
			options.setStopSequences(this.stopSequences);
			options.setTemperature(this.temperature);
			options.setTopK(this.topK);
			options.setTopP(this.topP);
			return options;
		}

		@Override
		public B combineWith(ChatOptions.Builder<?> other) {
			super.combineWith(other);
			if (other instanceof Builder<?> options) {
				this.toolCallbacks = options.toolCallbacks;
				this.toolNames = options.toolNames;
				this.toolContext.putAll(options.toolContext);
				if (options.internalToolExecutionEnabled != null) {
					this.internalToolExecutionEnabled = options.internalToolExecutionEnabled;
				}
			}
			return self();
		}

	}

}
