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

package org.springframework.ai.anthropic;

import java.util.regex.Pattern;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.Assert;

/**
 * A single entry of a batch submitted through {@link AnthropicBatchModel}.
 *
 * <p>
 * The {@code customId} is the only way to correlate a result with its request: the
 * Anthropic API does <b>not</b> guarantee that results come back in submission order. Use
 * an identifier your application can resolve back to its own domain entity, and keep it
 * stored alongside the batch id so that correlation survives a restart.
 *
 * <p>
 * The {@link Prompt} is mapped exactly like it would be for
 * {@link AnthropicChatModel#call(Prompt)} — including system messages, conversation
 * history, images and PDF documents, prompt caching, thinking, structured output and tool
 * definitions. Per-request model and options are taken from {@link Prompt#getOptions()}
 * when it carries an {@link AnthropicChatOptions}; otherwise the batch model's default
 * options apply.
 *
 * @param customId the caller-defined correlation identifier; 1 to 64 characters, limited
 * to letters, digits, underscores and hyphens
 * @param prompt the prompt to run
 * @author Ricken Bazolo
 * @since 2.0.0
 */
public record AnthropicBatchRequest(String customId, Prompt prompt) {

	/**
	 * The identifier format accepted by the Anthropic Message Batches API.
	 */
	private static final Pattern CUSTOM_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

	public AnthropicBatchRequest {
		Assert.hasText(customId, "customId must not be empty");
		Assert.isTrue(CUSTOM_ID_PATTERN.matcher(customId).matches(),
				() -> "customId must match " + CUSTOM_ID_PATTERN.pattern() + " but was: '" + customId + "'");
		Assert.notNull(prompt, "prompt must not be null");
	}

	/**
	 * Creates a batch request from a correlation identifier and a prompt.
	 * @param customId the caller-defined correlation identifier
	 * @param prompt the prompt to run
	 * @return the batch request
	 */
	public static AnthropicBatchRequest of(String customId, Prompt prompt) {
		return new AnthropicBatchRequest(customId, prompt);
	}

	/**
	 * Creates a batch request from a correlation identifier and a plain user message.
	 * @param customId the caller-defined correlation identifier
	 * @param userText the user message content
	 * @return the batch request, using the batch model's default options
	 */
	public static AnthropicBatchRequest of(String customId, String userText) {
		return new AnthropicBatchRequest(customId, new Prompt(userText));
	}

	/**
	 * Creates a batch request from a correlation identifier, a plain user message and
	 * per-request options.
	 * @param customId the caller-defined correlation identifier
	 * @param userText the user message content
	 * @param options the Anthropic options for this entry
	 * @return the batch request
	 */
	public static AnthropicBatchRequest of(String customId, String userText, AnthropicChatOptions options) {
		return new AnthropicBatchRequest(customId, new Prompt(userText, options));
	}

}
