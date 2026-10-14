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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.session.CreateSessionRequest;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.MessageFilter;
import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionAttributes;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionRepository;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.compaction.WindowCompactionStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for {@link SessionMemoryAdvisor} exercised end-to-end through a
 * Spring Boot application context. No external LLM is required — the advisor's
 * {@code before()} and {@code after()} hooks are invoked directly to verify that the
 * session is populated correctly and that conversation history is re-injected into
 * subsequent prompts.
 *
 * @author Christian Tzolov
 */
@SpringBootTest(classes = SessionMemoryAdvisorTests.TestConfig.class)
class SessionMemoryAdvisorTests {

	@Autowired
	SessionService sessionService;

	@Autowired
	SessionMemoryAdvisor advisor;

	private String sessionId;

	@BeforeEach
	void createSession() {
		Session session = this.sessionService
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "test-user").build());
		this.sessionId = session.id();
	}

	// --- Before hook ---

	@Test
	void beforeAppendsUserMessageToSession() {
		ChatClientRequest request = buildRequest(this.sessionId, "Hello, what is Spring AI?");
		AdvisorChain chain = mock(AdvisorChain.class);

		this.advisor.before(request, chain);

		List<SessionEvent> events = this.sessionService.getEvents(this.sessionId);
		assertThat(events).hasSize(1);
		assertThat(events.get(0).getMessage().getText()).isEqualTo("Hello, what is Spring AI?");
		assertThat(events.get(0).getMessageType().getValue()).isEqualTo("user");
	}

	@Test
	void beforeInjectsExistingHistoryIntoPromptMessages() {
		// Pre-populate the session with a prior turn
		this.sessionService.appendMessage(this.sessionId, new UserMessage("What is Spring?"));
		this.sessionService.appendMessage(this.sessionId, new AssistantMessage("Spring is a framework."));

		ChatClientRequest request = buildRequest(this.sessionId, "Tell me more about Spring AI.");
		AdvisorChain chain = mock(AdvisorChain.class);

		ChatClientRequest modified = this.advisor.before(request, chain);

		// The modified prompt must include the history + the new user message
		List<Message> combined = modified.prompt().getInstructions();
		assertThat(combined).hasSizeGreaterThanOrEqualTo(3);
		assertThat(combined.get(0).getText()).isEqualTo("What is Spring?");
		assertThat(combined.get(1).getText()).isEqualTo("Spring is a framework.");
		assertThat(combined.get(combined.size() - 1).getText()).isEqualTo("Tell me more about Spring AI.");
	}

	@Test
	void beforeThrowsWhenSessionIdContextKeyAbsent() {
		Prompt prompt = new Prompt(List.of(new UserMessage("hello")));
		ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).context(Map.of()).build();
		AdvisorChain chain = mock(AdvisorChain.class);

		assertThatThrownBy(() -> this.advisor.before(request, chain)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("SESSION_ID_CONTEXT_KEY");
	}

	// --- After hook ---

	@Test
	void afterAppendsAssistantMessageToSession() {
		// Simulate before() having appended the user message
		this.sessionService.appendMessage(this.sessionId, new UserMessage("What is Spring AI?"));

		ChatClientResponse response = buildResponse(this.sessionId, "Spring AI is a framework for AI apps.");
		AdvisorChain chain = mock(AdvisorChain.class);

		this.advisor.after(response, chain);

		List<SessionEvent> events = this.sessionService.getEvents(this.sessionId);
		assertThat(events).hasSize(2);
		assertThat(events.get(1).getMessage().getText()).isEqualTo("Spring AI is a framework for AI apps.");
		assertThat(events.get(1).getMessageType().getValue()).isEqualTo("assistant");
	}

	// --- Full round-trip ---

	@Test
	void multiTurnConversationBuildsUpHistory() {
		AdvisorChain chain = mock(AdvisorChain.class);

		// Turn 1
		ChatClientRequest req1 = buildRequest(this.sessionId, "What is Spring AI?");
		this.advisor.before(req1, chain);
		this.advisor.after(buildResponse(this.sessionId, "Spring AI is an AI framework."), chain);

		// Turn 2
		ChatClientRequest req2 = buildRequest(this.sessionId, "How do I use it?");
		ChatClientRequest modified2 = this.advisor.before(req2, chain);
		this.advisor.after(buildResponse(this.sessionId, "Use ChatClient."), chain);

		// After turn 2's before(), history from turn 1 should be in the prompt
		List<Message> instructions = modified2.prompt().getInstructions();
		assertThat(instructions).hasSizeGreaterThanOrEqualTo(3);
		assertThat(instructions.get(0).getText()).isEqualTo("What is Spring AI?");
		assertThat(instructions.get(1).getText()).isEqualTo("Spring AI is an AI framework.");

		// Session should now have 4 events total
		List<SessionEvent> events = this.sessionService.getEvents(this.sessionId);
		assertThat(events).hasSize(4);
	}

	@Test
	void beforeSkipsDuplicatingHistoryInToolLoopWhenSessionStoresSystemMessage() {
		// A stored system message plus the request's own system prompt: moving system
		// messages to the front leaves the request's prompt between the stored system
		// message and the rest of the history, which must not defeat the loop check.
		this.sessionService.appendMessage(this.sessionId, new SystemMessage("Answer in French."));
		this.sessionService.appendMessage(this.sessionId, new UserMessage("Hello"));
		this.sessionService.appendMessage(this.sessionId, new AssistantMessage("Bonjour !"));
		AdvisorChain chain = mock(AdvisorChain.class);
		SystemMessage requestSystem = new SystemMessage("Be brief.");
		UserMessage userMessage = new UserMessage("What is the weather in Paris?");
		AssistantMessage toolCallMessage = AssistantMessage.builder()
			.toolCalls(
					List.of(new AssistantMessage.ToolCall("call-1", "function", "get_weather", "{\"city\":\"Paris\"}")))
			.build();

		// Round 1
		List<Message> round1 = this.advisor
			.before(ChatClientRequest.builder()
				.prompt(new Prompt(List.of(requestSystem, userMessage)))
				.context(Map.of(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, this.sessionId))
				.build(), chain)
			.prompt()
			.getInstructions();
		this.advisor.after(buildResponseFromMessages(this.sessionId, toolCallMessage), chain);

		// Round 2: the looping advisor re-sends round 1's prompt plus the tool exchange.
		ToolResponseMessage toolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "get_weather", "15 degrees and sunny")))
			.build();
		List<Message> round2Input = new ArrayList<>(round1);
		round2Input.add(toolCallMessage);
		round2Input.add(toolResponse);
		List<Message> round2 = this.advisor
			.before(ChatClientRequest.builder()
				.prompt(new Prompt(round2Input))
				.context(Map.of(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, this.sessionId))
				.build(), chain)
			.prompt()
			.getInstructions();

		assertThat(round1).extracting(Message::getText)
			.containsExactly("Answer in French.", "Be brief.", "Hello", "Bonjour !", "What is the weather in Paris?");
		assertThat(round2).hasSize(7);
		assertThat(round2.subList(0, 5)).extracting(Message::getText)
			.containsExactly("Answer in French.", "Be brief.", "Hello", "Bonjour !", "What is the weather in Paris?");
		assertThat(round2.get(5)).isEqualTo(toolCallMessage);
		assertThat(round2.get(6)).isEqualTo(toolResponse);
	}

	@Test
	void beforePrependsHistoryWhenPromptOnlyMatchesItsText() {
		// The loop check compares messages by content, not equals(): a prompt message
		// with
		// the same text but a different tool call is not the stored history. Both tool
		// calls are answered, so the tool-call guard leaves them alone.
		this.sessionService.appendMessage(this.sessionId, new UserMessage("What is the weather?"));
		this.sessionService.appendMessage(this.sessionId, AssistantMessage.builder()
			.toolCalls(
					List.of(new AssistantMessage.ToolCall("call-1", "function", "get_weather", "{\"city\":\"Paris\"}")))
			.build());
		this.sessionService.appendMessage(this.sessionId, toolResponse("call-1"));
		AssistantMessage otherToolCall = AssistantMessage.builder()
			.toolCalls(
					List.of(new AssistantMessage.ToolCall("call-2", "function", "get_weather", "{\"city\":\"Rome\"}")))
			.build();

		List<Message> instructions = beforeWithPrompt(new UserMessage("What is the weather?"), otherToolCall,
				toolResponse("call-2"), new UserMessage("And tomorrow?"));

		assertThat(instructions).hasSize(7);
		assertThat(((AssistantMessage) instructions.get(1)).getToolCalls()).extracting(AssistantMessage.ToolCall::id)
			.containsExactly("call-1");
	}

	@Test
	void beforePrependsHistoryWhenPromptHasSameTextButDifferentMedia() {
		Media stored = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data(new byte[] { 1 }).build();
		Media other = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data(new byte[] { 2 }).build();
		this.sessionService.appendMessage(this.sessionId, UserMessage.builder().text("Describe").media(stored).build());
		this.sessionService.appendMessage(this.sessionId, new AssistantMessage("A cat."));

		List<Message> instructions = beforeWithPrompt(UserMessage.builder().text("Describe").media(other).build(),
				new AssistantMessage("A cat."), new UserMessage("And this one?"));

		assertThat(instructions).hasSize(5);
	}

	@Test
	void beforeAddsStoredSystemMessageWhenSessionHasNoConversationYet() {
		// Only a system message is stored: the loop check sees no conversation to skip,
		// but the stored system message must still reach the prompt.
		this.sessionService.appendMessage(this.sessionId, new SystemMessage("Answer in French."));

		List<Message> instructions = beforeWithPrompt(new UserMessage("Hi"));

		assertThat(instructions).extracting(Message::getText).containsExactly("Answer in French.", "Hi");
	}

	@Test
	void beforeSkipsDuplicatingHistoryWhenNestedInsideToolCallingLoop() {
		// Simulates a looping advisor such as ToolCallingAdvisor re-entering the chain
		// once per tool-call round (see the class Javadoc "Nesting inside a
		// tool-calling loop" section). From round 2 onward, the incoming prompt
		// already carries this turn's messages -- the same messages round 1's
		// before()/after() just persisted -- so before() must not prepend them again.
		AdvisorChain chain = mock(AdvisorChain.class);

		UserMessage userMessage = new UserMessage("What is the weather in Paris?");
		AssistantMessage toolCallMessage = AssistantMessage.builder()
			.toolCalls(
					List.of(new AssistantMessage.ToolCall("call-1", "function", "get_weather", "{\"city\":\"Paris\"}")))
			.build();

		// Round 1: only the user message is in the prompt.
		this.advisor.before(buildRequest(this.sessionId, userMessage.getText()), chain);
		this.advisor.after(buildResponseFromMessages(this.sessionId, toolCallMessage), chain);

		// Round 2: the prompt already contains this turn's user message and tool-call
		// message, plus the fresh tool response.
		ToolResponseMessage toolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "get_weather", "15 degrees and sunny")))
			.build();
		Prompt round2Prompt = new Prompt(List.of(userMessage, toolCallMessage, toolResponse));
		ChatClientRequest round2Request = ChatClientRequest.builder()
			.prompt(round2Prompt)
			.context(Map.of(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, this.sessionId))
			.build();

		ChatClientRequest modified = this.advisor.before(round2Request, chain);

		// No duplication: the user message and tool-call message must not appear twice.
		List<Message> instructions = modified.prompt().getInstructions();
		assertThat(instructions).containsExactly(userMessage, toolCallMessage, toolResponse);

		// The tool response is persisted for the first time; the user/assistant pair
		// from round 1 is not persisted again.
		List<SessionEvent> events = this.sessionService.getEvents(this.sessionId);
		assertThat(events).hasSize(3);
		assertThat(events.get(2).getMessage()).isEqualTo(toolResponse);
	}

	@Test
	void beforePromotesAllSystemMessagesToFront() {
		// Simulate a session whose history already contains a system message (e.g. from
		// a previous turn that was stored), plus the current request also carries one.
		this.sessionService.appendMessage(this.sessionId, new SystemMessage("You are a helpful assistant."));
		this.sessionService.appendMessage(this.sessionId, new UserMessage("Hello"));
		this.sessionService.appendMessage(this.sessionId, new AssistantMessage("Hi!"));

		// Current request has its own system message
		Prompt prompt = new Prompt(
				List.of(new SystemMessage("Always reply in English."), new UserMessage("How are you?")));
		ChatClientRequest request = ChatClientRequest.builder()
			.prompt(prompt)
			.context(Map.of(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, this.sessionId))
			.build();
		AdvisorChain chain = mock(AdvisorChain.class);

		ChatClientRequest modified = this.advisor.before(request, chain);

		List<Message> instructions = modified.prompt().getInstructions();

		// Both system messages must be at the front — neither stranded mid-list
		assertThat(instructions.get(0)).isInstanceOf(SystemMessage.class);
		assertThat(instructions.get(1)).isInstanceOf(SystemMessage.class);
		// Non-system messages follow
		instructions.subList(2, instructions.size()).forEach(m -> assertThat(m).isNotInstanceOf(SystemMessage.class));
	}

	@Test
	void beforeSendsStoredSystemMessageIdenticalToRequestOneOnlyOnce() {
		this.sessionService.appendMessage(this.sessionId, new SystemMessage("Answer in French."));
		this.sessionService.appendMessage(this.sessionId, new UserMessage("Hello"));
		this.sessionService.appendMessage(this.sessionId, new AssistantMessage("Bonjour !"));

		List<Message> instructions = beforeWithPrompt(new SystemMessage("Answer in French."),
				new SystemMessage("Be brief."), new UserMessage("How are you?"));

		assertThat(instructions).filteredOn(SystemMessage.class::isInstance)
			.extracting(Message::getText)
			.containsExactly("Answer in French.", "Be brief.");
		assertThat(instructions.subList(0, 2)).allMatch(SystemMessage.class::isInstance);
		assertThat(instructions).extracting(Message::getText)
			.containsExactly("Answer in French.", "Be brief.", "Hello", "Bonjour !", "How are you?");
	}

	@Test
	void beforeSendsOnlyLatestStoredSystemMessage() {
		this.sessionService.appendMessage(this.sessionId, new SystemMessage("Answer in French."));
		this.sessionService.appendMessage(this.sessionId, new UserMessage("Hello"));
		this.sessionService.appendMessage(this.sessionId, new AssistantMessage("Bonjour !"));
		this.sessionService.appendMessage(this.sessionId, new SystemMessage("Answer in German."));

		List<Message> instructions = beforeWithPrompt(new UserMessage("How are you?"));

		assertThat(instructions).extracting(Message::getText)
			.containsExactly("Answer in German.", "Hello", "Bonjour !", "How are you?");
	}

	@Test
	void storedSystemPromptStillAppliesAfterCompaction() {
		// The system prompt was stored in turn 1; after later turns have been compacted
		// it
		// must still apply.
		this.sessionService.appendMessage(this.sessionId, new UserMessage("Research Paris."));
		this.sessionService.appendMessage(this.sessionId, new SystemMessage("Researcher rules."));
		this.sessionService.appendMessage(this.sessionId, new AssistantMessage("Paris findings."));
		for (int i = 2; i <= 4; i++) {
			this.sessionService.appendMessage(this.sessionId, new UserMessage("question " + i));
			this.sessionService.appendMessage(this.sessionId, new AssistantMessage("answer " + i));
		}
		this.sessionService.compact(this.sessionId, request -> true,
				WindowCompactionStrategy.builder().lastEvents(2).build());
		List<Message> instructions = beforeWithPrompt(new UserMessage("Research Rome."));

		assertThat(instructions).filteredOn(SystemMessage.class::isInstance)
			.extracting(Message::getText)
			.containsExactly("Researcher rules.");
	}

	@Test
	void onlyLatestStoredSystemMessageSurvivesCompactionThatKeepsOlderOneInItsWindow() {
		// sys-old sits inside the window the compaction keeps; it is superseded by
		// sys-new,
		// so the compaction covers it and the advisor sends sys-new alone
		this.sessionService.appendMessage(this.sessionId, new UserMessage("q1"));
		this.sessionService.appendMessage(this.sessionId, new AssistantMessage("a1"));
		this.sessionService.appendMessage(this.sessionId, new UserMessage("q2"));
		this.sessionService.appendMessage(this.sessionId, new SystemMessage("sys-old"));
		this.sessionService.appendMessage(this.sessionId, new AssistantMessage("a2"));
		this.sessionService.appendMessage(this.sessionId, new UserMessage("q3"));
		this.sessionService.appendMessage(this.sessionId, new SystemMessage("sys-new"));
		this.sessionService.appendMessage(this.sessionId, new AssistantMessage("a3"));
		this.sessionService.compact(this.sessionId, request -> true,
				WindowCompactionStrategy.builder().lastEvents(4).build());

		List<Message> instructions = beforeWithPrompt(new UserMessage("q4"));

		assertThat(instructions).extracting(Message::getText).containsExactly("sys-new", "q2", "a2", "q3", "a3", "q4");
	}

	@Test
	void systemMessageInsideActiveWindowIsSentFirst() {
		// Compaction keeps the system message where it was stored, in the middle of the
		// active window; the advisor still sends it first.
		this.sessionService.appendMessage(this.sessionId, new UserMessage("question 1"));
		this.sessionService.appendMessage(this.sessionId, new AssistantMessage("answer 1"));
		this.sessionService.appendMessage(this.sessionId, new UserMessage("question 2"));
		this.sessionService.appendMessage(this.sessionId, new SystemMessage("Answer in French."));
		this.sessionService.appendMessage(this.sessionId, new AssistantMessage("answer 2"));
		this.sessionService.compact(this.sessionId, request -> true,
				WindowCompactionStrategy.builder().lastEvents(2).build());

		List<Message> instructions = beforeWithPrompt(new UserMessage("question 3"));

		assertThat(instructions).extracting(Message::getText)
			.containsExactly("Answer in French.", "question 2", "answer 2", "question 3");
	}

	@Test
	void beforeKeepsDistinctSystemMessagesInOrder() {
		this.sessionService.appendMessage(this.sessionId, new SystemMessage("Stored rule."));

		List<Message> instructions = beforeWithPrompt(new SystemMessage("Request rule."), new UserMessage("Hi"));

		assertThat(instructions).extracting(Message::getText).containsExactly("Stored rule.", "Request rule.", "Hi");
	}

	private List<Message> beforeWithPrompt(Message... messages) {
		ChatClientRequest request = ChatClientRequest.builder()
			.prompt(new Prompt(List.of(messages)))
			.context(Map.of(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, this.sessionId))
			.build();
		return this.advisor.before(request, mock(AdvisorChain.class)).prompt().getInstructions();
	}

	// --- Session metadata on first use ---

	@Test
	void beforeCreatesSessionWithContextMetadata() {
		String newId = "created-with-metadata-" + System.nanoTime();
		ChatClientRequest request = buildRequestWithMetadata(newId, "hello", Map.of("agent", "support"));

		this.advisor.before(request, mock(AdvisorChain.class));

		Session created = this.sessionService.findById(newId);
		assertThat(created).isNotNull();
		assertThat(created.metadata()).containsEntry("agent", "support");
	}

	@Test
	void beforeIgnoresContextMetadataForExistingSession() {
		ChatClientRequest request = buildRequestWithMetadata(this.sessionId, "hello", Map.of("agent", "support"));

		this.advisor.before(request, mock(AdvisorChain.class));

		assertThat(this.sessionService.findById(this.sessionId).metadata()).doesNotContainKey("agent");
	}

	@Test
	void beforeRejectsContextMetadataValueThatIsNotMap() {
		ChatClientRequest request = ChatClientRequest.builder()
			.prompt(new Prompt(List.of(new UserMessage("hello"))))
			.context(Map.of(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, "new-" + System.nanoTime(),
					SessionMemoryAdvisor.SESSION_METADATA_CONTEXT_KEY, "not a map"))
			.build();

		assertThatThrownBy(() -> this.advisor.before(request, mock(AdvisorChain.class)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining(SessionMemoryAdvisor.SESSION_METADATA_CONTEXT_KEY);
	}

	// --- Per-request EventFilter override ---

	@Test
	void requestEventFilterOverridesAdvisorDefault() {
		// Populate 4 messages
		this.sessionService.appendMessage(this.sessionId, new UserMessage("msg1"));
		this.sessionService.appendMessage(this.sessionId, new AssistantMessage("reply1"));
		this.sessionService.appendMessage(this.sessionId, new UserMessage("msg2"));
		this.sessionService.appendMessage(this.sessionId, new AssistantMessage("reply2"));

		// Advisor has no filter (all), but request narrows to lastN=2
		Prompt prompt = new Prompt(List.of(new UserMessage("new question")));
		ChatClientRequest request = ChatClientRequest.builder()
			.prompt(prompt)
			.context(Map.of(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, this.sessionId,
					SessionMemoryAdvisor.EVENT_FILTER_CONTEXT_KEY, EventFilter.lastN(2)))
			.build();

		ChatClientRequest modified = this.advisor.before(request, mock(AdvisorChain.class));

		List<String> texts = modified.prompt().getInstructions().stream().map(Message::getText).toList();
		assertThat(texts).contains("msg2", "reply2");
		assertThat(texts).doesNotContain("msg1", "reply1");
	}

	@Test
	void nullRequestEventFilterIsIgnoredAndAdvisorDefaultIsUsed() {
		this.sessionService.appendMessage(this.sessionId, new UserMessage("only message"));

		Prompt prompt = new Prompt(List.of(new UserMessage("follow-up")));
		Map<String, Object> ctx = new java.util.HashMap<>();
		ctx.put(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, this.sessionId);
		ctx.put(SessionMemoryAdvisor.EVENT_FILTER_CONTEXT_KEY, null);
		ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).context(ctx).build();

		ChatClientRequest modified = this.advisor.before(request, mock(AdvisorChain.class));

		List<String> texts = modified.prompt().getInstructions().stream().map(Message::getText).toList();
		assertThat(texts).contains("only message");
	}

	// --- Empty assistant message filtering ---

	@Test
	void afterDoesNotStoreAssistantMessageWithBlankText() {
		// Bedrock Converse emits AssistantMessage("") to signal end_turn after tool use.
		// It must not be stored; replaying it on the next request causes a
		// ValidationException on Bedrock.
		ChatClientResponse response = buildResponseFromMessages(this.sessionId, new AssistantMessage(""));
		this.advisor.after(response, mock(AdvisorChain.class));

		assertThat(this.sessionService.getEvents(this.sessionId)).isEmpty();
	}

	@Test
	void afterDoesNotStoreAssistantMessageWithWhitespaceOnlyText() {
		ChatClientResponse response = buildResponseFromMessages(this.sessionId, new AssistantMessage("   "));
		this.advisor.after(response, mock(AdvisorChain.class));

		assertThat(this.sessionService.getEvents(this.sessionId)).isEmpty();
	}

	@Test
	void afterStoresAssistantMessageWithText() {
		// Regression guard: normal assistant messages must still be stored.
		ChatClientResponse response = buildResponse(this.sessionId, "Paris is the capital of France.");
		this.advisor.after(response, mock(AdvisorChain.class));

		List<SessionEvent> events = this.sessionService.getEvents(this.sessionId);
		assertThat(events).hasSize(1);
		assertThat(events.get(0).getMessage().getText()).isEqualTo("Paris is the capital of France.");
	}

	@Test
	void afterStoresAssistantMessageWithToolCallsEvenWhenTextIsBlank() {
		// An AssistantMessage with tool calls but no text is a real tool-invocation
		// event — it must not be filtered out.
		AssistantMessage withToolCalls = AssistantMessage.builder()
			.toolCalls(
					List.of(new AssistantMessage.ToolCall("call-1", "function", "get_weather", "{\"city\":\"Paris\"}")))
			.build();
		ChatClientResponse response = buildResponseFromMessages(this.sessionId, withToolCalls);
		this.advisor.after(response, mock(AdvisorChain.class));

		List<SessionEvent> events = this.sessionService.getEvents(this.sessionId);
		assertThat(events).hasSize(1);
		assertThat(events.get(0).hasToolCalls()).isTrue();
	}

	@Test
	void afterStoresOnlyNonEmptyMessagesFromMultiGenerationResponse() {
		// Bedrock scenario: the model response contains two generations — the real
		// tool-call invocation followed by an empty end_turn frame. Only the tool-call
		// generation must be persisted.
		AssistantMessage toolCallMessage = AssistantMessage.builder()
			.toolCalls(
					List.of(new AssistantMessage.ToolCall("call-1", "function", "get_weather", "{\"city\":\"Paris\"}")))
			.build();
		AssistantMessage emptyEndTurn = new AssistantMessage("");

		ChatClientResponse response = buildResponseFromMessages(this.sessionId, toolCallMessage, emptyEndTurn);
		this.advisor.after(response, mock(AdvisorChain.class));

		List<SessionEvent> events = this.sessionService.getEvents(this.sessionId);
		assertThat(events).hasSize(1);
		assertThat(events.get(0).hasToolCalls()).isTrue();
	}

	@Test
	void emptyEndTurnMessageIsNotReplayedOnNextTurn() {
		// Full Bedrock tool-call round-trip:
		// turn 1 before() → user message stored
		// turn 1 after() → ASSISTANT[tool calls] stored, ASSISTANT[""] skipped
		// turn 2 before() → history injected into prompt must not contain the empty frame
		AdvisorChain chain = mock(AdvisorChain.class);

		// Turn 1
		this.advisor.before(buildRequest(this.sessionId, "What is the weather in Paris?"), chain);

		AssistantMessage toolCallMessage = AssistantMessage.builder()
			.toolCalls(
					List.of(new AssistantMessage.ToolCall("call-1", "function", "get_weather", "{\"city\":\"Paris\"}")))
			.build();
		AssistantMessage emptyEndTurn = new AssistantMessage("");
		this.advisor.after(buildResponseFromMessages(this.sessionId, toolCallMessage, emptyEndTurn), chain);

		// Turn 2 — inspect the prompt that the advisor builds
		ChatClientRequest modified = this.advisor.before(buildRequest(this.sessionId, "Thanks!"), chain);

		List<String> texts = modified.prompt().getInstructions().stream().map(Message::getText).toList();
		// The empty end_turn frame must not appear in the injected history
		assertThat(texts).doesNotContain("");
		assertThat(texts).doesNotContain("   ");
		// Real events are still present
		assertThat(texts).contains("What is the weather in Paris?");
	}

	// --- Multimodal assistant message filtering ---

	@Test
	void afterStoresAssistantMessageWithMediaEvenWhenTextIsBlank() {
		// A multimodal model may respond with an image (or other media) and no text.
		// That is a valid, content-bearing message and must NOT be dropped.
		Media image = new Media(MimeTypeUtils.IMAGE_PNG, URI.create("https://example.com/image.png"));
		AssistantMessage withMedia = AssistantMessage.builder().media(List.of(image)).build();
		ChatClientResponse response = buildResponseFromMessages(this.sessionId, withMedia);
		this.advisor.after(response, mock(AdvisorChain.class));

		List<SessionEvent> events = this.sessionService.getEvents(this.sessionId);
		assertThat(events).hasSize(1);
		assertThat(events.get(0).getMessage()).isInstanceOf(AssistantMessage.class);
		assertThat(((AssistantMessage) events.get(0).getMessage()).getMedia()).hasSize(1);
	}

	@Test
	void afterStoresAssistantMessageWithMediaAndToolCallsEvenWhenTextIsBlank() {
		Media image = new Media(MimeTypeUtils.IMAGE_PNG, URI.create("https://example.com/image.png"));
		AssistantMessage withBoth = AssistantMessage.builder()
			.media(List.of(image))
			.toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "describe_image", "{}")))
			.build();
		ChatClientResponse response = buildResponseFromMessages(this.sessionId, withBoth);
		this.advisor.after(response, mock(AdvisorChain.class));

		List<SessionEvent> events = this.sessionService.getEvents(this.sessionId);
		assertThat(events).hasSize(1);
		assertThat(events.get(0).hasToolCalls()).isTrue();
	}

	@Test
	void afterDropsAssistantMessageWithNullTextNoToolCallsAndNoMedia() {
		// Explicit null text (not just blank) with no other content — must be dropped.
		AssistantMessage nullText = AssistantMessage.builder().build();
		ChatClientResponse response = buildResponseFromMessages(this.sessionId, nullText);
		this.advisor.after(response, mock(AdvisorChain.class));

		assertThat(this.sessionService.getEvents(this.sessionId)).isEmpty();
	}

	// --- MessageFilter ---

	@Test
	void customMessageFilterBlocksUserMessagePersistenceInBefore() {
		// A filter rejecting USER messages must prevent persistence in before(), while
		// the outgoing prompt still carries the user message.
		SessionMemoryAdvisor filteringAdvisor = SessionMemoryAdvisor.builder(this.sessionService)
			.messageFilter(msg -> msg.getMessageType() != MessageType.USER)
			.build();

		ChatClientRequest request = buildRequest(this.sessionId, "do not store me");
		ChatClientRequest modified = filteringAdvisor.before(request, mock(AdvisorChain.class));

		assertThat(this.sessionService.getEvents(this.sessionId)).isEmpty();
		List<String> texts = modified.prompt().getInstructions().stream().map(Message::getText).toList();
		assertThat(texts).contains("do not store me");
	}

	@Test
	void toolMessagesAreStoredWhateverMessageFilterSays() {
		// A filter that keeps only user and assistant text cannot split a tool call from
		// its result: both are always stored; the prompt hides them with an EventFilter
		SessionMemoryAdvisor filteringAdvisor = SessionMemoryAdvisor.builder(this.sessionService)
			.messageFilter(MessageFilter.byMessageType(MessageType.USER)
				.or(message -> message instanceof AssistantMessage assistant && !assistant.hasToolCalls()))
			.build();
		AdvisorChain chain = mock(AdvisorChain.class);
		UserMessage user = new UserMessage("Weather in Paris?");
		AssistantMessage call = toolCall("call-1");
		filteringAdvisor.before(buildRequest(this.sessionId, user.getText()), chain);
		filteringAdvisor.after(buildResponseFromMessages(this.sessionId, call), chain);

		ChatClientRequest round2 = ChatClientRequest.builder()
			.prompt(new Prompt(List.of(user, call, toolResponse("call-1"))))
			.context(Map.of(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, this.sessionId))
			.build();
		filteringAdvisor.before(round2, chain);

		assertThat(this.sessionService.getEvents(this.sessionId)).extracting(SessionEvent::getMessageType)
			.containsExactly(MessageType.USER, MessageType.ASSISTANT, MessageType.TOOL);
	}

	@Test
	void replyWithToolCallsIsStoredWholeEvenWhenFilterRejectsItsText() {
		SessionMemoryAdvisor filteringAdvisor = SessionMemoryAdvisor.builder(this.sessionService)
			.messageFilter(MessageFilter.containsText("confidential").negate())
			.build();
		AdvisorChain chain = mock(AdvisorChain.class);
		AssistantMessage call = AssistantMessage.builder()
			.content("Checking the confidential report.")
			.toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "read_report", "{}")))
			.build();

		filteringAdvisor.after(buildResponseFromMessages(this.sessionId, call), chain);
		filteringAdvisor.after(buildResponse(this.sessionId, "The confidential figures are up."), chain);

		// The call is kept whole, so its result can follow; the plain text is filtered
		List<SessionEvent> events = this.sessionService.getEvents(this.sessionId);
		assertThat(events).hasSize(1);
		assertThat(events.get(0).hasToolCalls()).isTrue();
		assertThat(events.get(0).getMessage().getText()).isEqualTo("Checking the confidential report.");
	}

	@Test
	void customMessageFilterAppliedInAfter() {
		// A content-based filter must gate which generations get persisted.
		SessionMemoryAdvisor filteringAdvisor = SessionMemoryAdvisor.builder(this.sessionService)
			.messageFilter(MessageFilter.containsText("confidential").negate())
			.build();

		ChatClientResponse response = buildResponseFromMessages(this.sessionId,
				new AssistantMessage("this is confidential data"), new AssistantMessage("public answer"));
		filteringAdvisor.after(response, mock(AdvisorChain.class));

		List<SessionEvent> events = this.sessionService.getEvents(this.sessionId);
		assertThat(events).hasSize(1);
		assertThat(events.get(0).getMessage().getText()).isEqualTo("public answer");
	}

	@Test
	void composedCustomFilterStillSkipsEmptyAssistantMessages() {
		// Composing a custom filter with skipEmptyMessages() preserves the
		// empty end_turn protection (compose, don't replace).
		SessionMemoryAdvisor filteringAdvisor = SessionMemoryAdvisor.builder(this.sessionService)
			.messageFilter(MessageFilter.byMessageType(MessageType.USER, MessageType.ASSISTANT)
				.and(MessageFilter.skipEmptyMessages()))
			.build();

		ChatClientResponse response = buildResponseFromMessages(this.sessionId, new AssistantMessage(""));
		filteringAdvisor.after(response, mock(AdvisorChain.class));

		assertThat(this.sessionService.getEvents(this.sessionId)).isEmpty();
	}

	// --- Pluggable event id generation ---

	@Test
	void repeatedCallsStoreDistinctEvents() {
		// Regression guard: without a custom generator, retried calls with identical
		// content must NOT be deduped -- the random default must keep producing fresh
		// ids, matching the pre-existing appendMessage() behaviour.
		ChatClientRequest request = buildRequest(this.sessionId, "hello");
		AdvisorChain chain = mock(AdvisorChain.class);

		this.advisor.before(request, chain);
		this.advisor.before(request, chain);

		assertThat(this.sessionService.getEvents(this.sessionId)).hasSize(2);
	}

	// --- Builder validation ---

	@Test
	void builderRejectsNullMessageFilter() {
		assertThatThrownBy(() -> SessionMemoryAdvisor.builder(this.sessionService).messageFilter(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("messageFilter must not be null");
	}

	// --- Tool-call integrity ---

	@Test
	void lastNWindowNeverSplitsToolCallFromItsResult() {
		// #44: a lastN window that landed on the tool result used to send an assistant
		// tool call without the tool message answering it, which providers reject.
		storeToolTurn(this.sessionId, "Weather in Paris?", "call-1", "Sunny.");
		SessionMemoryAdvisor windowed = SessionMemoryAdvisor.builder(this.sessionService)
			.eventFilter(EventFilter.lastN(2))
			.build();

		ChatClientRequest modified = windowed.before(buildRequest(this.sessionId, "And tomorrow?"),
				mock(AdvisorChain.class));

		assertThat(describe(modified.prompt().getInstructions())).containsExactly("Weather in Paris?", "call:call-1",
				"result:call-1", "Sunny.", "And tomorrow?");
	}

	@Test
	void messageTypesFilterHidingToolResultsDropsStrandedToolCall() {
		storeToolTurn(this.sessionId, "Weather in Paris?", "call-1", "Sunny.");
		SessionMemoryAdvisor noToolMessages = SessionMemoryAdvisor.builder(this.sessionService)
			.eventFilter(EventFilter.builder().messageTypes(Set.of(MessageType.USER, MessageType.ASSISTANT)).build())
			.build();

		ChatClientRequest modified = noToolMessages.before(buildRequest(this.sessionId, "And tomorrow?"),
				mock(AdvisorChain.class));

		assertThat(describe(modified.prompt().getInstructions())).containsExactly("Weather in Paris?", "Sunny.",
				"And tomorrow?");
	}

	@Test
	void toolCallLeftByInterruptedTurnIsDroppedFromPrompt() {
		// The process died between the tool call and its result
		this.sessionService.appendMessage(this.sessionId, new UserMessage("Weather in Paris?"));
		this.sessionService.appendMessage(this.sessionId, toolCall("call-1"));

		ChatClientRequest modified = this.advisor.before(buildRequest(this.sessionId, "Are you there?"),
				mock(AdvisorChain.class));

		assertThat(describe(modified.prompt().getInstructions())).containsExactly("Weather in Paris?",
				"Are you there?");
	}

	@Test
	void toolCallAnsweredByPromptItselfIsKept() {
		// Round 2 of a tool loop with the looping advisor's history disabled: the prompt
		// carries only the tool result, the call it answers is the last stored event
		this.sessionService.appendMessage(this.sessionId, new UserMessage("Weather in Paris?"));
		this.sessionService.appendMessage(this.sessionId, toolCall("call-1"));
		ChatClientRequest round2 = ChatClientRequest.builder()
			.prompt(new Prompt(List.of(toolResponse("call-1"))))
			.context(Map.of(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, this.sessionId))
			.build();

		ChatClientRequest modified = this.advisor.before(round2, mock(AdvisorChain.class));

		assertThat(describe(modified.prompt().getInstructions())).containsExactly("Weather in Paris?", "call:call-1",
				"result:call-1");
		assertThat(this.sessionService.getEvents(this.sessionId)).hasSize(3);
	}

	@Test
	void parallelToolCallsAreKeptWhenFullyAnsweredAndDroppedWhenPartiallyAnswered() {
		AssistantMessage twoCalls = AssistantMessage.builder()
			.toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "get_weather", "{}"),
					new AssistantMessage.ToolCall("call-2", "function", "get_time", "{}")))
			.build();
		this.sessionService.appendMessage(this.sessionId, new UserMessage("Weather and time?"));
		this.sessionService.appendMessage(this.sessionId, twoCalls);
		this.sessionService.appendMessage(this.sessionId,
				ToolResponseMessage.builder()
					.responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "get_weather", "Sunny"),
							new ToolResponseMessage.ToolResponse("call-2", "get_time", "noon")))
					.build());
		this.sessionService.appendMessage(this.sessionId, new AssistantMessage("Sunny, at noon."));

		ChatClientRequest full = this.advisor.before(buildRequest(this.sessionId, "Thanks"), mock(AdvisorChain.class));
		assertThat(describe(full.prompt().getInstructions())).containsExactly("Weather and time?", "call:call-1",
				"result:call-1", "Sunny, at noon.", "Thanks");

		String partial = this.sessionService
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "test-user").build())
			.id();
		this.sessionService.appendMessage(partial, new UserMessage("Weather and time?"));
		this.sessionService.appendMessage(partial, twoCalls);
		this.sessionService.appendMessage(partial, toolResponse("call-1"));
		this.sessionService.appendMessage(partial, new AssistantMessage("Sunny."));

		ChatClientRequest halved = this.advisor.before(buildRequest(partial, "Thanks"), mock(AdvisorChain.class));
		assertThat(describe(halved.prompt().getInstructions())).containsExactly("Weather and time?", "Sunny.",
				"Thanks");
	}

	@Test
	void storedSystemPromptIsKeptUnderLastNWindow() {
		this.sessionService.appendMessage(this.sessionId, new SystemMessage("Be brief."));
		for (int i = 1; i <= 3; i++) {
			this.sessionService.appendMessage(this.sessionId, new UserMessage("q" + i));
			this.sessionService.appendMessage(this.sessionId, new AssistantMessage("a" + i));
		}
		SessionMemoryAdvisor windowed = SessionMemoryAdvisor.builder(this.sessionService)
			.eventFilter(EventFilter.lastN(2))
			.build();

		ChatClientRequest modified = windowed.before(buildRequest(this.sessionId, "q4"), mock(AdvisorChain.class));

		assertThat(describe(modified.prompt().getInstructions())).containsExactly("Be brief.", "q3", "a3", "q4");
	}

	private void storeToolTurn(String sessionId, String question, String callId, String answer) {
		this.sessionService.appendMessage(sessionId, new UserMessage(question));
		this.sessionService.appendMessage(sessionId, toolCall(callId));
		this.sessionService.appendMessage(sessionId, toolResponse(callId));
		this.sessionService.appendMessage(sessionId, new AssistantMessage(answer));
	}

	private static AssistantMessage toolCall(String callId) {
		return AssistantMessage.builder()
			.toolCalls(
					List.of(new AssistantMessage.ToolCall(callId, "function", "get_weather", "{\"city\":\"Paris\"}")))
			.build();
	}

	private static ToolResponseMessage toolResponse(String callId) {
		return ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse(callId, "get_weather", "Sunny")))
			.build();
	}

	/** The text, "call:id" for an assistant tool call, "result:id" for a tool result. */
	private static List<String> describe(List<Message> messages) {
		return messages.stream().map(m -> {
			if (m instanceof AssistantMessage assistant && assistant.hasToolCalls()) {
				return "call:" + assistant.getToolCalls().get(0).id();
			}
			if (m instanceof ToolResponseMessage responses) {
				return "result:" + responses.getResponses().get(0).id();
			}
			return m.getText();
		}).toList();
	}

	private static ChatClientRequest buildRequest(String sessionId, String userText) {
		Prompt prompt = new Prompt(List.of(new UserMessage(userText)));
		return ChatClientRequest.builder()
			.prompt(prompt)
			.context(Map.of(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, sessionId))
			.build();
	}

	private static ChatClientRequest buildRequestWithMetadata(String sessionId, String userText,
			Map<String, Object> metadata) {
		Prompt prompt = new Prompt(List.of(new UserMessage(userText)));
		return ChatClientRequest.builder()
			.prompt(prompt)
			.context(Map.of(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, sessionId,
					SessionMemoryAdvisor.SESSION_METADATA_CONTEXT_KEY, metadata))
			.build();
	}

	private static ChatClientResponse buildResponse(String sessionId, String assistantText) {
		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(new AssistantMessage(assistantText))))
			.build();
		return ChatClientResponse.builder()
			.chatResponse(chatResponse)
			.context(Map.of(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, sessionId))
			.build();
	}

	/**
	 * Builds a response whose {@code ChatResponse} contains one {@code Generation} per
	 * supplied {@link AssistantMessage}. Use this to simulate multi-generation responses
	 * such as the Bedrock Converse end_turn pattern (tool-call frame + empty frame).
	 */
	private static ChatClientResponse buildResponseFromMessages(String sessionId, AssistantMessage... messages) {
		List<Generation> generations = List.of(messages).stream().map(Generation::new).toList();
		ChatResponse chatResponse = ChatResponse.builder().generations(generations).build();
		return ChatClientResponse.builder()
			.chatResponse(chatResponse)
			.context(Map.of(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, sessionId))
			.build();
	}

	private SessionMemoryAdvisor memoryAdvisor() {
		return SessionMemoryAdvisor.builder(this.sessionService).build();
	}

	// --- Agent attributes ---

	@Test
	void sessionCreatedOnFirstUseCarriesAgentAttributesOfRequestMetadata() {
		AdvisorChain chain = mock(AdvisorChain.class);
		String fresh = "fresh-" + UUID.randomUUID();
		this.advisor.before(buildRequestWithMetadata(fresh, "Hi",
				Map.of(SessionAttributes.AGENT_NAME, "support", SessionAttributes.AGENT_MODEL, "claude-sonnet-5-5")),
				chain);

		Session created = this.sessionService.findById(fresh);
		assertThat(SessionAttributes.agentName(created)).isEqualTo("support");
		assertThat(SessionAttributes.agentModel(created)).isEqualTo("claude-sonnet-5-5");
		// An existing session is left alone
		this.advisor.before(
				buildRequestWithMetadata(this.sessionId, "Hi", Map.of(SessionAttributes.AGENT_NAME, "billing")), chain);
		assertThat(SessionAttributes.agentName(this.sessionService.findById(this.sessionId))).isNull();
	}

	// --- Event ids on the context (#20) ---

	@Test
	void advisorNamesEventsItStoredOnContext() {
		SessionMemoryAdvisor advisor = memoryAdvisor();
		AdvisorChain chain = mock(AdvisorChain.class);

		ChatClientRequest forwarded = advisor.before(buildRequest(this.sessionId, "Hi"), chain);
		ChatClientResponse reply = advisor.after(buildResponse(this.sessionId, "Hello"), chain);

		List<SessionEvent> messages = this.sessionService.getEvents(this.sessionId, EventFilter.active());
		assertThat(forwarded.context()).containsEntry(SessionMemoryAdvisor.REQUEST_EVENT_ID_CONTEXT_KEY,
				messages.get(0).getId());
		assertThat(reply.context()).containsEntry(SessionMemoryAdvisor.RESPONSE_EVENT_IDS_CONTEXT_KEY,
				List.of(messages.get(1).getId()));
		// Nothing stored: no request key (a null would break the model call advisor's
		// copy
		// of the context), the response list empty
		ChatClientRequest empty = advisor.before(ChatClientRequest.builder()
			.prompt(new Prompt(List.of()))
			.context(Map.of(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, this.sessionId))
			.build(), chain);
		assertThat(empty.context()).doesNotContainKey(SessionMemoryAdvisor.REQUEST_EVENT_ID_CONTEXT_KEY);
		ChatClientResponse blank = advisor.after(buildResponse(this.sessionId, "   "), chain);
		assertThat(blank.context()).containsEntry(SessionMemoryAdvisor.RESPONSE_EVENT_IDS_CONTEXT_KEY, List.of());
	}

	@Test
	void eventIdsAreSetWithoutTouchingUnmodifiableContext() {
		SessionMemoryAdvisor advisor = memoryAdvisor();
		AdvisorChain chain = mock(AdvisorChain.class);
		ChatClientRequest frozen = new ChatClientRequest(new Prompt(List.of(new UserMessage("Hi"))),
				Map.of(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, this.sessionId));

		ChatClientRequest forwarded = advisor.before(frozen, chain);
		ChatClientResponse reply = advisor.after(new ChatClientResponse(
				ChatResponse.builder().generations(List.of(new Generation(new AssistantMessage("Hello")))).build(),
				Map.of(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, this.sessionId)), chain);

		assertThat(forwarded.context().get(SessionMemoryAdvisor.REQUEST_EVENT_ID_CONTEXT_KEY)).isNotNull();
		assertThat(reply.context().get(SessionMemoryAdvisor.RESPONSE_EVENT_IDS_CONTEXT_KEY)).asList().hasSize(1);
		assertThat(frozen.context()).doesNotContainKey(SessionMemoryAdvisor.REQUEST_EVENT_ID_CONTEXT_KEY);
	}

	// --- Spring Boot configuration ---

	@Test
	void storedSystemPromptSurvivesMessageTypesFilter() {
		this.sessionService.appendMessage(this.sessionId, new SystemMessage("Answer in French."));
		this.sessionService.appendMessage(this.sessionId, new UserMessage("Hello"));
		this.sessionService.appendMessage(this.sessionId, new AssistantMessage("Bonjour !"));
		SessionMemoryAdvisor filtered = SessionMemoryAdvisor.builder(this.sessionService)
			.eventFilter(EventFilter.builder().messageTypes(Set.of(MessageType.USER, MessageType.ASSISTANT)).build())
			.build();
		ChatClientRequest request = ChatClientRequest.builder()
			.prompt(new Prompt(List.of(new UserMessage("Again?"))))
			.context(Map.of(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, this.sessionId))
			.build();

		List<Message> instructions = filtered.before(request, mock(AdvisorChain.class)).prompt().getInstructions();

		assertThat(instructions).extracting(Message::getText)
			.containsExactly("Answer in French.", "Hello", "Bonjour !", "Again?");
	}

	@SpringBootConfiguration
	static class TestConfig {

		@Bean
		SessionRepository sessionRepository() {
			return InMemorySessionRepository.builder().build();
		}

		@Bean
		SessionService sessionService(SessionRepository sessionRepository) {
			// Some tests store system messages on purpose (session setup).
			return DefaultSessionService.builder()
				.sessionRepository(sessionRepository)
				.allowSystemMessages(true)
				.build();
		}

		@Bean
		SessionMemoryAdvisor sessionMemoryAdvisor(SessionService sessionService) {
			return SessionMemoryAdvisor.builder(sessionService).build();
		}

	}

}
