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

package org.springframework.ai.session.question;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.EventType;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.status.TurnService;
import org.springframework.util.Assert;

/**
 * Builds the events that record a question and its answer, and finds the question a
 * session waits on. A {@link EventType#QUESTION} event records the {@link Question} a
 * turn parked on, an {@link EventType#ANSWER} event records the {@link Answer} it resumed
 * with, pointing at the question by event id. Neither carries a message; the fields go in
 * the payload under the {@code PAYLOAD_*} keys. The records read them back:
 * {@link Question#from} and {@link Answer#from}.
 * <p>
 * {@link TurnService#park} writes the question with the status event that parks the turn,
 * {@link TurnService#resume} writes the answer with the status event that starts the turn
 * again, each pair in one append. Like the status events they stay in the log as the
 * audit trail and never reach a prompt: the active view holds message events only.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class QuestionEvents {

	/** Payload key of a question event: the {@link Question#kind()}. */
	public static final String PAYLOAD_KIND = "kind";

	/**
	 * Payload key of a question event: the {@link Question#payload()}; of an answer
	 * event: the {@link Answer#value()}. Absent when there is none.
	 */
	public static final String PAYLOAD_VALUE = "value";

	/**
	 * Payload key of an answer event: the event id of the question it answers.
	 */
	public static final String PAYLOAD_QUESTION_EVENT_ID = "questionEventId";

	/**
	 * Selects the newest question or answer event of a session, what {@link #pending}
	 * reads: one indexed read. A question is open until its answer follows it, and a
	 * session asks one question at a time, since a {@link TurnService#start start}
	 * refuses a parked session. So the newest event of the two types tells: a question is
	 * pending, an answer means none is.
	 */
	public static final EventFilter QUESTIONS_AND_ANSWERS = EventFilter.builder()
		.eventTypes(Set.of(EventType.QUESTION, EventType.ANSWER))
		.lastN(1)
		.build();

	private QuestionEvents() {
	}

	/** Creates the event that records the question a turn parks on. */
	public static SessionEvent ask(String sessionId, Question question) {
		Assert.hasText(sessionId, "sessionId must not be null or empty");
		Assert.notNull(question, "question must not be null");
		SessionEvent.Builder builder = SessionEvent.builder()
			.sessionId(sessionId)
			.type(EventType.QUESTION)
			.payload(PAYLOAD_KIND, question.kind());
		if (question.payload() != null) {
			builder.payload(PAYLOAD_VALUE, question.payload());
		}
		return builder.build();
	}

	/**
	 * Creates the event that records the answer to a question.
	 * @param questionEventId the event id of the question it answers
	 * @param value the answer, stored as JSON; {@code null} for an acknowledgement
	 */
	public static SessionEvent answer(String sessionId, String questionEventId, @Nullable Object value) {
		Assert.hasText(sessionId, "sessionId must not be null or empty");
		Assert.hasText(questionEventId, "questionEventId must not be null or empty");
		SessionEvent.Builder builder = SessionEvent.builder()
			.sessionId(sessionId)
			.type(EventType.ANSWER)
			.payload(PAYLOAD_QUESTION_EVENT_ID, questionEventId);
		if (value != null) {
			builder.payload(PAYLOAD_VALUE, value);
		}
		return builder.build();
	}

	/**
	 * Finds the question a session waits on: the newest question event that no answer
	 * event points at. Events of other types are skipped.
	 * @param events the session's question and answer events, oldest first, or the newest
	 * of them alone; see {@link #QUESTIONS_AND_ANSWERS}
	 * @return the pending question's event, or {@code null} when every question has its
	 * answer
	 */
	public static @Nullable SessionEvent pending(List<SessionEvent> events) {
		Assert.notNull(events, "events must not be null");
		Set<String> answered = new HashSet<>();
		for (SessionEvent event : events) {
			if (EventType.ANSWER.equals(event.getType())) {
				String questionEventId = text(event.getPayload().get(PAYLOAD_QUESTION_EVENT_ID));
				if (questionEventId != null) {
					answered.add(questionEventId);
				}
			}
		}
		for (int i = events.size() - 1; i >= 0; i--) {
			SessionEvent event = events.get(i);
			if (EventType.QUESTION.equals(event.getType()) && !answered.contains(event.getId())) {
				return event;
			}
		}
		return null;
	}

	static void requireType(SessionEvent event, EventType type) {
		Assert.notNull(event, "event must not be null");
		Assert.isTrue(type.equals(event.getType()),
				() -> "Event '" + event.getId() + "' is a " + event.getType() + " event, not a " + type + " event");
	}

	static @Nullable String text(@Nullable Object value) {
		return (value != null) ? String.valueOf(value) : null;
	}

}
