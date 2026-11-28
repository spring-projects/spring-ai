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

import org.jspecify.annotations.Nullable;

import org.springframework.ai.model.ModelOptions;

/**
 * {@link ModelOptions} representing the common options that are portable across different
 * chat models.
 */
public interface ChatOptions extends ModelOptions {

	/**
	 * Returns the model to use for the chat.
	 * @return the model to use for the chat
	 */
	@Nullable String getModel();

	/**
	 * Returns the frequency penalty to use for the chat.
	 * @return the frequency penalty to use for the chat
	 */
	@Nullable Double getFrequencyPenalty();

	/**
	 * Returns the maximum number of tokens to use for the chat.
	 * @return the maximum number of tokens to use for the chat
	 */
	@Nullable Integer getMaxTokens();

	/**
	 * Returns the presence penalty to use for the chat.
	 * @return the presence penalty to use for the chat
	 */
	@Nullable Double getPresencePenalty();

	/**
	 * Returns the stop sequences to use for the chat.
	 * @return the stop sequences to use for the chat
	 */
	@Nullable List<String> getStopSequences();

	/**
	 * Returns the temperature to use for the chat.
	 * @return the temperature to use for the chat
	 */
	@Nullable Double getTemperature();

	/**
	 * Returns the top K to use for the chat.
	 * @return the top K to use for the chat
	 */
	@Nullable Integer getTopK();

	/**
	 * Returns the top P to use for the chat.
	 * @return the top P to use for the chat
	 */
	@Nullable Double getTopP();

	/**
	 * Returns the expected output content type (e.g. "text", "json").
	 * @return the output content type
	 * @since 2.1.0
	 */
	default @Nullable String getOutputType() {
		return null;
	}

	/**
	 * Returns the reasoning effort or level setting for the model request.
	 * @return the reasoning level
	 * @since 2.1.0
	 */
	default @Nullable String getReasoningLevel() {
		return null;
	}

	/**
	 * Returns the seed setting for the model request.
	 * @return the seed
	 * @since 2.1.0
	 */
	default @Nullable Integer getSeed() {
		return null;
	}

	/**
	 * Returns the requested number of completions or choices to generate.
	 * @return the choice count
	 * @since 2.1.0
	 */
	default @Nullable Integer getChoiceCount() {
		return null;
	}

	/**
	 * Returns a new {@link Builder} initialized with the values of this
	 * {@link ChatOptions}.
	 *
	 * Concrete ChatOptions classes must implement this and return the most concrete
	 * builder implementation.
	 */
	ChatOptions.Builder<?> mutate();

	/**
	 * Creates a new {@link Builder} to create the default {@link ChatOptions}.
	 * @return Returns a new {@link Builder}.
	 */
	static ChatOptions.Builder<?> builder() {
		return new DefaultChatOptionsBuilder<>();
	}

	/**
	 * Builder for creating {@link ChatOptions} instance.
	 */
	interface Builder<B extends Builder<B>> extends Cloneable {

		B clone();

		/**
		 * Builds with the model to use for the chat.
		 * @param model
		 * @return the builder
		 */
		B model(@Nullable String model);

		/**
		 * Builds with the frequency penalty to use for the chat.
		 * @param frequencyPenalty
		 * @return the builder.
		 */
		B frequencyPenalty(@Nullable Double frequencyPenalty);

		/**
		 * Builds with the maximum number of tokens to use for the chat.
		 * @param maxTokens
		 * @return the builder.
		 */
		B maxTokens(@Nullable Integer maxTokens);

		/**
		 * Builds with the presence penalty to use for the chat.
		 * @param presencePenalty
		 * @return the builder.
		 */
		B presencePenalty(@Nullable Double presencePenalty);

		/**
		 * Builds with the stop sequences to use for the chat.
		 * @param stopSequences
		 * @return the builder.
		 */
		B stopSequences(@Nullable List<String> stopSequences);

		/**
		 * Builds with the temperature to use for the chat.
		 * @param temperature
		 * @return the builder.
		 */
		B temperature(@Nullable Double temperature);

		/**
		 * Builds with the top K to use for the chat.
		 * @param topK
		 * @return the builder.
		 */
		B topK(@Nullable Integer topK);

		/**
		 * Builds with the top P to use for the chat.
		 * @param topP
		 * @return the builder.
		 */
		B topP(@Nullable Double topP);

		/**
		 * Builds with the output content type.
		 * @param outputType the expected output content type
		 * @return the builder
		 * @since 2.1.0
		 */
		@SuppressWarnings("unchecked")
		default B outputType(@Nullable String outputType) {
			return (B) this;
		}

		/**
		 * Builds with the reasoning level.
		 * @param reasoningLevel the reasoning level
		 * @return the builder
		 * @since 2.1.0
		 */
		@SuppressWarnings("unchecked")
		default B reasoningLevel(@Nullable String reasoningLevel) {
			return (B) this;
		}

		/**
		 * Builds with the seed.
		 * @param seed the seed
		 * @return the builder
		 * @since 2.1.0
		 */
		@SuppressWarnings("unchecked")
		default B seed(@Nullable Integer seed) {
			return (B) this;
		}

		/**
		 * Builds with the choice count.
		 * @param choiceCount the number of completions to generate
		 * @return the builder
		 * @since 2.1.0
		 */
		@SuppressWarnings("unchecked")
		default B choiceCount(@Nullable Integer choiceCount) {
			return (B) this;
		}

		/**
		 * Build the {@link ChatOptions}.
		 * @return the Chat options.
		 */
		ChatOptions build();

		/**
		 * Mutate this builder by taking all {@code other}'s values that are non-null,
		 * retaining {@code this} other values.
		 */
		B combineWith(ChatOptions.Builder<?> other);

	}

}
