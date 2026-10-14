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

package org.springframework.ai.session.compaction;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.session.SessionEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link CompactionResult}: the unchanged result and its invariants.
 *
 * @author Christian Tzolov
 */
class CompactionResultTests extends CompactionTestSupport {

	@Test
	void unchangedCoversNothingAndCarriesNoSummary() {
		CompactionResult result = CompactionResult.unchanged();

		assertThat(result.isUnchanged()).isTrue();
		assertThat(result.coveredEventCount()).isZero();
		assertThat(result.coveredEvents()).isEmpty();
		assertThat(result.pinnedEvents()).isEmpty();
		assertThat(result.preservedEvents()).isEmpty();
		assertThat(result.toEvents("s")).isEmpty();
		assertThat(result.summary()).isEmpty();
		assertThat(result.tokensEstimatedSaved()).isZero();
	}

	@Test
	void resultThatCoversSomethingIsChange() {
		SessionEvent sys = system("rules");
		SessionEvent u1 = user("u1");
		SessionEvent u2 = user("u2");

		CompactionResult result = new CompactionResult(List.of(u1), List.of(sys), List.of(u2), List.of(), 12, "test");

		assertThat(result.isUnchanged()).isFalse();
		assertThat(result.coveredEventCount()).isEqualTo(1);
		assertThat(result.pinnedEvents()).containsExactly(sys);
		assertThat(result.preservedEvents()).containsExactly(u2);
	}

	@Test
	void summaryNeedsCoveredEventsToStandInFor() {
		assertThatThrownBy(() -> new CompactionResult(List.of(), List.of(), List.of(user("u1")),
				List.of(new AssistantMessage("summary")), 0, "test"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("covered");
	}

	@Test
	void listsAreCopiedAndImmutable() {
		List<SessionEvent> covered = new java.util.ArrayList<>(List.of(user("u1")));
		CompactionResult result = new CompactionResult(covered, List.of(), List.of(assistant("a1")), List.of(), 0,
				"test");
		covered.clear();

		assertThat(result.coveredEvents()).hasSize(1);
		assertThatThrownBy(() -> result.preservedEvents().add(user("x")))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void eventIsCoveredPinnedOrPreservedNotTwo() {
		SessionEvent u1 = user("u1");

		assertThatThrownBy(() -> new CompactionResult(List.of(u1), List.of(u1), List.of(), List.of(), 0, "test"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("not two of them");
	}

}
