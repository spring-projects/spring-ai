/*
 * Copyright 2023-present the original author or authors.
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
	public Set<String> getToolNames() {
		return Set.copyOf(this.toolNames);
	}

	@Override
	public Map<String, Object> getToolContext() {
		return Map.copyOf(this.toolContext);
	}

	@Override
	public @Nullable Boolean getInternalToolExecutionEnabled() {
		return this.internalToolExecutionEnabled;
	}

	@Override
	public @Nullable String getModel() {
		return this.model;
	}

	@Override
	public @Nullable Double getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	@Override
	public @Nullable Integer getMaxTokens() {
		return this.maxTokens;
	}

	@Override
	public @Nullable Double getPresencePenalty() {
		return this.presencePenalty;
	}

	@Override
	public @Nullable List<String> getStopSequences() {
		return this.stopSequences;
	}

	@Override
	public @Nullable Double getTemperature() {
		return this.temperature;
	}

	@Override
	public @Nullable Integer getTopK() {
		return this.topK;
	}

	@Override
	public @Nullable Double getTopP() {
		return this.topP;
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

		protected @Nullable List<ToolCallback> toolCallbacks;

		protected @Nullable Set<String> toolNames;

		protected @Nullable Map<String, Object> toolContext;

		protected @Nullable Boolean internalToolExecutionEnabled;

		@Override
		public B clone() {
			B copy = super.clone();
			copy.toolCallbacks = this.toolCallbacks == null ? null : new ArrayList<>(this.toolCallbacks);
			copy.toolNames = this.toolNames == null ? null : new HashSet<>(this.toolNames);
			copy.toolContext = this.toolContext == null ? null : new HashMap<>(this.toolContext);
			return copy;
		}

		@Override
		public B toolCallbacks(@Nullable List<ToolCallback> toolCallbacks) {
			if (toolCallbacks != null) {
				this.toolCallbacks = new ArrayList<>(toolCallbacks);
			}
			else {
				this.toolCallbacks = null;
			}
			return self();
		}

		@Override
		public B toolCallbacks(ToolCallback... toolCallbacks) {
			Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
			if (this.toolCallbacks == null) {
				this.toolCallbacks = new ArrayList<>();
			}
			this.toolCallbacks.addAll(Arrays.asList(toolCallbacks));
			return self();
		}

		@Override
		public B toolNames(@Nullable Set<String> toolNames) {
			if (toolNames != null) {
				this.toolNames = new HashSet<>(toolNames);
			}
			else {
				this.toolNames = null;
			}
			return self();
		}

		@Override
		public B toolNames(String... toolNames) {
			Assert.notNull(toolNames, "toolNames cannot be null");
			if (this.toolNames == null) {
				this.toolNames = new HashSet<>();
			}
			this.toolNames.addAll(Set.of(toolNames));
			return self();
		}

		@Override
		public B toolContext(@Nullable Map<String, Object> context) {
			if (context != null) {
				if (this.toolContext == null) {
					this.toolContext = new HashMap<>();
				}
				this.toolContext.putAll(context);
			}
			else {
				this.toolContext = null;
			}
			return self();
		}

		@Override
		public B toolContext(String key, Object value) {
			Assert.hasText(key, "key cannot be null");
			Assert.notNull(value, "value cannot be null");
			if (this.toolContext == null) {
				this.toolContext = new HashMap<>();
			}
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
			if (this.toolCallbacks != null) {
				options.toolCallbacks = this.toolCallbacks;
			}
			if (this.toolNames != null) {
				options.toolNames = this.toolNames;
			}
			if (this.toolContext != null) {
				options.toolContext = this.toolContext;
			}
			options.internalToolExecutionEnabled = this.internalToolExecutionEnabled;

			options.model = this.model;
			options.frequencyPenalty = this.frequencyPenalty;
			options.maxTokens = this.maxTokens;
			options.presencePenalty = this.presencePenalty;
			options.stopSequences = this.stopSequences;
			options.temperature = this.temperature;
			options.topK = this.topK;
			options.topP = this.topP;
			return options;
		}

		@Override
		public B combineWith(ChatOptions.Builder<?> other) {
			super.combineWith(other);
			if (other instanceof Builder<?> that) {
				if (that.toolCallbacks != null) {
					this.toolCallbacks = new ArrayList<>(that.toolCallbacks);
				}
				if (that.toolNames != null) {
					this.toolNames = new HashSet<>(that.toolNames);
				}
				if (that.toolContext != null) {
					if (this.toolContext == null) {
						this.toolContext = new HashMap<>();
					}
					this.toolContext.putAll(that.toolContext); // TODO:replace instead of
																// merge?
				}
				if (that.internalToolExecutionEnabled != null) {
					this.internalToolExecutionEnabled = that.internalToolExecutionEnabled;
				}
			}
			return self();
		}

	}

}
