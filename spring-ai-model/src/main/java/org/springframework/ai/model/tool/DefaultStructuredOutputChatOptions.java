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

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.ai.chat.prompt.DefaultChatOptionsBuilder;

/**
 * Default implementation of {@link StructuredOutputChatOptions}.
 *
 * Mainly to be used in model generic tests, as concrete chat implementations typically
 * use dedicated sub implementations specific to the model.
 *
 * @author Eric Bottard
 */
public class DefaultStructuredOutputChatOptions extends DefaultChatOptions implements StructuredOutputChatOptions {

	private @Nullable String outputSchema;

	protected DefaultStructuredOutputChatOptions(@Nullable String model, @Nullable Double frequencyPenalty,
			@Nullable Integer maxTokens, @Nullable Double presencePenalty, @Nullable List<String> stopSequences,
			@Nullable Double temperature, @Nullable Integer topK, @Nullable Double topP,
			@Nullable String outputSchema) {
		super(model, frequencyPenalty, maxTokens, presencePenalty, stopSequences, temperature, topK, topP);
		this.outputSchema = outputSchema;
	}

	@Override
	public @Nullable String getOutputSchema() {
		return this.outputSchema;
	}

	@Override
	public StructuredOutputChatOptions.Builder<?> mutate() {
		return StructuredOutputChatOptions.builder()
			.model(this.getModel())
			.frequencyPenalty(this.getFrequencyPenalty())
			.maxTokens(this.getMaxTokens())
			.presencePenalty(this.getPresencePenalty())
			.stopSequences(this.getStopSequences())
			.temperature(this.getTemperature())
			.topK(this.getTopK())
			.topP(this.getTopP())
			.outputSchema(this.getOutputSchema());
	}

	public static class Builder<B extends DefaultStructuredOutputChatOptions.Builder<B>>
			extends DefaultChatOptionsBuilder<B> implements StructuredOutputChatOptions.Builder<B> {

		protected @Nullable String outputSchema;

		@Override
		public B outputSchema(@Nullable String outputSchema) {
			this.outputSchema = outputSchema;
			return self();
		}

		@Override
		public B clone() {
			B copy = super.clone();
			copy.outputSchema = this.outputSchema;
			return copy;
		}

		@Override
		public StructuredOutputChatOptions build() {
			return new DefaultStructuredOutputChatOptions(this.model, this.frequencyPenalty, this.maxTokens,
					this.presencePenalty, this.stopSequences, this.temperature, this.topK, this.topP,
					this.outputSchema);
		}

		@Override
		public B combineWith(ChatOptions.Builder<?> other) {
			super.combineWith(other);
			if (other instanceof DefaultStructuredOutputChatOptions.Builder<?> that) {
				if (that.outputSchema != null) {
					this.outputSchema = that.outputSchema;
				}
			}
			return self();
		}

	}

}
