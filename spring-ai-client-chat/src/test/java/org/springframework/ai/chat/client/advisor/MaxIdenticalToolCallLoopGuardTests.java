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

package org.springframework.ai.chat.client.advisor;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MaxIdenticalToolCallLoopGuard}.
 *
 * @author Christian Tzolov
 */
class MaxIdenticalToolCallLoopGuardTests {

	@Test
	void builderDefaultsToDefaultLimit() {
		MaxIdenticalToolCallLoopGuard guard = MaxIdenticalToolCallLoopGuard.builder().build();
		assertThat(guard.getMaxIdenticalToolCallCount())
			.isEqualTo(MaxIdenticalToolCallLoopGuard.DEFAULT_MAX_IDENTICAL_TOOL_CALL_COUNT);
	}

	@Test
	void builderHonorsConfiguredLimit() {
		MaxIdenticalToolCallLoopGuard guard = MaxIdenticalToolCallLoopGuard.builder()
			.maxIdenticalToolCallCount(5)
			.build();
		assertThat(guard.getMaxIdenticalToolCallCount()).isEqualTo(5);
	}

	@Test
	void builderRejectsNonPositiveLimit() {
		assertThatThrownBy(() -> MaxIdenticalToolCallLoopGuard.builder().maxIdenticalToolCallCount(0).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("maxIdenticalToolCallCount must be positive");
	}

	@Test
	void returnsErrorDecisionAfterLimitExceeded() {
		MaxIdenticalToolCallLoopGuard guard = MaxIdenticalToolCallLoopGuard.builder()
			.maxIdenticalToolCallCount(2)
			.build();
		AdvisorLoopGuard.LoopState scope = guard.begin();
		ChatResponse response = responseWithToolCall("testTool", "{}");

		// First two identical calls continue, the third exceeds the limit.
		assertThat(scope.check(response).action()).isEqualTo(AdvisorLoopGuard.Decision.Action.CONTINUE);
		assertThat(scope.check(response).action()).isEqualTo(AdvisorLoopGuard.Decision.Action.CONTINUE);
		AdvisorLoopGuard.Decision decision = scope.check(response);
		assertThat(decision.action()).isEqualTo(AdvisorLoopGuard.Decision.Action.ERROR);
		assertThat(decision.message()).contains("Identical tool call detected").contains("testTool");
	}

	@Test
	void differentArgumentsAreTrackedSeparately() {
		MaxIdenticalToolCallLoopGuard guard = MaxIdenticalToolCallLoopGuard.builder()
			.maxIdenticalToolCallCount(1)
			.build();
		AdvisorLoopGuard.LoopState scope = guard.begin();

		assertThat(scope.check(responseWithToolCall("testTool", "{}")).action())
			.isEqualTo(AdvisorLoopGuard.Decision.Action.CONTINUE);
		// Different arguments -> different key, should continue.
		assertThat(scope.check(responseWithToolCall("testTool", "{\"a\":1}")).action())
			.isEqualTo(AdvisorLoopGuard.Decision.Action.CONTINUE);
	}

	@Test
	void scopesAreIndependent() {
		MaxIdenticalToolCallLoopGuard guard = MaxIdenticalToolCallLoopGuard.builder()
			.maxIdenticalToolCallCount(1)
			.build();
		ChatResponse response = responseWithToolCall("testTool", "{}");

		AdvisorLoopGuard.LoopState first = guard.begin();
		assertThat(first.check(response).action()).isEqualTo(AdvisorLoopGuard.Decision.Action.CONTINUE);

		// A fresh scope starts counting from zero.
		AdvisorLoopGuard.LoopState second = guard.begin();
		assertThat(second.check(response).action()).isEqualTo(AdvisorLoopGuard.Decision.Action.CONTINUE);
	}

	@Test
	void nullArgumentsAreHandled() {
		MaxIdenticalToolCallLoopGuard guard = MaxIdenticalToolCallLoopGuard.builder()
			.maxIdenticalToolCallCount(1)
			.build();
		AdvisorLoopGuard.LoopState scope = guard.begin();
		ChatResponse response = responseWithToolCall("testTool", null);

		assertThat(scope.check(response).action()).isEqualTo(AdvisorLoopGuard.Decision.Action.CONTINUE);
		assertThat(scope.check(response).action()).isEqualTo(AdvisorLoopGuard.Decision.Action.ERROR);
	}

	@Test
	void excludedToolNamesAreEmptyByDefault() {
		assertThat(MaxIdenticalToolCallLoopGuard.builder().build().getExcludedToolNames()).isEmpty();
	}

	@Test
	void excludedToolsAreNotGuarded() {
		MaxIdenticalToolCallLoopGuard guard = MaxIdenticalToolCallLoopGuard.builder()
			.maxIdenticalToolCallCount(1)
			.excludedToolNames(List.of("get_status"))
			.build();
		AdvisorLoopGuard.LoopState scope = guard.begin();
		ChatResponse response = responseWithToolCall("get_status", "{}");

		assertThat(guard.getExcludedToolNames()).containsExactly("get_status");
		// Excluded tool can be called any number of times with identical arguments.
		assertThat(scope.check(response).action()).isEqualTo(AdvisorLoopGuard.Decision.Action.CONTINUE);
		assertThat(scope.check(response).action()).isEqualTo(AdvisorLoopGuard.Decision.Action.CONTINUE);
		assertThat(scope.check(response).action()).isEqualTo(AdvisorLoopGuard.Decision.Action.CONTINUE);
	}

	@Test
	void nonExcludedToolsAreStillGuardedWhenExclusionsConfigured() {
		MaxIdenticalToolCallLoopGuard guard = MaxIdenticalToolCallLoopGuard.builder()
			.maxIdenticalToolCallCount(1)
			.excludedToolNames(List.of("get_status"))
			.build();
		AdvisorLoopGuard.LoopState scope = guard.begin();
		ChatResponse response = responseWithToolCall("testTool", "{}");

		assertThat(scope.check(response).action()).isEqualTo(AdvisorLoopGuard.Decision.Action.CONTINUE);
		AdvisorLoopGuard.Decision decision = scope.check(response);
		assertThat(decision.action()).isEqualTo(AdvisorLoopGuard.Decision.Action.ERROR);
		assertThat(decision.message()).contains("testTool");
	}

	@Test
	void excludeToolNamesVarargsAccumulate() {
		MaxIdenticalToolCallLoopGuard guard = MaxIdenticalToolCallLoopGuard.builder()
			.excludeToolNames("a")
			.excludeToolNames("b", "c")
			.build();
		assertThat(guard.getExcludedToolNames()).containsExactlyInAnyOrder("a", "b", "c");
	}

	@Test
	void nullExcludedToolNamesAreRejected() {
		assertThatThrownBy(() -> new MaxIdenticalToolCallLoopGuard(1, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("excludedToolNames must not be null");
	}

	private static ChatResponse responseWithToolCall(String toolName, String arguments) {
		AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("tool-call-1", "function", toolName,
				arguments);
		AssistantMessage assistantMessage = AssistantMessage.builder()
			.content("response")
			.toolCalls(List.of(toolCall))
			.build();
		return ChatResponse.builder().generations(List.of(new Generation(assistantMessage))).build();
	}

}
