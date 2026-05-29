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
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.ai.chat.prompt.DefaultChatOptionsBuilder;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;

/**
 * Default implementation of {@link ToolCallingChatOptions}.
 *
 * @author Thomas Vitale
 * @author Sebastien Deleuze
 * @since 1.0.0
 */
public class DefaultToolCallingChatOptions extends DefaultChatOptions implements ToolCallingChatOptions {

	private final List<ToolCallback> toolCallbacks;

	private final Set<String> toolNames;

	private final Map<String, Object> toolContext;

	private final @Nullable Boolean internalToolExecutionEnabled;

	protected DefaultToolCallingChatOptions(@Nullable List<ToolCallback> toolCallbacks, @Nullable Set<String> toolNames,
			@Nullable Map<String, Object> toolContext, @Nullable Boolean internalToolExecutionEnabled,
			@Nullable String model, @Nullable Double frequencyPenalty, @Nullable Integer maxTokens,
			@Nullable Double presencePenalty, @Nullable List<String> stopSequences, @Nullable Double temperature,
			@Nullable Integer topK, @Nullable Double topP) {
		super(model, frequencyPenalty, maxTokens, presencePenalty, stopSequences, temperature, topK, topP);
		this.toolCallbacks = toolCallbacks != null ? List.copyOf(toolCallbacks) : List.of();
		this.toolNames = toolNames != null ? Set.copyOf(toolNames) : Set.of();
		this.toolContext = toolContext != null ? Map.copyOf(toolContext) : Map.of();
		this.internalToolExecutionEnabled = internalToolExecutionEnabled;
	}

	@Override
	public List<ToolCallback> getToolCallbacks() {
		return this.toolCallbacks;
	}

	@Override
	public Set<String> getToolNames() {
		return this.toolNames;
	}

	@Override
	public Map<String, Object> getToolContext() {
		return this.toolContext;
	}

	@Override
	public @Nullable Boolean getInternalToolExecutionEnabled() {
		return this.internalToolExecutionEnabled;
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

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		if (!super.equals(o)) {
			return false;
		}
		DefaultToolCallingChatOptions that = (DefaultToolCallingChatOptions) o;
		return Objects.equals(this.toolCallbacks, that.toolCallbacks) && Objects.equals(this.toolNames, that.toolNames)
				&& Objects.equals(this.toolContext, that.toolContext)
				&& Objects.equals(this.internalToolExecutionEnabled, that.internalToolExecutionEnabled);
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), this.toolCallbacks, this.toolNames, this.toolContext,
				this.internalToolExecutionEnabled);
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
			return new DefaultToolCallingChatOptions(this.toolCallbacks, this.toolNames, this.toolContext,
					this.internalToolExecutionEnabled, this.model, this.frequencyPenalty, this.maxTokens,
					this.presencePenalty, this.stopSequences, this.temperature, this.topK, this.topP);
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
