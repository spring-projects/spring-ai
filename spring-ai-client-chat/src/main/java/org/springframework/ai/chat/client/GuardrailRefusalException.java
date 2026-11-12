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

import org.springframework.ai.chat.model.GuardrailRefusal;
import org.springframework.util.Assert;

/**
 * Thrown when an answer of the model is asked for as an entity and a guardrail refused
 * the turn instead.
 *
 * <p>
 * A refusal is a reply in the guardrail's own words, not the structured output that was
 * requested, so there is nothing to convert. Converting it anyway would fail as malformed
 * output and hide what happened. The exception carries the response, with the refusal and
 * the context the guardrail left, so that the caller can tell the person why there is no
 * answer.
 *
 * <p>
 * The response is not serializable. A serialized exception keeps the text of the refusal
 * and leaves the response behind.
 *
 * <p>
 * A refusal is a response that a filter stopped, so the exception is a
 * {@link ContentFilteredException}: a caller that handles both the refusal of a guardrail
 * and the safety stop of a provider catches that one type. Unlike a response that a
 * provider stopped, a refusal is never converted, as it is not what the model said.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 * @see GuardrailRefusal
 */
public class GuardrailRefusalException extends ContentFilteredException {

	/**
	 * Creates the exception for a refused turn.
	 * @param chatClientResponse the response that carries the refusal
	 */
	public GuardrailRefusalException(ChatClientResponse chatClientResponse) {
		this(chatClientResponse, refusalOf(chatClientResponse));
	}

	private GuardrailRefusalException(ChatClientResponse chatClientResponse, @Nullable String refusal) {
		super("A guardrail refused the turn, so there is no answer of the model to convert"
				+ ((refusal != null) ? ": " + refusal : ""), chatClientResponse, GuardrailRefusal.FINISH_REASON,
				refusal);
	}

	/**
	 * What the guardrail said in place of an answer. It is also the {@link #getText()
	 * text} of the exception.
	 * @return the text of the refusal, or {@code null} when it has none
	 */
	public @Nullable String getRefusal() {
		return getText();
	}

	private static @Nullable String refusalOf(ChatClientResponse chatClientResponse) {
		Assert.notNull(chatClientResponse, "chatClientResponse must not be null");
		return GuardrailRefusal.text(chatClientResponse.chatResponse());
	}

}
