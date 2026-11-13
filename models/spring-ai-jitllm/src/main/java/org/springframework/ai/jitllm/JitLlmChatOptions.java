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

package org.springframework.ai.jitllm;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

/**
 * Per-request options for {@link JitLlmChatModel}.
 *
 * <p>
 * The model file, the execution target and the context length are fixed when the model is
 * loaded and are set on {@link JitLlmChatModel.Builder}. {@code topK},
 * {@code frequencyPenalty} and {@code presencePenalty} are accepted for
 * {@link ChatOptions} compatibility but ignored: the engine's sampler does not expose
 * them.
 *
 * @author Michalis Papadimitriou
 * @since 2.1.0
 */
public class JitLlmChatOptions implements ToolCallingChatOptions {

	private final @Nullable String model;

	private final @Nullable Integer maxTokens;

	private final @Nullable Double temperature;

	private final @Nullable Double topP;

	private final @Nullable Integer topK;

	private final @Nullable Long seed;

	private final @Nullable List<String> stopSequences;

	private final @Nullable Double frequencyPenalty;

	private final @Nullable Double presencePenalty;

	private final @Nullable List<ToolCallback> toolCallbacks;

	private final @Nullable Map<String, Object> toolContext;

	protected JitLlmChatOptions(@Nullable String model, @Nullable Integer maxTokens, @Nullable Double temperature,
			@Nullable Double topP, @Nullable Integer topK, @Nullable Long seed, @Nullable List<String> stopSequences,
			@Nullable Double frequencyPenalty, @Nullable Double presencePenalty,
			@Nullable List<ToolCallback> toolCallbacks, @Nullable Map<String, Object> toolContext) {
		this.model = model;
		this.maxTokens = maxTokens;
		this.temperature = temperature;
		this.topP = topP;
		this.topK = topK;
		this.seed = seed;
		this.stopSequences = stopSequences != null ? List.copyOf(stopSequences) : null;
		this.frequencyPenalty = frequencyPenalty;
		this.presencePenalty = presencePenalty;
		this.toolCallbacks = toolCallbacks != null ? List.copyOf(toolCallbacks) : null;
		this.toolContext = toolContext != null ? Map.copyOf(toolContext) : null;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public @Nullable String getModel() {
		return this.model;
	}

	@Override
	public @Nullable Integer getMaxTokens() {
		return this.maxTokens;
	}

	@Override
	public @Nullable Double getTemperature() {
		return this.temperature;
	}

	@Override
	public @Nullable Double getTopP() {
		return this.topP;
	}

	@Override
	public @Nullable Integer getTopK() {
		return this.topK;
	}

	/**
	 * The sampling seed.
	 * @return the seed, or {@code null} for the engine's default
	 */
	public @Nullable Long getSeed() {
		return this.seed;
	}

	@Override
	public @Nullable List<String> getStopSequences() {
		return this.stopSequences;
	}

	@Override
	public @Nullable Double getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	@Override
	public @Nullable Double getPresencePenalty() {
		return this.presencePenalty;
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
		return JitLlmChatOptions.builder()
			.model(this.model)
			.maxTokens(this.maxTokens)
			.temperature(this.temperature)
			.topP(this.topP)
			.topK(this.topK)
			.seed(this.seed)
			.stopSequences(this.stopSequences)
			.frequencyPenalty(this.frequencyPenalty)
			.presencePenalty(this.presencePenalty)
			.toolCallbacks(this.toolCallbacks)
			.toolContext(this.toolContext);
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof JitLlmChatOptions that)) {
			return false;
		}
		return Objects.equals(this.model, that.model) && Objects.equals(this.maxTokens, that.maxTokens)
				&& Objects.equals(this.temperature, that.temperature) && Objects.equals(this.topP, that.topP)
				&& Objects.equals(this.topK, that.topK) && Objects.equals(this.seed, that.seed)
				&& Objects.equals(this.stopSequences, that.stopSequences)
				&& Objects.equals(this.frequencyPenalty, that.frequencyPenalty)
				&& Objects.equals(this.presencePenalty, that.presencePenalty)
				&& Objects.equals(this.toolCallbacks, that.toolCallbacks)
				&& Objects.equals(this.toolContext, that.toolContext);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.maxTokens, this.temperature, this.topP, this.topK, this.seed,
				this.stopSequences, this.frequencyPenalty, this.presencePenalty, this.toolCallbacks, this.toolContext);
	}

	// public Builder class exposed to users. Avoids having to deal with noisy generic
	// parameters.
	public static class Builder extends AbstractBuilder<Builder> {

	}

	protected abstract static class AbstractBuilder<B extends AbstractBuilder<B>>
			extends DefaultToolCallingChatOptions.Builder<B> {

		protected @Nullable Long seed;

		public B seed(@Nullable Long seed) {
			this.seed = seed;
			return self();
		}

		@Override
		public B combineWith(ChatOptions.Builder<?> other) {
			super.combineWith(other);
			if (other instanceof AbstractBuilder<?> that && that.seed != null) {
				this.seed = that.seed;
			}
			return self();
		}

		@Override
		public JitLlmChatOptions build() {
			return new JitLlmChatOptions(this.model, this.maxTokens, this.temperature, this.topP, this.topK, this.seed,
					this.stopSequences, this.frequencyPenalty, this.presencePenalty, this.toolCallbacks,
					this.toolContext);
		}

	}

}
