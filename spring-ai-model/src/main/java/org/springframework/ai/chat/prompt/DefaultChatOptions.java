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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Default implementation for the {@link ChatOptions}.
 */
public class DefaultChatOptions implements ChatOptions {

	private @Nullable String model;

	private @Nullable Double frequencyPenalty;

	private @Nullable Integer maxTokens;

	private @Nullable Double presencePenalty;

	private @Nullable List<String> stopSequences;

	private @Nullable Double temperature;

	private @Nullable Integer topK;

	private @Nullable Double topP;

	public DefaultChatOptions() {
		// TODO remove
	}

	protected /* TODO move builder as an inner class */ DefaultChatOptions(@Nullable String model,
			@Nullable Double frequencyPenalty, @Nullable Integer maxTokens, @Nullable Double presencePenalty,
			@Nullable List<String> stopSequences, @Nullable Double temperature, @Nullable Integer topK,
			@Nullable Double topP) {
		this.model = model;
		this.frequencyPenalty = frequencyPenalty;
		this.maxTokens = maxTokens;
		this.presencePenalty = presencePenalty;
		this.stopSequences = stopSequences;
		this.temperature = temperature;
		this.topK = topK;
		this.topP = topP;
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
		return this.stopSequences != null ? Collections.unmodifiableList(this.stopSequences) : null;
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
	public ChatOptions.Builder<?> mutate() {
		return ChatOptions.builder()
			.model(this.model)
			.frequencyPenalty(this.frequencyPenalty)
			.maxTokens(this.maxTokens)
			.presencePenalty(this.presencePenalty)
			.stopSequences(this.stopSequences != null ? new ArrayList<>(this.stopSequences) : null)
			.temperature(this.temperature)
			.topK(this.topK)
			.topP(this.topP);
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		DefaultChatOptions that = (DefaultChatOptions) o;
		return Objects.equals(this.model, that.model) && Objects.equals(this.frequencyPenalty, that.frequencyPenalty)
				&& Objects.equals(this.maxTokens, that.maxTokens)
				&& Objects.equals(this.presencePenalty, that.presencePenalty)
				&& Objects.equals(this.stopSequences, that.stopSequences)
				&& Objects.equals(this.temperature, that.temperature) && Objects.equals(this.topK, that.topK)
				&& Objects.equals(this.topP, that.topP);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.frequencyPenalty, this.maxTokens, this.presencePenalty, this.stopSequences,
				this.temperature, this.topK, this.topP);
	}

}
