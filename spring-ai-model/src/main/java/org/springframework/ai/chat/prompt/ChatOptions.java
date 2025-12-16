/*
 * Copyright 2023-2024 the original author or authors.
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
	 * Returns a copy of this {@link ChatOptions}.
	 * @return a copy of this {@link ChatOptions}
	 */
	<T extends ChatOptions> T copy();

	/**
	 * Creates a new {@link Builder} to create the default {@link ChatOptions}.
	 * @return Returns a new {@link Builder}.
	 */
	static Builder builder() {
		return new DefaultChatOptionsBuilder();
	}

	/**
	 * Builder for creating {@link ChatOptions} instance.
	 */
	interface Builder {

		/**
		 * Builds with the model to use for the chat.
		 * @param model
		 * @return the builder
		 */
		Builder model(String model);

		/**
		 * Builds with the frequency penalty to use for the chat.
		 * @param frequencyPenalty
		 * @return the builder.
		 */
		Builder frequencyPenalty(Double frequencyPenalty);

		/**
		 * Builds with the maximum number of tokens to use for the chat.
		 * @param maxTokens
		 * @return the builder.
		 */
		Builder maxTokens(Integer maxTokens);

		/**
		 * Builds with the presence penalty to use for the chat.
		 * @param presencePenalty
		 * @return the builder.
		 */
		Builder presencePenalty(Double presencePenalty);

		/**
		 * Builds with the stop sequences to use for the chat.
		 * @param stopSequences
		 * @return the builder.
		 */
		Builder stopSequences(List<String> stopSequences);

		/**
		 * Builds with the temperature to use for the chat.
		 * @param temperature
		 * @return the builder.
		 */
		Builder temperature(Double temperature);

		/**
		 * Builds with the top K to use for the chat.
		 * @param topK
		 * @return the builder.
		 */
		Builder topK(Integer topK);

		/**
		 * Builds with the top P to use for the chat.
		 * @param topP
		 * @return the builder.
		 */
		Builder topP(Double topP);

		/**
		 * Build the {@link ChatOptions}.
		 * @return the Chat options.
		 */
		ChatOptions build();

	}

}
