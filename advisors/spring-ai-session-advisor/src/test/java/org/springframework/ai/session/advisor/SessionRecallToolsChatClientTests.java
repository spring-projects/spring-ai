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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.compaction.WindowCompactionStrategy;
import org.springframework.ai.session.tool.SessionRecallTools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * {@link SessionRecallTools} in a {@code ChatClient} with the session advisors: the model
 * calls {@code conversation_search} through the tool-calling loop and gets back what a
 * compaction took out of its prompt.
 *
 * @author Christian Tzolov
 */
class SessionRecallToolsChatClientTests {

	private final ScriptedChatModel model = new ScriptedChatModel();

	private final SessionService sessionService = DefaultSessionService.builder()
		.sessionRepository(InMemorySessionRepository.builder().build())
		.build();

	private final String sessionId = "s1";

	private final ChatClient client = ChatClient.builder(this.model)
		.defaultAdvisors(SessionAdvisors.builder(this.sessionService).build())
		.defaultTools(SessionRecallTools.builder(this.sessionService).build())
		.build();

	@Test
	void toolReadsSessionIdUnderAdvisorsKey() {
		assertThat(SessionRecallTools.SESSION_ID_CONTEXT_KEY).isEqualTo(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY);
	}

	@Test
	void modelFindsWhatCompactionTookOutOfPrompt() {
		this.model.thenAnswer("Noted.").thenAnswer("Hello!");
		say("My invoice number is 42.");
		say("Hi again");
		this.sessionService.compact(this.sessionId, request -> true,
				WindowCompactionStrategy.builder().lastTurns(1).build());

		this.model.then(prompt -> search("c1", "invoice")).then(prompt -> answer("It is 42."));
		assertThat(say("What was my invoice number?")).isEqualTo("It is 42.");

		// The first round's prompt no longer holds the old turn; the search brings it
		// back
		Prompt firstRound = this.model.prompts().get(2);
		assertThat(ScriptedChatModel.describe(firstRound)).doesNotContain("My invoice number is 42.");
		assertThat(toolResults(this.model.prompts().get(3))).singleElement()
			.asString()
			.contains("My invoice number is 42.")
			.doesNotContain("Hi again");
	}

	@Test
	void searchWithoutSessionIdInToolContextTellsModel() {
		this.model.then(prompt -> search("c1", "invoice")).thenAnswer("I cannot search.");

		// The advisors get the session id; the tool context holds something else
		this.client.prompt()
			.user("Find my invoice")
			.advisors(SessionAdvisors.session(this.sessionId))
			.toolContext(Map.of("tenant", "acme"))
			.call()
			.content();

		assertThat(toolResults(this.model.prompts().get(1))).singleElement()
			.asString()
			.contains("no session id was provided");
	}

	@Test
	void searchWithoutAnyToolContextFailsCall() {
		this.model.then(prompt -> search("c1", "invoice"));

		// The tool-calling machinery refuses a tool that takes a ToolContext when the
		// call has none, before the tool runs
		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.client.prompt()
				.user("Find my invoice")
				.advisors(SessionAdvisors.session(this.sessionId))
				.call()
				.content())
			.withMessageContaining("ToolContext is required");
	}

	private String say(String text) {
		return this.client.prompt()
			.user(text)
			.advisors(SessionAdvisors.session(this.sessionId))
			.toolContext(Map.of(SessionRecallTools.SESSION_ID_CONTEXT_KEY, this.sessionId))
			.call()
			.content();
	}

	private static ChatResponse search(String callId, String query) {
		return new ChatResponse(List.of(new Generation(AssistantMessage.builder()
			.content("")
			.toolCalls(List.of(new AssistantMessage.ToolCall(callId, "function", "conversation_search",
					"{\"query\":\"" + query + "\"}")))
			.build())));
	}

	private static ChatResponse answer(String text) {
		return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
	}

	private static List<String> toolResults(Prompt prompt) {
		return prompt.getInstructions()
			.stream()
			.filter(ToolResponseMessage.class::isInstance)
			.map(ToolResponseMessage.class::cast)
			.flatMap(results -> results.getResponses().stream())
			.map(ToolResponseMessage.ToolResponse::responseData)
			.toList();
	}

}
