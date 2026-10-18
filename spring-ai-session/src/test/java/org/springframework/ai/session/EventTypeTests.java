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

package org.springframework.ai.session;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for the open {@link EventType}.
 *
 * @author Christian Tzolov
 */
class EventTypeTests {

	@Test
	void typeIsItsName() {
		EventType progress = EventType.of("MY_APP.PROGRESS");

		assertThat(progress.name()).isEqualTo("MY_APP.PROGRESS");
		assertThat(EventType.of("MY_APP.PROGRESS")).isEqualTo(progress).hasSameHashCodeAs(progress);
		assertThat(EventType.of("MESSAGE")).isEqualTo(EventType.MESSAGE);
		assertThat(new java.util.HashSet<>(java.util.List.of(EventType.COMPACTION, EventType.of("COMPACTION"))))
			.hasSize(1);
		assertThat(progress).hasToString("MY_APP.PROGRESS");
		assertThat(progress.canCarryMessage()).isFalse();
		assertThat(EventType.MESSAGE.canCarryMessage()).isTrue();
		assertThat(EventType.of("STATUS")).isEqualTo(EventType.STATUS);
		assertThat(EventType.of("INTERRUPT")).isEqualTo(EventType.INTERRUPT);
		assertThat(EventType.STATUS.canCarryMessage()).isFalse();
		assertThat(EventType.INTERRUPT.canCarryMessage()).isFalse();
	}

	@Test
	void nameIsUpperCaseWithDigitsUnderscoresAndDots() {
		assertThatIllegalArgumentException().isThrownBy(() -> EventType.of("message"));
		assertThatIllegalArgumentException().isThrownBy(() -> EventType.of("MY APP"));
		assertThatIllegalArgumentException().isThrownBy(() -> EventType.of(""));
		assertThat(EventType.of("A1_B.C").name()).isEqualTo("A1_B.C");
	}

	@Test
	void nameIsAtMostSixtyFourCharacters() {
		String longest = "A".repeat(EventType.MAX_NAME_LENGTH);
		assertThat(EventType.of(longest).name()).isEqualTo(longest);
		assertThatIllegalArgumentException().isThrownBy(() -> EventType.of(longest + "A"))
			.withMessageContaining("longer than " + EventType.MAX_NAME_LENGTH);
	}

	@Test
	void customTypeIsStoredAndFilteredLikeAnyOther() {
		SessionService service = DefaultSessionService.builder()
			.sessionRepository(InMemorySessionRepository.builder().build())
			.build();
		Session session = service.create(CreateSessionRequest.builder().build());
		EventType progress = EventType.of("MY_APP.PROGRESS");
		service.appendEvent(SessionEvent.builder().sessionId(session.id()).type(progress).payload("step", 1).build());
		service.appendMessage(session.id(), new org.springframework.ai.chat.messages.UserMessage("hi"));

		assertThat(service.getEvents(session.id(), EventFilter.builder().eventTypes(Set.of(progress)).build()))
			.singleElement()
			.satisfies(e -> assertThat(e.getPayload()).containsEntry("step", 1));
		assertThat(service.getActiveMessages(session.id())).hasSize(1);
		assertThat(service.getEvents(session.id())).hasSize(2);
	}

	@Test
	void typeIsJsonText() {
		tools.jackson.databind.json.JsonMapper mapper = tools.jackson.databind.json.JsonMapper.builder().build();

		assertThat(mapper.writeValueAsString(EventType.of("MY_APP.PROGRESS"))).isEqualTo("\"MY_APP.PROGRESS\"");
		assertThat(mapper.readValue("\"STATUS\"", EventType.class)).isEqualTo(EventType.of("STATUS"));
		assertThat(mapper.writeValueAsString(java.util.Map.of("type", EventType.MESSAGE)))
			.isEqualTo("{\"type\":\"MESSAGE\"}");
	}

}
