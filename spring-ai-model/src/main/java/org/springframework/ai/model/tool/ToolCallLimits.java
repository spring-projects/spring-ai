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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * Tool call limit configuration for {@link DefaultToolCallingManager}, and the logic to
 * evaluate it against the number of times a tool has been called.
 *
 * @author Christian Tzolov
 * @since 2.0.1
 */
final class ToolCallLimits {

	/**
	 * Sentinel value meaning that no limit is enforced.
	 */
	static final int NO_LIMIT = -1;

	private final int defaultMaxCallsPerTool;

	private final Map<String, Integer> maxCallsPerTool;

	private final Set<String> toolsExcludedFromLimit;

	private final int maxTotalToolCalls;

	private final ToolCallLimitBehavior onLimitExceeded;

	ToolCallLimits(int defaultMaxCallsPerTool, Map<String, Integer> maxCallsPerTool, Set<String> toolsExcludedFromLimit,
			int maxTotalToolCalls, ToolCallLimitBehavior onLimitExceeded) {
		this.defaultMaxCallsPerTool = defaultMaxCallsPerTool;
		this.maxCallsPerTool = Map.copyOf(maxCallsPerTool);
		this.toolsExcludedFromLimit = Set.copyOf(toolsExcludedFromLimit);
		this.maxTotalToolCalls = maxTotalToolCalls;
		this.onLimitExceeded = onLimitExceeded;
	}

	ToolCallLimitBehavior onLimitExceeded() {
		return this.onLimitExceeded;
	}

	/**
	 * Tally tool calls already executed earlier in this turn by scanning the
	 * {@link ToolResponseMessage.ToolResponse} entries present in the current turn's
	 * portion of the conversation history. The
	 * {@link org.springframework.ai.chat.prompt.Prompt} passed into
	 * {@link DefaultToolCallingManager#executeToolCalls} accumulates every prior round's
	 * assistant and tool response messages (see {@code ToolCallingAdvisor}'s tool-calling
	 * loop), so this reconstructs the running count without the manager itself holding
	 * any per-turn state. Only messages from the current turn - the last
	 * {@link UserMessage} onward, matching the Turn definition used by the
	 * {@code spring-ai-session} project - are counted, so a long conversation replayed in
	 * full by an external {@code ChatMemory} advisor does not inflate the count with tool
	 * calls from earlier turns.
	 */
	static Map<String, Integer> countPriorToolCalls(List<Message> instructions) {
		Map<String, Integer> counts = new HashMap<>();
		for (Message message : currentTurnMessages(instructions)) {
			if (message instanceof ToolResponseMessage toolResponseMessage) {
				for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
					counts.merge(response.name(), 1, Integer::sum);
				}
			}
		}
		return counts;
	}

	/**
	 * The current, still-open turn: the last {@link UserMessage} in {@code instructions}
	 * plus everything after it. Falls back to the full list if no {@link UserMessage} is
	 * present.
	 */
	private static List<Message> currentTurnMessages(List<Message> instructions) {
		for (int i = instructions.size() - 1; i >= 0; i--) {
			if (instructions.get(i) instanceof UserMessage) {
				return instructions.subList(i, instructions.size());
			}
		}
		return instructions;
	}

	/**
	 * Checks the given call counts (inclusive of the call about to be made) against the
	 * configured limits.
	 * @return a {@link Breach} describing the limit that was hit, or {@code null} if the
	 * call is within all configured limits.
	 */
	@Nullable Breach check(String toolName, int toolCallCount, int totalToolCallCount) {
		if (!this.toolsExcludedFromLimit.contains(toolName)) {
			int maxCallsForTool = this.maxCallsPerTool.getOrDefault(toolName, this.defaultMaxCallsPerTool);
			if (maxCallsForTool != NO_LIMIT && toolCallCount > maxCallsForTool) {
				return new Breach(toolName, maxCallsForTool,
						"Tool call limit (%d) exceeded for tool '%s'. No further calls to this tool are allowed in this turn."
							.formatted(maxCallsForTool, toolName));
			}
		}

		if (this.maxTotalToolCalls != NO_LIMIT && totalToolCallCount > this.maxTotalToolCalls) {
			return new Breach(null, this.maxTotalToolCalls,
					"Total tool call limit (%d) exceeded for this turn. No further tool calls are allowed."
						.formatted(this.maxTotalToolCalls));
		}

		return null;
	}

	record Breach(@Nullable String toolName, int limit, String message) {
	}

}
