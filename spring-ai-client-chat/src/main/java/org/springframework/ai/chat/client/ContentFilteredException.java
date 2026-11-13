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

package org.springframework.ai.chat.client;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.metadata.FinishReasonCategory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.util.Assert;

/**
 * Thrown when an answer of the model is asked for as an entity, and a safety filter or a
 * refusal of the model stopped the response and left nothing that converts to it.
 *
 * <p>
 * Providers report such a stop under different names: {@code content_filter},
 * {@code refusal}, {@code guardrail_intervened}, {@code SAFETY} and others. All of them
 * are {@link FinishReasonCategory#FILTERED}. What is left of the response is usually
 * empty, cut short, or a refusal in plain words. The exception is thrown when that text
 * is empty or cannot be converted, in place of a parsing error that would hide what
 * happened; the parsing error is then the {@linkplain #getCause() cause}. A filtered
 * response that converts all the same is returned as an entity.
 *
 * <p>
 * The exception carries the finish reason as the provider named it, the text the provider
 * returned, and the response. A provider may report the stop elsewhere than in the finish
 * reason: OpenAI reports a refusal of the model in a field of its own, with an ordinary
 * stop and an empty text. The finish reason then does not tell why, and the response
 * holds what the provider said, in its metadata. The response is not serializable: a
 * serialized exception keeps the finish reason and the text, and leaves the response
 * behind.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 * @see FinishReasonCategory#FILTERED
 * @see GuardrailRefusalException
 */
public class ContentFilteredException extends RuntimeException {

	private final @Nullable String finishReason;

	private final @Nullable String text;

	private final transient @Nullable ChatClientResponse chatClientResponse;

	/**
	 * Creates the exception for a filtered response.
	 * @param chatClientResponse the response that the filter stopped
	 * @param cause why what the filter left could not be converted, or {@code null} when
	 * it left nothing
	 */
	public ContentFilteredException(ChatClientResponse chatClientResponse, @Nullable Throwable cause) {
		this(chatClientResponse, resultOf(chatClientResponse), cause);
	}

	private ContentFilteredException(ChatClientResponse chatClientResponse, @Nullable Generation result,
			@Nullable Throwable cause) {
		this(chatClientResponse, (result != null) ? result.getMetadata().getFinishReason() : null,
				(result != null) ? result.getOutput().getText() : null, cause);
	}

	private ContentFilteredException(ChatClientResponse chatClientResponse, @Nullable String finishReason,
			@Nullable String text, @Nullable Throwable cause) {
		super("A safety filter or a refusal of the model stopped the response (finish reason: " + finishReason
				+ "), so there is no answer to convert", cause);
		this.chatClientResponse = chatClientResponse;
		this.finishReason = finishReason;
		this.text = text;
	}

	/**
	 * Creates the exception for a subclass that tells in its own words what stopped the
	 * response.
	 * @param message the message of the exception
	 * @param chatClientResponse the response that was stopped
	 * @param finishReason the finish reason of the generation that was stopped
	 * @param text the text of the generation that was stopped
	 */
	protected ContentFilteredException(String message, ChatClientResponse chatClientResponse,
			@Nullable String finishReason, @Nullable String text) {
		super(message);
		Assert.notNull(chatClientResponse, "chatClientResponse must not be null");
		this.chatClientResponse = chatClientResponse;
		this.finishReason = finishReason;
		this.text = text;
	}

	/**
	 * The finish reason as the provider named it, for example {@code content_filter} or
	 * {@code refusal}. It is an ordinary stop when the provider reports the stop in
	 * another field.
	 * @return the raw finish reason
	 */
	public @Nullable String getFinishReason() {
		return this.finishReason;
	}

	/**
	 * What the provider returned as the text of the response: nothing, the start of an
	 * answer, or a refusal in plain words.
	 * @return the text of the stopped response, possibly empty or {@code null}
	 */
	public @Nullable String getText() {
		return this.text;
	}

	/**
	 * The response that the filter stopped.
	 * @return the response
	 * @throws IllegalStateException if the exception was deserialized, as the response is
	 * not serialized with it
	 */
	public ChatClientResponse getChatClientResponse() {
		Assert.state(this.chatClientResponse != null, "The response is not kept when the exception is serialized");
		return this.chatClientResponse;
	}

	private static @Nullable Generation resultOf(ChatClientResponse chatClientResponse) {
		Assert.notNull(chatClientResponse, "chatClientResponse must not be null");
		ChatResponse chatResponse = chatClientResponse.chatResponse();
		return (chatResponse != null) ? chatResponse.getResult() : null;
	}

}
