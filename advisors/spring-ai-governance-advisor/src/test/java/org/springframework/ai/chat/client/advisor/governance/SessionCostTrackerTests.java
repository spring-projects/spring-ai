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

package org.springframework.ai.chat.client.advisor.governance;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SessionCostTracker}.
 *
 * @author Spring AI Contributors
 */
class SessionCostTrackerTests {

	// -------------------------------------------------------------------------
	// No limit
	// -------------------------------------------------------------------------

	@Test
	void noLimitNeverThrows() {
		SessionCostTracker tracker = new SessionCostTracker();
		tracker.recordUsage("s1", buildResponse(1_000_000L));
		// Should not throw
		tracker.checkBudget("s1");
		assertThat(tracker.hasLimit()).isFalse();
	}

	@Test
	void tokenCountAccumulatesAcrossResponses() {
		SessionCostTracker tracker = new SessionCostTracker();
		tracker.recordUsage("s1", buildResponse(100L));
		tracker.recordUsage("s1", buildResponse(50L));
		assertThat(tracker.getTokenCount("s1")).isEqualTo(150L);
	}

	@Test
	void separateSessionsTrackedIndependently() {
		SessionCostTracker tracker = new SessionCostTracker();
		tracker.recordUsage("s1", buildResponse(200L));
		tracker.recordUsage("s2", buildResponse(300L));
		assertThat(tracker.getTokenCount("s1")).isEqualTo(200L);
		assertThat(tracker.getTokenCount("s2")).isEqualTo(300L);
	}

	@Test
	void zeroCountForUnknownSession() {
		SessionCostTracker tracker = new SessionCostTracker();
		assertThat(tracker.getTokenCount("unknown")).isZero();
	}

	// -------------------------------------------------------------------------
	// With limit
	// -------------------------------------------------------------------------

	@Test
	void budgetNotExceededBelowLimit() {
		SessionCostTracker tracker = new SessionCostTracker(500L);
		tracker.recordUsage("s1", buildResponse(499L));
		// Should not throw
		tracker.checkBudget("s1");
	}

	@Test
	void budgetExceededAtLimit() {
		SessionCostTracker tracker = new SessionCostTracker(500L);
		tracker.recordUsage("s1", buildResponse(500L));
		assertThatThrownBy(() -> tracker.checkBudget("s1")).isInstanceOf(BudgetExceededException.class)
			.hasMessageContaining("s1")
			.satisfies(ex -> {
				BudgetExceededException bex = (BudgetExceededException) ex;
				assertThat(bex.getSessionId()).isEqualTo("s1");
				assertThat(bex.getTokenLimit()).isEqualTo(500L);
				assertThat(bex.getCurrentTokenCount()).isEqualTo(500L);
			});
	}

	@Test
	void budgetExceededAboveLimit() {
		SessionCostTracker tracker = new SessionCostTracker(100L);
		tracker.recordUsage("s1", buildResponse(50L));
		tracker.recordUsage("s1", buildResponse(60L));
		assertThatThrownBy(() -> tracker.checkBudget("s1")).isInstanceOf(BudgetExceededException.class);
	}

	// -------------------------------------------------------------------------
	// Clear session
	// -------------------------------------------------------------------------

	@Test
	void clearSessionResetsCount() {
		SessionCostTracker tracker = new SessionCostTracker(100L);
		tracker.recordUsage("s1", buildResponse(90L));
		tracker.clearSession("s1");
		assertThat(tracker.getTokenCount("s1")).isZero();
		// Should not throw after reset
		tracker.checkBudget("s1");
	}

	// -------------------------------------------------------------------------
	// Null / missing response
	// -------------------------------------------------------------------------

	@Test
	void nullResponseIsIgnored() {
		SessionCostTracker tracker = new SessionCostTracker(100L);
		tracker.recordUsage("s1", null);
		assertThat(tracker.getTokenCount("s1")).isZero();
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private static ChatResponse buildResponse(long totalTokens) {
		int total = (int) totalTokens;
		int half = total / 2;
		return ChatResponse.builder()
			.generations(
					java.util.List.of(new Generation(new org.springframework.ai.chat.messages.AssistantMessage("ok"))))
			.metadata(org.springframework.ai.chat.metadata.ChatResponseMetadata.builder()
				.usage(new DefaultUsage(half, half, total))
				.build())
			.build();
	}

}
