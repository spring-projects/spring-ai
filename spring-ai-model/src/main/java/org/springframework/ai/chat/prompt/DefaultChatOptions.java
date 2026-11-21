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

package org.springframework.ai.chat.prompt;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Default implementation for the {@link ChatOptions}.
 */
public class DefaultChatOptions implements ChatOptions {

	private final @Nullable String model;

	private final @Nullable Double frequencyPenalty;

	private final @Nullable Integer maxTokens;

	private final @Nullable Double presencePenalty;

	private final @Nullable List<String> stopSequences;

	private final @Nullable Double temperature;

	private final @Nullable Integer topK;

	private final @Nullable Double topP;

	private final @Nullable String outputType;

	private final @Nullable String reasoningLevel;

	private final @Nullable Integer seed;

	private final @Nullable Integer choiceCount;

	protected DefaultChatOptions(@Nullable String model, @Nullable Double frequencyPenalty, @Nullable Integer maxTokens,
			@Nullable Double presencePenalty, @Nullable List<String> stopSequences, @Nullable Double temperature,
			@Nullable Integer topK, @Nullable Double topP) {
		this(model, frequencyPenalty, maxTokens, presencePenalty, stopSequences, temperature, topK, topP, null, null,
				null, null);
	}

	protected DefaultChatOptions(@Nullable String model, @Nullable Double frequencyPenalty, @Nullable Integer maxTokens,
			@Nullable Double presencePenalty, @Nullable List<String> stopSequences, @Nullable Double temperature,
			@Nullable Integer topK, @Nullable Double topP, @Nullable String outputType, @Nullable String reasoningLevel,
			@Nullable Integer seed, @Nullable Integer choiceCount) {
		this.model = model;
		this.frequencyPenalty = frequencyPenalty;
		this.maxTokens = maxTokens;
		this.presencePenalty = presencePenalty;
		this.stopSequences = stopSequences != null ? List.copyOf(stopSequences) : null;
		this.temperature = temperature;
		this.topK = topK;
		this.topP = topP;
		this.outputType = outputType;
		this.reasoningLevel = reasoningLevel;
		this.seed = seed;
		this.choiceCount = choiceCount;
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
	public @Nullable String getOutputType() {
		return this.outputType;
	}

	@Override
	public @Nullable String getReasoningLevel() {
		return this.reasoningLevel;
	}

	@Override
	public @Nullable Integer getSeed() {
		return this.seed;
	}

	@Override
	public @Nullable Integer getChoiceCount() {
		return this.choiceCount;
	}

	@Override
	public ChatOptions.Builder<?> mutate() {
		return ChatOptions.builder()
			.model(this.model)
			.frequencyPenalty(this.frequencyPenalty)
			.maxTokens(this.maxTokens)
			.presencePenalty(this.presencePenalty)
			.stopSequences(this.stopSequences)
			.temperature(this.temperature)
			.topK(this.topK)
			.topP(this.topP)
			.outputType(this.outputType)
			.reasoningLevel(this.reasoningLevel)
			.seed(this.seed)
			.choiceCount(this.choiceCount);
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		DefaultChatOptions that = (DefaultChatOptions) o;
		return Objects.equals(this.model, that.model) && Objects.equals(this.frequencyPenalty, that.frequencyPenalty)
				&& Objects.equals(this.maxTokens, that.maxTokens)
				&& Objects.equals(this.presencePenalty, that.presencePenalty)
				&& Objects.equals(this.stopSequences, that.stopSequences)
				&& Objects.equals(this.temperature, that.temperature) && Objects.equals(this.topK, that.topK)
				&& Objects.equals(this.topP, that.topP) && Objects.equals(this.outputType, that.outputType)
				&& Objects.equals(this.reasoningLevel, that.reasoningLevel) && Objects.equals(this.seed, that.seed)
				&& Objects.equals(this.choiceCount, that.choiceCount);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.frequencyPenalty, this.maxTokens, this.presencePenalty, this.stopSequences,
				this.temperature, this.topK, this.topP, this.outputType, this.reasoningLevel, this.seed,
				this.choiceCount);
	}

}
