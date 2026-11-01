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

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionAttributes;
import org.springframework.ai.session.SessionEvent;

/**
 * The base of the compaction tests: event factories and the texts of a result.
 */
abstract class CompactionTestSupport {

	static final String SESSION_ID = "test-session";

	protected CompactionTestSupport() {
	}

	/**
	 * The texts of the view as the strategy leaves it: pinned events, then the summary
	 * messages, then the preserved events.
	 */
	static List<String> kept(CompactionResult result) {
		List<String> texts = new ArrayList<>();
		result.pinnedEvents().forEach(e -> texts.add(e.getMessage().getText()));
		result.summary().forEach(m -> texts.add(m.getText()));
		result.preservedEvents().forEach(e -> texts.add(e.getMessage().getText()));
		return texts;
	}

	/** The texts of the events that leave the view. */
	static List<String> covered(CompactionResult result) {
		return result.coveredEvents().stream().map(e -> e.getMessage().getText()).toList();
	}

	static SessionEvent system(String text) {
		return event(new SystemMessage(text));
	}

	static SessionEvent user(String text) {
		return event(new UserMessage(text));
	}

	static SessionEvent assistant(String text) {
		return event(new AssistantMessage(text));
	}

	static SessionEvent event(Message message) {
		return SessionEvent.builder().sessionId(SESSION_ID).message(message).build();
	}

	/**
	 * A synthetic summary-turn event, as a previous compaction would have appended it.
	 */
	static SessionEvent synthetic(Message message) {
		return SessionEvent.builder()
			.sessionId(SESSION_ID)
			.message(message)
			.metadata(SessionEvent.METADATA_SYNTHETIC, true)
			.metadata(SessionEvent.METADATA_COMPACTION_SOURCE, "test")
			.build();
	}

	/** A prior summary turn: a synthetic shadow prompt and a synthetic summary. */
	static List<SessionEvent> summaryTurn(String summary) {
		return List.of(synthetic(new UserMessage("Summarize the conversation we had so far.")),
				synthetic(new AssistantMessage(summary)));
	}

	static List<SessionEvent> turn(String userText, String assistantText) {
		return List.of(user(userText), assistant(assistantText));
	}

	/** A user question answered with a tool call and its result. */
	static List<SessionEvent> toolTurn(String question) {
		return List.of(user(question), event(AssistantMessage.builder()
			.toolCalls(List
				.of(new AssistantMessage.ToolCall("call-1", "function", "get_weather", "{\"location\":\"Paris\"}")))
			.build()),
				event(ToolResponseMessage.builder()
					.responses(List
						.of(new ToolResponseMessage.ToolResponse("call-1", "get_weather", "{\"temp\":\"22C\"}")))
					.build()));
	}

	/** {@code n} user messages {@code msg-1 … msg-n}. */
	static List<SessionEvent> userMessages(int n) {
		List<SessionEvent> events = new ArrayList<>();
		for (int i = 1; i <= n; i++) {
			events.add(user("msg-" + i));
		}
		return events;
	}

	@SafeVarargs
	static List<SessionEvent> events(List<SessionEvent>... groups) {
		List<SessionEvent> all = new ArrayList<>();
		for (List<SessionEvent> group : groups) {
			all.addAll(group);
		}
		return all;
	}

	static CompactionRequest request(List<SessionEvent> view) {
		Session session = Session.builder()
			.id(SESSION_ID)
			.metadata(SessionAttributes.PRINCIPAL_NAME, "test-user")
			.build();
		return CompactionRequest.of(session, new ArrayList<>(view));
	}

}
