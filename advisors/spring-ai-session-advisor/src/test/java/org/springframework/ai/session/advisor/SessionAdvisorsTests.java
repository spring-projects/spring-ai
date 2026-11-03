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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallDecision;
import org.springframework.ai.model.tool.ToolCallDecisions;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.MessageFilter;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.compaction.WindowCompactionStrategy;
import org.springframework.ai.session.status.StopReason;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.core.Ordered;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SessionAdvisors} with a {@link ChatClient} that registers its own tool-calling
 * loop.
 *
 * @author Christian Tzolov
 */
class SessionAdvisorsTests {

	private final ScriptedChatModel model = new ScriptedChatModel();

	private final SessionService sessionService = DefaultSessionService.builder()
		.sessionRepository(InMemorySessionRepository.builder().build())
		.build();

	private final String sessionId = "s1";

	@Test
	void turnAndMemoryAdvisorsInTheirOrder() {
		List<Advisor> advisors = SessionAdvisors.builder(this.sessionService).build();

		assertThat(advisors).hasExactlyElementsOfTypes(SessionTurnAdvisor.class, SessionMemoryAdvisor.class);
		assertThat(advisors).extracting(Advisor::getOrder).isSorted();
	}

	@Test
	void interruptibleAddsTheInterruptAdvisorInsideTheMemoryAdvisor() {
		List<Advisor> advisors = SessionAdvisors.builder(this.sessionService).interruptible(true).build();

		assertThat(advisors).hasExactlyElementsOfTypes(SessionTurnAdvisor.class, SessionMemoryAdvisor.class,
				SessionInterruptAdvisor.class);
		assertThat(advisors).extracting(Advisor::getOrder).isSorted();
		assertThat(advisors.get(2).getOrder()).isEqualTo(SessionInterruptAdvisor.DEFAULT_ORDER);
	}

	@Test
	void interruptAdvisorFollowsTheMemoryAdvisorsOrder() {
		List<Advisor> advisors = SessionAdvisors.builder(this.sessionService)
			.memory(memory -> memory.order(Ordered.HIGHEST_PRECEDENCE + 1500))
			.interruptible(true)
			.build();

		// Just inside the memory advisor, wherever it was put, so a round's request is
		// stored before the check throws
		assertThat(advisors.get(1).getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 1500);
		assertThat(advisors.get(2).getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 1600);
	}

	@Test
	void callRunsTurnWithHistory() {
		ChatClient client = client(SessionAdvisors.builder(this.sessionService).build());
		this.model.thenAnswer("Hi!").thenAnswer("Fine.");

		say(client, "Hello");
		say(client, "How are you?");

		assertThat(ScriptedChatModel.describe(this.model.prompts().get(1))).containsExactly("Hello", "Hi!",
				"How are you?");
	}

	@Test
	void sessionWithRunIdSetsBothKeys() {
		ChatClient client = client(SessionAdvisors.builder(this.sessionService).build());
		this.model.thenAnswer("Hi!");

		Map<String, Object> context = client.prompt()
			.user("Hello")
			.advisors(SessionAdvisors.session(this.sessionId, "run-7"))
			.call()
			.chatClientResponse()
			.context();

		assertThat(context).containsEntry(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, this.sessionId)
			.containsEntry(SessionTurnAdvisor.RUN_ID_CONTEXT_KEY, "run-7");
		assertThat(this.sessionService.turns().status(this.sessionId).runId()).isEqualTo("run-7");
	}

	@Test
	void sessionWithoutRunIdRejectsAnEmptyOne() {
		assertThatIllegalArgumentException().isThrownBy(() -> SessionAdvisors.session(this.sessionId, ""));
		assertThatIllegalArgumentException().isThrownBy(() -> SessionAdvisors.session("", "run-7"));
	}

	@Test
	void memoryCustomizerConfiguresMemoryAdvisor() {
		ChatClient client = client(SessionAdvisors.builder(this.sessionService)
			.memory(memory -> memory.messageFilter(MessageFilter.byMessageType(MessageType.USER)))
			.build());
		this.model.thenAnswer("Hi!");

		say(client, "Hello");

		assertThat(this.sessionService.getActiveMessages(this.sessionId)).extracting(m -> m.getText())
			.containsExactly("Hello");
	}

	@Test
	void compactionAndMemoryCustomizersReachAdvisors() {
		AtomicInteger customized = new AtomicInteger();
		ChatClient client = client(SessionAdvisors.builder(this.sessionService)
			.compaction(request -> true, WindowCompactionStrategy.builder().lastEvents(2).build())
			.memory(memory -> customized.incrementAndGet())
			.memory(memory -> memory.messageFilter(MessageFilter.byMessageType(MessageType.USER)))
			.build());
		this.model.thenAnswer("Hi!").thenAnswer("Fine.");

		say(client, "Hello");
		say(client, "How are you?");

		// Both customizers ran: only the user messages were stored, and two events fit
		// the window
		assertThat(customized).hasValue(1);
		assertThat(ScriptedChatModel.describe(new Prompt(this.sessionService.getActiveMessages(this.sessionId))))
			.containsExactly("Hello", "How are you?");
	}

	@Test
	void asyncCompactionReachesTurnAdvisor() throws Exception {
		SessionTurnAdvisorTests.HeldCompaction held = new SessionTurnAdvisorTests.HeldCompaction();
		List<Advisor> advisors = SessionAdvisors.builder(this.sessionService)
			.compaction(request -> true, held)
			.asyncCompaction(true)
			.build();
		SessionTurnAdvisor turn = (SessionTurnAdvisor) advisors.get(0);
		this.model.thenAnswer("Hi!");

		// The reply comes back while the compaction is held
		say(client(advisors), "Hello");
		assertThat(held.started.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(turn.isCompacting(this.sessionId)).isTrue();
		held.release.countDown();
		for (int i = 0; i < 100 && turn.isCompacting(this.sessionId); i++) {
			Thread.sleep(50);
		}
		assertThat(turn.isCompacting(this.sessionId)).isFalse();
	}

	private ChatClient client(List<Advisor> advisors, ToolCallback... tools) {
		return ChatClient.builder(this.model).defaultAdvisors(advisors).defaultToolCallbacks(tools).build();
	}

	private String say(ChatClient client, String text) {
		return client.prompt().user(text).advisors(SessionAdvisors.session(this.sessionId)).call().content();
	}

	@Test
	void autoRegisteredLoopRunsToolCallsThroughSession() {
		ToolCallback weather = FunctionToolCallback.builder("get_weather", (City city) -> "Sunny in " + city.city())
			.description("The weather in a city")
			.inputType(City.class)
			.build();
		ChatClient client = client(SessionAdvisors.builder(this.sessionService).build(), weather);
		this.model.thenCall("c1", "Lisbon").thenAnswer("Sunny.").thenAnswer("Fine.");

		say(client, "Weather in Lisbon?");
		say(client, "And you?");

		// The loop runs with its own history off: each round's prompt is rebuilt from the
		// session, without a blank system message
		assertThat(ScriptedChatModel.describe(new Prompt(this.sessionService.getActiveMessages(this.sessionId))))
			.containsExactly("Weather in Lisbon?", "call:c1", "result:c1", "Sunny.", "And you?", "Fine.");
		assertThat(this.model.prompts()).hasSize(3)
			.allSatisfy(prompt -> assertThat(prompt.getInstructions())
				.noneMatch(message -> message instanceof SystemMessage && message.getText().isBlank()));
		assertThat(ScriptedChatModel.describe(this.model.prompts().get(2))).containsExactly("Weather in Lisbon?",
				"call:c1", "result:c1", "Sunny.", "And you?");
	}

	@Test
	void decisionsResumeAParkedSessionThroughTheAutoRegisteredLoop() {
		ToolCallback book = FunctionToolCallback.builder("bookFlight", (City city) -> "Booked " + city.city())
			.description("Books a flight to a city")
			.inputType(City.class)
			.toolMetadata(ToolMetadata.builder().requiresConfirmation(true).build())
			.build();
		ChatClient client = client(SessionAdvisors.builder(this.sessionService).build(), book);
		this.model.then(prompt -> new ChatResponse(List.of(new Generation(AssistantMessage.builder()
			.content("")
			.toolCalls(List.of(new AssistantMessage.ToolCall("c1", "function", "bookFlight", "{\"city\":\"Lisbon\"}")))
			.build())))).thenAnswer("Booked!");
		say(client, "Book a flight to Lisbon");
		assertThat(this.sessionService.turns().status(this.sessionId).isParked()).isTrue();
		ToolCallDecisions decisions = ToolCallDecisions.of(ToolCallDecision.allow("c1"));

		Map<String, Object> context = client.prompt()
			.advisors(SessionAdvisors.decisions(this.sessionId, decisions))
			.call()
			.chatClientResponse()
			.context();

		assertThat(context).containsEntry(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, this.sessionId)
			.containsEntry(SessionTurnAdvisor.TOOL_CALL_DECISIONS_CONTEXT_KEY, decisions);
		assertThat(this.sessionService.turns().status(this.sessionId).stopReason()).isEqualTo(StopReason.END_TURN);
		assertThat(ScriptedChatModel.describe(new Prompt(this.sessionService.getActiveMessages(this.sessionId))))
			.containsExactly("Book a flight to Lisbon", "call:c1", "result:c1", "Booked!");
	}

	@Test
	void resumeContinuesAStoppedTurnThroughTheAutoRegisteredLoop() {
		ToolCallback stop = FunctionToolCallback.builder("stop", (City city) -> {
			this.sessionService.turns().interrupt(this.sessionId, "user pressed stop");
			return "stopping in " + city.city();
		}).description("Stops the turn").inputType(City.class).build();
		ChatClient client = client(SessionAdvisors.builder(this.sessionService).interruptible(true).build(), stop);
		this.model.then(prompt -> new ChatResponse(List.of(new Generation(AssistantMessage.builder()
			.content("")
			.toolCalls(List.of(new AssistantMessage.ToolCall("c1", "function", "stop", "{\"city\":\"Lisbon\"}")))
			.build())))).thenAnswer("Stopped in Lisbon.");
		assertThatThrownBy(() -> say(client, "Stop")).isInstanceOf(TurnInterruptedException.class);

		Map<String, Object> context = client.prompt()
			.advisors(SessionAdvisors.resume(this.sessionId))
			.call()
			.chatClientResponse()
			.context();

		assertThat(context).containsEntry(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, this.sessionId)
			.containsEntry(SessionTurnAdvisor.RESUME_CONTEXT_KEY, Boolean.TRUE);
		assertThat(this.sessionService.turns().status(this.sessionId).stopReason()).isEqualTo(StopReason.END_TURN);
		assertThat(ScriptedChatModel.describe(new Prompt(this.sessionService.getActiveMessages(this.sessionId))))
			.containsExactly("Stop", "call:c1", "result:c1", "Stopped in Lisbon.");
	}

	@Test
	void resumeRejectsAnEmptySessionId() {
		assertThatIllegalArgumentException().isThrownBy(() -> SessionAdvisors.resume(""));
	}

	@Test
	void decisionsRejectAnEmptySessionIdOrNullDecisions() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> SessionAdvisors.decisions("", ToolCallDecisions.of(ToolCallDecision.allow("c1"))));
		assertThatIllegalArgumentException().isThrownBy(() -> SessionAdvisors.decisions(this.sessionId, null));
	}

	record City(String city) {
	}

}
