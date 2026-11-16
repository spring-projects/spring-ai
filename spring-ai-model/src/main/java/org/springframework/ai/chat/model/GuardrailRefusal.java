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

package org.springframework.ai.chat.model;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.FinishReasonCategory;
import org.springframework.util.Assert;

/**
 * Marks a reply that a guardrail put in place of the model's answer.
 *
 * <p>
 * A guardrail that refuses a turn answers with words of its own, such as "I can't help
 * with that". To the rest of the chain that reply looks like any other, yet it is not
 * what the model said: validating it as the requested output, judging it or asking the
 * model again would be pointless. A guardrail therefore builds its refusal with
 * {@link #generation(String)}, which sets {@link #FINISH_REASON} as the finish reason,
 * and a component that needs to tell the two apart checks
 * {@link #isRefusal(ChatResponse)}.
 *
 * <p>
 * The marker is honoured where a refusal would otherwise be taken for structured output:
 * {@code ChatClient.entity(...)} throws a {@code GuardrailRefusalException} and the
 * {@code StructuredOutputValidationAdvisor} returns the refusal without validating it.
 * Other components, the memory advisors among them, treat a refusal as any other reply.
 * The marker is the finish reason of the generation, so a component that rebuilds the
 * generation from its text loses it.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class GuardrailRefusal {

	/**
	 * The finish reason of a {@link Generation} that carries a guardrail's refusal,
	 * telling it apart from an answer of the model.
	 */
	public static final String FINISH_REASON = "guardrailRefusal";

	private static final Set<String> FINISH_REASONS = Set.of(FINISH_REASON);

	private GuardrailRefusal() {
	}

	/**
	 * Builds the generation that carries a refusal.
	 * @param text what the refusal says
	 * @return an assistant message with the text, finished with {@link #FINISH_REASON} in
	 * the {@link FinishReasonCategory#FILTERED} category
	 */
	public static Generation generation(String text) {
		Assert.notNull(text, "text must not be null");
		return new Generation(new AssistantMessage(text),
				ChatGenerationMetadata.builder()
					.finishReason(GuardrailRefusal.FINISH_REASON)
					.finishReasonCategory(FinishReasonCategory.FILTERED)
					.build());
	}

	/**
	 * Whether a response is a guardrail's refusal rather than an answer of the model. As
	 * with {@link ChatResponse#hasToolCalls()}, one generation is enough: a response of
	 * which a guardrail refused any part is a refused turn.
	 * @param response the response, possibly {@code null}
	 * @return {@code true} when the response
	 * {@linkplain ChatResponse#hasFinishReasons(Set) has} the {@link #FINISH_REASON}
	 * finish reason
	 */
	public static boolean isRefusal(@Nullable ChatResponse response) {
		return response != null && response.hasFinishReasons(GuardrailRefusal.FINISH_REASONS);
	}

	/**
	 * Whether a generation carries a guardrail's refusal rather than an answer of the
	 * model.
	 * @param generation the generation, possibly {@code null}
	 * @return {@code true} when it finished with {@link #FINISH_REASON}, in any case
	 */
	public static boolean isRefusal(@Nullable Generation generation) {
		return generation != null && FINISH_REASON.equalsIgnoreCase(generation.getMetadata().getFinishReason());
	}

	/**
	 * What the guardrail said in place of an answer.
	 * @param response the response, possibly {@code null}
	 * @return the text of the refusal, or {@code null} when the response is not a refusal
	 * or the refusal has no text
	 */
	public static @Nullable String text(@Nullable ChatResponse response) {
		if (response == null) {
			return null;
		}
		for (Generation generation : response.getResults()) {
			if (isRefusal(generation)) {
				return generation.getOutput().getText();
			}
		}
		return null;
	}

}
