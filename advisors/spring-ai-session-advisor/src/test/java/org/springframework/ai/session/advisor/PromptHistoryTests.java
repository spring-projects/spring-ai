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

package org.springframework.ai.session.advisor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.session.SessionEvent;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PromptHistory}: the history a model call gets, and the prompt it
 * becomes.
 *
 * @author Christian Tzolov
 */
class PromptHistoryTests {

	private static final UserMessage U1 = new UserMessage("Weather in Lisbon?");

	private static final AssistantMessage A1 = new AssistantMessage("Sunny.");

	private static final UserMessage U2 = new UserMessage("And Porto?");

	@Test
	void firstCallPrependsWholeHistory() {
		assertThat(prompt(history(U1, A1), U2)).containsExactly("Weather in Lisbon?", "Sunny.", "And Porto?");
	}

	@Test
	void laterRoundPrependsOnlyWhatLoopDoesNotCarry() {
		// The loop re-sends the turn so far, which the log ends with
		AssistantMessage call = call("c1");
		List<String> prompt = prompt(history(U1, A1, U2, call), U2, call, result("c1"));

		assertThat(prompt).containsExactly("Weather in Lisbon?", "Sunny.", "And Porto?", "call:c1", "result:c1");
	}

	@Test
	void withLoopsOwnHistoryOffWholeHistoryIsPrepended() {
		AssistantMessage call = call("c1");

		assertThat(prompt(history(U1, A1, U2, call), result("c1"))).containsExactly("Weather in Lisbon?", "Sunny.",
				"And Porto?", "call:c1", "result:c1");
	}

	@Test
	void messageReloadedWithoutItsMetadataStillOverlaps() {
		// A store such as JDBC does not keep the finish reason
		AssistantMessage stored = call("c1");
		AssistantMessage resent = AssistantMessage.builder()
			.content("")
			.properties(Map.of("finishReason", "TOOL_CALLS"))
			.toolCalls(stored.getToolCalls())
			.build();

		assertThat(prompt(history(U2, stored), U2, resent, result("c1"))).containsExactly("And Porto?", "call:c1",
				"result:c1");
	}

	@Test
	void toolCallWithoutItsResultIsDropped() {
		assertThat(prompt(history(U1, call("c1")), U2)).containsExactly("Weather in Lisbon?", "And Porto?");
	}

	@Test
	void partlyAnsweredParallelCallIsDroppedWithItsAnswers() {
		AssistantMessage parallel = AssistantMessage.builder()
			.content("")
			.toolCalls(List.of(new AssistantMessage.ToolCall("c1", "function", "weather", "{}"),
					new AssistantMessage.ToolCall("c2", "function", "time", "{}")))
			.build();

		assertThat(prompt(history(U1, parallel, result("c1"), A1), U2)).containsExactly("Weather in Lisbon?", "Sunny.",
				"And Porto?");
	}

	@Test
	void toolResultWithoutItsCallIsDropped() {
		assertThat(prompt(history(U1, result("c9"), A1), U2)).containsExactly("Weather in Lisbon?", "Sunny.",
				"And Porto?");
	}

	@Test
	void requestsOwnMessagesAreNeverDropped() {
		assertThat(prompt(history(U1, A1), result("c9"))).containsExactly("Weather in Lisbon?", "Sunny.", "result:c9");
	}

	@Test
	void latestStoredSystemPromptWinsAndGoesFirstOnce() {
		List<String> prompt = prompt(history(new SystemMessage("Be long."), U1, A1, new SystemMessage("Be brief.")),
				new SystemMessage("Be brief."), U2);

		assertThat(prompt).containsExactly("Be brief.", "Weather in Lisbon?", "Sunny.", "And Porto?");
	}

	@Test
	void systemPromptOutsideWindowGoesFirst() {
		List<Message> prompt = PromptHistory.of(events(A1), new SystemMessage("Be brief.")).prompt(List.of(U2), "s1");

		assertThat(ScriptedChatModel.describe(new Prompt(prompt))).containsExactly("Be brief.", "Sunny.", "And Porto?");
	}

	@Test
	void systemPromptAmongEventsIsSentOnce() {
		SystemMessage systemPrompt = new SystemMessage("Be brief.");

		List<Message> prompt = PromptHistory.of(events(systemPrompt, U1), systemPrompt).prompt(List.of(U2), "s1");

		assertThat(ScriptedChatModel.describe(new Prompt(prompt))).containsExactly("Be brief.", "Weather in Lisbon?",
				"And Porto?");
	}

	@Test
	void syntheticSummaryIsKeptAfterSystemPrompt() {
		SessionEvent summary = SessionEvent.builder()
			.sessionId("s1")
			.message(new SystemMessage("Summary: the user asked about Lisbon."))
			.metadata(SessionEvent.METADATA_SYNTHETIC, true)
			.build();
		// The summary was logged before the system prompt changed: the prompt still goes
		// first
		List<SessionEvent> events = List.of(summary, event(new SystemMessage("Be brief.")), event(U1));

		List<Message> prompt = PromptHistory.of(events, new SystemMessage("Be brief.")).prompt(List.of(U2), "s1");

		assertThat(ScriptedChatModel.describe(new Prompt(prompt))).containsExactly("Be brief.",
				"Summary: the user asked about Lisbon.", "Weather in Lisbon?", "And Porto?");
	}

	@Test
	void withoutStoredSystemPromptOnlySyntheticSummariesComeFromLog() {
		// A ChatClient sends its system prompt with each request
		SessionEvent summary = SessionEvent.builder()
			.sessionId("s1")
			.message(new SystemMessage("Summary: the user asked about Lisbon."))
			.metadata(SessionEvent.METADATA_SYNTHETIC, true)
			.build();

		List<Message> prompt = PromptHistory.of(List.of(summary, event(U1)), null)
			.prompt(List.of(new SystemMessage("Be brief."), U2), "s1");

		assertThat(ScriptedChatModel.describe(new Prompt(prompt)))
			.containsExactly("Summary: the user asked about Lisbon.", "Be brief.", "Weather in Lisbon?", "And Porto?");
	}

	@Test
	void lastUserOrToolResponseIsMessageToStore() {
		assertThat(PromptHistory.lastUserOrToolResponse(List.of(U1, A1, U2))).isSameAs(U2);
		ToolResponseMessage result = result("c1");
		assertThat(PromptHistory.lastUserOrToolResponse(List.of(U2, call("c1"), result))).isSameAs(result);
		assertThat(PromptHistory.lastUserOrToolResponse(List.of(new SystemMessage("Be brief.")))).isNull();
	}

	private static List<String> prompt(PromptHistory history, Message... request) {
		return ScriptedChatModel.describe(new Prompt(history.prompt(List.of(request), "s1")));
	}

	/**
	 * The history as the advisor builds it: the latest stored system message is the
	 * prompt.
	 */
	private static PromptHistory history(Message... messages) {
		List<SessionEvent> events = events(messages);
		SessionEvent systemPrompt = PromptHistory.latestStoredSystemPrompt(events);
		return PromptHistory.of(events, (systemPrompt != null) ? systemPrompt.getMessage() : null);
	}

	private static List<SessionEvent> events(Message... messages) {
		return Arrays.stream(messages).map(PromptHistoryTests::event).toList();
	}

	private static SessionEvent event(Message message) {
		return SessionEvent.builder().sessionId("s1").message(message).build();
	}

	private static AssistantMessage call(String id) {
		return AssistantMessage.builder()
			.content("")
			.toolCalls(List.of(new AssistantMessage.ToolCall(id, "function", "weather", "{}")))
			.build();
	}

	private static ToolResponseMessage result(String id) {
		return ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse(id, "weather", "sunny")))
			.build();
	}

}
