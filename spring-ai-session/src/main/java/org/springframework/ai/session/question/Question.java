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
 * A question a turn stops on, waiting for an answer from outside: a person's decision on
 * a tool call, for example. It is recorded as a {@link EventType#QUESTION} event when the
 * turn parks ({@link TurnService#park}) and read back from it; the {@link Answer} comes
 * in its own event when the turn resumes ({@link TurnService#resume}).
 * <p>
 * The question goes from the turn to the outside and waits for an answer. An
 * {@link EventType#INTERRUPT} goes the other way, from the outside into the turn, and
 * waits for nothing. The two are different events for that reason.
 *
 * @param kind what sort of question it is, so a reader knows how to show it and what
 * answers it: {@link #KIND_TOOL_CONFIRMATION}, or a harness's own name
 * @param payload the question itself, stored as JSON; what it holds depends on the kind.
 * A tool confirmation carries the calls that wait for a decision and names the reply they
 * belong to. {@code null} when the kind says it all
 * @author Christian Tzolov
 * @since 2.1.0
 */
public record Question(String kind, @Nullable Object payload) {

	/**
	 * A tool call that waits for a person's confirmation. The payload names the reply's
	 * calls and which of them wait for a decision; the answer is the decisions.
	 */
	public static final String KIND_TOOL_CONFIRMATION = "toolConfirmation";

	public Question {
		Assert.hasText(kind, "kind must not be null or empty");
	}

	/**
	 * Reads the question a {@link EventType#QUESTION} event records.
	 * @throws IllegalArgumentException if the event is not a question event, or names no
	 * kind
	 */
	public static Question from(SessionEvent event) {
		QuestionEvents.requireType(event, EventType.QUESTION);
		Object kind = event.getPayload().get(QuestionEvents.PAYLOAD_KIND);
		Assert.notNull(kind, () -> "Question event '" + event.getId() + "' names no kind");
		return new Question(String.valueOf(kind), event.getPayload().get(QuestionEvents.PAYLOAD_VALUE));
	}

}
