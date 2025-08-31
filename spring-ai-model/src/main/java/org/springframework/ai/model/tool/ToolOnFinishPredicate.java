/*
 * Copyright 2023-2025 the original author or authors.
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
import java.util.function.BiPredicate;

import org.jetbrains.annotations.NotNull;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * Executes tools only when the assistant signals completion (finishReason = "tool_calls"
 * or "stop").
 */
public final class ToolOnFinishPredicate implements ToolExecutionEligibilityPredicate {

	@Override
	public boolean isToolExecutionRequired(ChatOptions opts, ChatResponse resp) {
		List<Generation> gens = resp.getResults();
		if (gens.isEmpty()) {
			return false;
		}

		Generation gen = gens.get(0);
		boolean hasToolCalls = !gen.getOutput().getToolCalls().isEmpty();
		String finish = String.valueOf(gen.getMetadata().get("finishReason"));

		return hasToolCalls && ("tool_calls".equalsIgnoreCase(finish) || "stop".equalsIgnoreCase(finish));
	}

	@Override
	public boolean test(ChatOptions chatOptions, ChatResponse chatResponse) {
		return false;
	}

	@NotNull
	@Override
	public BiPredicate<ChatOptions, ChatResponse> and(
			@NotNull BiPredicate<? super ChatOptions, ? super ChatResponse> other) {
		return ToolExecutionEligibilityPredicate.super.and(other);
	}

	@NotNull
	@Override
	public BiPredicate<ChatOptions, ChatResponse> negate() {
		return ToolExecutionEligibilityPredicate.super.negate();
	}

	@NotNull
	@Override
	public BiPredicate<ChatOptions, ChatResponse> or(
			@NotNull BiPredicate<? super ChatOptions, ? super ChatResponse> other) {
		return ToolExecutionEligibilityPredicate.super.or(other);
	}

}
