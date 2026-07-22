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

package org.springframework.ai.deliverance;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

/**
 * Chat options for the Deliverance chat API.
 *
 * @author Edward Capriolo
 * @since 2.0.1
 */
public final class DeliveranceChatOptions implements ToolCallingChatOptions {

	private final @Nullable String model;

	private final @Nullable Double temperature;

	private final @Nullable Integer maxTokens;

	private final @Nullable Double topP;

	private final @Nullable Integer topK;

	private final @Nullable List<String> stopSequences;

	private final @Nullable Integer seed;

	private final @Nullable Boolean logprobs;

	private final @Nullable Integer topLogprobs;

	private final @Nullable Double xtcThreshold;

	private final @Nullable Double xtcProbability;

	private final @Nullable String guidedRegex;

	private final @Nullable String guidedJson;

	private final @Nullable List<ToolCallback> toolCallbacks;

	private final @Nullable Map<String, Object> toolContext;

	private DeliveranceChatOptions(Builder builder) {
		this.model = builder.model;
		this.temperature = builder.temperature;
		this.maxTokens = builder.maxTokens;
		this.topP = builder.topP;
		this.topK = builder.topK;
		this.stopSequences = builder.stopSequences != null ? List.copyOf(builder.stopSequences) : null;
		this.seed = builder.seed;
		this.logprobs = builder.logprobs;
		this.topLogprobs = builder.topLogprobs;
		this.xtcThreshold = builder.xtcThreshold;
		this.xtcProbability = builder.xtcProbability;
		this.guidedRegex = builder.guidedRegex;
		this.guidedJson = builder.guidedJson;
		this.toolCallbacks = builder.toolCallbacks != null ? List.copyOf(builder.toolCallbacks) : null;
		this.toolContext = builder.toolContext != null ? Map.copyOf(builder.toolContext) : null;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public @Nullable String getModel() {
		return this.model;
	}

	@Override
	public @Nullable Double getFrequencyPenalty() {
		return null;
	}

	@Override
	public @Nullable Integer getMaxTokens() {
		return this.maxTokens;
	}

	@Override
	public @Nullable Double getPresencePenalty() {
		return null;
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

	public @Nullable Integer getSeed() {
		return this.seed;
	}

	public @Nullable Boolean getLogprobs() {
		return this.logprobs;
	}

	public @Nullable Integer getTopLogprobs() {
		return this.topLogprobs;
	}

	public @Nullable Double getXtcThreshold() {
		return this.xtcThreshold;
	}

	public @Nullable Double getXtcProbability() {
		return this.xtcProbability;
	}

	public @Nullable String getGuidedRegex() {
		return this.guidedRegex;
	}

	public @Nullable String getGuidedJson() {
		return this.guidedJson;
	}

	@Override
	public @Nullable List<ToolCallback> getToolCallbacks() {
		return this.toolCallbacks;
	}

	@Override
	public @Nullable Map<String, Object> getToolContext() {
		return this.toolContext;
	}

	@Override
	public Builder mutate() {
		return builder().model(this.model)
			.temperature(this.temperature)
			.maxTokens(this.maxTokens)
			.topP(this.topP)
			.topK(this.topK)
			.stopSequences(this.stopSequences)
			.seed(this.seed)
			.logprobs(this.logprobs)
			.topLogprobs(this.topLogprobs)
			.xtcThreshold(this.xtcThreshold)
			.xtcProbability(this.xtcProbability)
			.guidedRegex(this.guidedRegex)
			.guidedJson(this.guidedJson)
			.toolCallbacks(this.toolCallbacks)
			.toolContext(this.toolContext);
	}

	public static final class Builder implements ToolCallingChatOptions.Builder<Builder> {

		private @Nullable String model;

		private @Nullable Double temperature;

		private @Nullable Integer maxTokens;

		private @Nullable Double topP;

		private @Nullable Integer topK;

		private @Nullable List<String> stopSequences;

		private @Nullable Integer seed;

		private @Nullable Boolean logprobs;

		private @Nullable Integer topLogprobs;

		private @Nullable Double xtcThreshold;

		private @Nullable Double xtcProbability;

		private @Nullable String guidedRegex;

		private @Nullable String guidedJson;

		private @Nullable List<ToolCallback> toolCallbacks;

		private @Nullable Map<String, Object> toolContext;

		@Override
		public Builder clone() {
			return build().mutate();
		}

		@Override
		public Builder model(@Nullable String model) {
			this.model = model;
			return this;
		}

		@Override
		public Builder frequencyPenalty(@Nullable Double frequencyPenalty) {
			return this;
		}

		@Override
		public Builder maxTokens(@Nullable Integer maxTokens) {
			this.maxTokens = maxTokens;
			return this;
		}

		@Override
		public Builder presencePenalty(@Nullable Double presencePenalty) {
			return this;
		}

		@Override
		public Builder stopSequences(@Nullable List<String> stopSequences) {
			this.stopSequences = stopSequences;
			return this;
		}

		@Override
		public Builder temperature(@Nullable Double temperature) {
			this.temperature = temperature;
			return this;
		}

		@Override
		public Builder topK(@Nullable Integer topK) {
			this.topK = topK;
			return this;
		}

		@Override
		public Builder topP(@Nullable Double topP) {
			this.topP = topP;
			return this;
		}

		public Builder seed(@Nullable Integer seed) {
			this.seed = seed;
			return this;
		}

		public Builder logprobs(@Nullable Boolean logprobs) {
			this.logprobs = logprobs;
			return this;
		}

		public Builder topLogprobs(@Nullable Integer topLogprobs) {
			this.topLogprobs = topLogprobs;
			return this;
		}

		public Builder xtcThreshold(@Nullable Double xtcThreshold) {
			this.xtcThreshold = xtcThreshold;
			return this;
		}

		public Builder xtcProbability(@Nullable Double xtcProbability) {
			this.xtcProbability = xtcProbability;
			return this;
		}

		public Builder guidedRegex(@Nullable String guidedRegex) {
			this.guidedRegex = guidedRegex;
			return this;
		}

		public Builder guidedJson(@Nullable String guidedJson) {
			this.guidedJson = guidedJson;
			return this;
		}

		@Override
		public Builder toolCallbacks(@Nullable List<ToolCallback> toolCallbacks) {
			this.toolCallbacks = toolCallbacks;
			return this;
		}

		@Override
		public Builder toolCallbacks(ToolCallback... toolCallbacks) {
			this.toolCallbacks = toolCallbacks != null ? List.of(toolCallbacks) : null;
			return this;
		}

		@Override
		public Builder toolContext(@Nullable Map<String, Object> context) {
			this.toolContext = context;
			return this;
		}

		@Override
		public Builder toolContext(String key, Object value) {
			this.toolContext = this.toolContext != null ? new java.util.HashMap<>(this.toolContext)
					: new java.util.HashMap<>();
			this.toolContext.put(key, value);
			return this;
		}

		@Override
		public DeliveranceChatOptions build() {
			return new DeliveranceChatOptions(this);
		}

		@Override
		public Builder combineWith(org.springframework.ai.chat.prompt.ChatOptions.Builder<?> other) {
			org.springframework.ai.chat.prompt.ChatOptions options = other.build();
			if (options.getModel() != null) {
				this.model = options.getModel();
			}
			if (options.getTemperature() != null) {
				this.temperature = options.getTemperature();
			}
			if (options.getMaxTokens() != null) {
				this.maxTokens = options.getMaxTokens();
			}
			if (options.getTopP() != null) {
				this.topP = options.getTopP();
			}
			if (options.getTopK() != null) {
				this.topK = options.getTopK();
			}
			if (options.getStopSequences() != null) {
				this.stopSequences = options.getStopSequences();
			}
			if (options instanceof ToolCallingChatOptions toolOptions) {
				this.toolCallbacks = ToolCallingChatOptions.mergeToolCallbacks(toolOptions.getToolCallbacks(),
						this.toolCallbacks);
				this.toolContext = ToolCallingChatOptions.mergeToolContext(toolOptions.getToolContext(),
						this.toolContext);
			}
			return this;
		}

	}

}
