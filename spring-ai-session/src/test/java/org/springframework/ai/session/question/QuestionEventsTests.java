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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.session.EventType;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.status.SessionStatusEvents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link QuestionEvents}, {@link Question} and {@link Answer}.
 *
 * @author Christian Tzolov
 */
class QuestionEventsTests {

	@Test
	void questionEventRecordsTheKindAndThePayload() {
		Question question = new Question("myApp.approval", Map.of("ask", "Publish?"));

		SessionEvent event = QuestionEvents.ask("s", question);

		assertThat(event.getType()).isEqualTo(EventType.QUESTION);
		assertThat(event.hasMessage()).isFalse();
		assertThat(event.getPayload()).containsOnly(Map.entry(QuestionEvents.PAYLOAD_KIND, "myApp.approval"),
				Map.entry(QuestionEvents.PAYLOAD_VALUE, Map.of("ask", "Publish?")));
		assertThat(Question.from(event)).isEqualTo(question);
	}

	@Test
	void questionEventLeavesOutThePayloadWhenThereIsNone() {
		SessionEvent event = QuestionEvents.ask("s", new Question("myApp.approval", null));

		assertThat(event.getPayload()).containsOnly(Map.entry(QuestionEvents.PAYLOAD_KIND, "myApp.approval"));
		Question question = Question.from(event);
		assertThat(question.kind()).isEqualTo("myApp.approval");
		assertThat(question.payload()).isNull();
	}

	@Test
	void answerEventPointsAtTheQuestion() {
		SessionEvent asked = QuestionEvents.ask("s", new Question(Question.KIND_TOOL_CONFIRMATION, List.of("c1")));

		SessionEvent event = QuestionEvents.answer("s", asked.getId(), List.of(Map.of("key", "c1", "allowed", true)));

		assertThat(event.getType()).isEqualTo(EventType.ANSWER);
		assertThat(event.hasMessage()).isFalse();
		assertThat(event.getPayload()).containsOnly(Map.entry(QuestionEvents.PAYLOAD_QUESTION_EVENT_ID, asked.getId()),
				Map.entry(QuestionEvents.PAYLOAD_VALUE, List.of(Map.of("key", "c1", "allowed", true))));
		Answer answer = Answer.from(event);
		assertThat(answer.questionEventId()).isEqualTo(asked.getId());
		assertThat(answer.value()).isEqualTo(List.of(Map.of("key", "c1", "allowed", true)));
	}

	@Test
	void answerWithoutAValueIsAnAcknowledgement() {
		SessionEvent event = QuestionEvents.answer("s", "q1", null);

		assertThat(event.getPayload()).containsOnly(Map.entry(QuestionEvents.PAYLOAD_QUESTION_EVENT_ID, "q1"));
		assertThat(Answer.from(event)).isEqualTo(new Answer("q1", null));
	}

	@Test
	void pendingIsTheNewestQuestionNoAnswerPointsAt() {
		SessionEvent first = QuestionEvents.ask("s", new Question("k", "first"));
		SessionEvent second = QuestionEvents.ask("s", new Question("k", "second"));
		SessionEvent answerToFirst = QuestionEvents.answer("s", first.getId(), "ok");

		assertThat(QuestionEvents.pending(List.of())).isNull();
		assertThat(QuestionEvents.pending(List.of(first))).isSameAs(first);
		assertThat(QuestionEvents.pending(List.of(first, answerToFirst))).isNull();
		assertThat(QuestionEvents.pending(List.of(first, second, answerToFirst))).isSameAs(second);
		assertThat(QuestionEvents.pending(List.of(first, second, QuestionEvents.answer("s", second.getId(), null))))
			.isSameAs(first);
		// An answer that points at no known question answers nothing
		assertThat(QuestionEvents.pending(List.of(first, QuestionEvents.answer("s", "elsewhere", null))))
			.isSameAs(first);
	}

	@Test
	void pendingSkipsEventsOfOtherTypes() {
		SessionEvent question = QuestionEvents.ask("s", new Question("k", null));
		SessionEvent status = SessionStatusEvents.running("s", "run-1");
		SessionEvent message = SessionEvent.builder().sessionId("s").message(new UserMessage("hi")).build();

		assertThat(QuestionEvents.pending(List.of(status, question, message))).isSameAs(question);
		assertThat(QuestionEvents.pending(List.of(status, message))).isNull();
	}

	@Test
	void readersRejectEventsOfAnotherType() {
		SessionEvent question = QuestionEvents.ask("s", new Question("k", null));
		SessionEvent answer = QuestionEvents.answer("s", question.getId(), null);
		SessionEvent message = SessionEvent.builder().sessionId("s").message(new UserMessage("hi")).build();

		assertThatIllegalArgumentException().isThrownBy(() -> Question.from(answer))
			.withMessageContaining("not a QUESTION event");
		assertThatIllegalArgumentException().isThrownBy(() -> Question.from(message));
		assertThatIllegalArgumentException().isThrownBy(() -> Answer.from(question))
			.withMessageContaining("not a ANSWER event");
		assertThatIllegalArgumentException().isThrownBy(() -> Answer.from(message));
	}

	@Test
	void eventsWithoutTheirKeyFieldAreRejected() {
		SessionEvent kindless = SessionEvent.builder().sessionId("s").type(EventType.QUESTION).build();
		SessionEvent pointless = SessionEvent.builder().sessionId("s").type(EventType.ANSWER).build();

		assertThatIllegalArgumentException().isThrownBy(() -> Question.from(kindless))
			.withMessageContaining("names no kind");
		assertThatIllegalArgumentException().isThrownBy(() -> Answer.from(pointless))
			.withMessageContaining("points at no question");
		assertThatIllegalArgumentException().isThrownBy(() -> new Question(" ", null));
		assertThatIllegalArgumentException().isThrownBy(() -> new Answer("", null));
		assertThatIllegalArgumentException().isThrownBy(() -> QuestionEvents.answer("s", "", null));
	}

}
