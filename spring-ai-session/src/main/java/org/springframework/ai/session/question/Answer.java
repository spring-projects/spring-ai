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

import org.jspecify.annotations.Nullable;

import org.springframework.ai.session.EventType;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.status.TurnService;
import org.springframework.util.Assert;

/**
 * The answer to a {@link Question}, recorded as an {@link EventType#ANSWER} event when
 * the turn resumes ({@link TurnService#resume}). It points at the question by the event
 * id of the {@link EventType#QUESTION} event; a question no answer points at is pending.
 *
 * @param questionEventId the event id of the question this answers
 * @param value the answer, stored as JSON: the decisions on the tool calls, the value the
 * interrupt node resumes with, the elicitation result; {@code null} for an
 * acknowledgement without content
 * @author Christian Tzolov
 * @since 2.1.0
 */
public record Answer(String questionEventId, @Nullable Object value) {

	public Answer {
		Assert.hasText(questionEventId, "questionEventId must not be null or empty");
	}

	/**
	 * Reads the answer an {@link EventType#ANSWER} event records.
	 * @throws IllegalArgumentException if the event is not an answer event, or points at
	 * no question
	 */
	public static Answer from(SessionEvent event) {
		QuestionEvents.requireType(event, EventType.ANSWER);
		Object questionEventId = event.getPayload().get(QuestionEvents.PAYLOAD_QUESTION_EVENT_ID);
		Assert.notNull(questionEventId, () -> "Answer event '" + event.getId() + "' points at no question");
		return new Answer(String.valueOf(questionEventId), event.getPayload().get(QuestionEvents.PAYLOAD_VALUE));
	}

}
