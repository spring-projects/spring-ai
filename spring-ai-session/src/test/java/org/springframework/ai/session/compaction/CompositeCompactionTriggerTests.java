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
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link CompositeCompactionTrigger}.
 */
class CompositeCompactionTriggerTests extends CompactionTestSupport {

	private final CompactionRequest request = request(List.of());

	@Test
	void firesWhenAnyTriggerFires() {
		CompactionTrigger trigger = CompositeCompactionTrigger.anyOf(req -> false, req -> true);

		assertThat(trigger.shouldCompact(this.request)).isTrue();
	}

	@Test
	void doesNotFireWhenNoTriggerFires() {
		CompactionTrigger trigger = CompositeCompactionTrigger.anyOf(req -> false, req -> false);

		assertThat(trigger.shouldCompact(this.request)).isFalse();
	}

	@Test
	void stopsAtFirstMatchingTrigger() {
		AtomicBoolean secondInvoked = new AtomicBoolean();
		CompactionTrigger trigger = CompositeCompactionTrigger.anyOf(req -> true, req -> {
			secondInvoked.set(true);
			return false;
		});

		assertThat(trigger.shouldCompact(this.request)).isTrue();
		assertThat(secondInvoked).isFalse();
	}

	@Test
	void combinesWithTurnCountTrigger() {
		CompactionTrigger trigger = CompositeCompactionTrigger.anyOf(req -> false, new TurnCountTrigger(1));

		assertThat(trigger.shouldCompact(request(turn("q1", "a1")))).isFalse();
		assertThat(trigger.shouldCompact(request(events(turn("q1", "a1"), turn("q2", "a2"))))).isTrue();
	}

	@Test
	void rejectsEmptyTriggers() {
		assertThatIllegalArgumentException().isThrownBy(() -> CompositeCompactionTrigger.anyOf())
			.withMessageContaining("triggers must not be empty");
	}

}
