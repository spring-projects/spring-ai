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

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.session.compaction.CompactionResult;
import org.springframework.ai.session.compaction.WindowCompactionStrategy;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that {@link SessionService} keeps working as a conversation API when the log also
 * holds events that are not messages.
 *
 * @author Christian Tzolov
 */
class SessionServiceTypedEventsTests {

	private final SessionService service = DefaultSessionService.builder()
		.sessionRepository(InMemorySessionRepository.builder().build())
		.build();

	private Session sessionWithStatusEvents() {
		Session session = this.service
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "alice").build());
		this.service.appendMessage(session.id(), new UserMessage("q1"));
		this.service.appendEvent(status(session.id(), "RUNNING"));
		this.service.appendMessage(session.id(), new AssistantMessage("a1"));
		this.service.appendEvent(status(session.id(), "IDLE"));
		this.service.appendMessage(session.id(), new UserMessage("q2"));
		this.service.appendMessage(session.id(), new AssistantMessage("a2"));
		return session;
	}

	private static SessionEvent status(String sessionId, String status) {
		return SessionEvent.builder()
			.sessionId(sessionId)
			.type(EventType.of("STATUS"))
			.payload("status", status)
			.build();
	}

	@Test
	void getMessagesSkipsEventsThatAreNotMessages() {
		Session session = sessionWithStatusEvents();

		assertThat(this.service.getEvents(session.id())).hasSize(6);
		assertThat(this.service.getMessages(session.id())).extracting(Message::getText)
			.containsExactly("q1", "a1", "q2", "a2");
		assertThat(this.service.getActiveMessages(session.id())).extracting(Message::getText)
			.containsExactly("q1", "a1", "q2", "a2");
	}

	@Test
	void compactionSeesAndCoversOnlyMessageEvents() {
		Session session = sessionWithStatusEvents();

		CompactionResult result = this.service.compact(session.id(), req -> {
			assertThat(req.events()).allMatch(SessionEvent::hasMessage);
			assertThat(req.currentTurnCount()).isEqualTo(2);
			return true;
		}, WindowCompactionStrategy.builder().lastEvents(2).build());

		assertThat(result.coveredEvents()).allMatch(SessionEvent::hasMessage);
		assertThat(this.service.getActiveMessages(session.id())).extracting(Message::getText)
			.containsExactly("q2", "a2");
		// Runtime state is read from the log, not from the view: the boundary names
		// conversation events only, so the statuses logged before it are not in the view
		assertThat(this.service.getEvents(session.id(),
				EventFilter.builder().eventTypes(Set.of(EventType.of("STATUS"))).build()))
			.hasSize(2);
		assertThat(this.service.getEvents(session.id(),
				EventFilter.builder().activeView(true).eventTypes(Set.of(EventType.of("STATUS"))).build()))
			.isEmpty();
	}

}
