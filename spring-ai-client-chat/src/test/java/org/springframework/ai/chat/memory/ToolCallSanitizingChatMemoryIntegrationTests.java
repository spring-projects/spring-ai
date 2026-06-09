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

package org.springframework.ai.chat.memory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.annotation.Tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Integration tests for the
 * {@code stream() + ToolCallingAdvisor.streamToolCallResponses(true)
 * + MessageChatMemoryAdvisor} scenario from
 * <a href= "https://github.com/spring-projects/spring-ai/issues/6340">spring-ai#6340</a>.
 * These tests use a scripted {@link ChatModel} (no OpenAI / DeepSeek API key required)
 * that emits a deterministic two-round flux, the same shape that produces the 400 in the
 * issue. The fix under test is the {@link ToolCallSanitizingChatMemory} decorator that
 * strips the tool round-trip from persisted memory.
 *
 * @author redinside-dev
 */
class ToolCallSanitizingChatMemoryIntegrationTests {

	/**
	 * Acceptance test #1 from the spec — no-network repro of the bug. With the
	 * {@link ToolCallSanitizingChatMemory} decorator wrapping
	 * {@link MessageWindowChatMemory}, the persisted memory after a tool-calling turn
	 * must contain a clean transcript — no orphan {@code tool_calls} (which would trigger
	 * the 400 on the next turn replay against an OpenAI-compatible backend).
	 * <p>
	 * Without the sanitizer, the outer {@code ChatClientMessageAggregator} would fold the
	 * streaming tool-call chunks and the recursive text chunks into a single
	 * {@code AssistantMessage(text+toolCalls)} and persist it as-is. With the sanitizer,
	 * the persisted assistant message carries text only, no {@code
	 * toolCalls}, and never a {@code ToolResponseMessage} (which would have required the
	 * same backend pairing that just got sanitized away).
	 */
	@Test
	void streamingToolCallMemoryPersistsSanitizedTranscript() {
		ChatMemory memory = new ToolCallSanitizingChatMemory(
				MessageWindowChatMemory.builder().chatMemoryRepository(new InMemoryChatMemoryRepository()).build());

		ChatClient client = ChatClient.builder(new FakeScriptedChatModel())
			.defaultOptions((ChatOptions.Builder) ToolCallingChatOptions.builder())
			.defaultAdvisors(ToolCallingAdvisor.builder().streamToolCallResponses(true).build(),
					MessageChatMemoryAdvisor.builder(memory).build())
			.defaultTools(new Clock())
			.build();

		List<ChatResponse> chunks = client.prompt()
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "c1"))
			.user("What time is it?")
			.stream()
			.chatResponse()
			.collectList()
			.block();

		assertThat(chunks).isNotNull().isNotEmpty();

		// Read the persisted memory: the assistant message in there must NOT carry
		// tool_calls (they were sanitized away) and there must be NO ToolResponseMessage
		// (the tool round-trip was stripped, which is the entire point of #6340 Shape B).
		List<Message> persisted = memory.get("c1");
		assertThat(persisted).extracting(Message::getMessageType).doesNotContain(MessageType.TOOL);

		AssistantMessage assistant = (AssistantMessage) persisted.stream()
			.filter(m -> m instanceof AssistantMessage)
			.reduce((a, b) -> b) // last assistant message
			.orElseThrow(() -> new AssertionError("expected at least one AssistantMessage in memory"));
		assertThat(assistant.hasToolCalls()).as("persisted assistant message must not carry tool_calls (issue #6340)")
			.isFalse();
	}

	/**
	 * Acceptance test #1 (second part) — follow-up turn on the same conversation id does
	 * not raise. Before the fix, the persisted transcript carried an
	 * {@code AssistantMessage} with orphan {@code tool_calls}, and a follow-up turn
	 * against an OpenAI-compatible backend would be rejected with HTTP 400. With the
	 * sanitizer in place, no orphan tool_calls reach the wire and the next turn goes
	 * through cleanly.
	 */
	@Test
	void followUpTurnAfterStreamingToolCallDoesNotThrow() {
		ChatMemory memory = new ToolCallSanitizingChatMemory(
				MessageWindowChatMemory.builder().chatMemoryRepository(new InMemoryChatMemoryRepository()).build());

		ChatClient client = ChatClient.builder(new FakeScriptedChatModel())
			.defaultOptions((ChatOptions.Builder) ToolCallingChatOptions.builder())
			.defaultAdvisors(ToolCallingAdvisor.builder().streamToolCallResponses(true).build(),
					MessageChatMemoryAdvisor.builder(memory).build())
			.defaultTools(new Clock())
			.build();

		client.prompt()
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "c1"))
			.user("What time is it?")
			.stream()
			.chatResponse()
			.blockLast();

		assertThatNoException().isThrownBy(() -> client.prompt()
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "c1"))
			.user("Thanks")
			.stream()
			.chatResponse()
			.blockLast());
	}

	/**
	 * Acceptance test #2 — multi-round replay. After a streaming tool-calling turn, the
	 * persisted memory snapshot — what would be fed to the next
	 * {@code ChatModel.stream(...)} request on a follow-up turn — contains no orphan
	 * {@code tool_calls} and no {@code ToolResponseMessage}. I.e. it is replay-clean and
	 * would not be rejected by an OpenAI-compatible backend. The follow-up turn is
	 * replayed through a recording {@link ChatModel} wrapper that captures the actual
	 * wire prompt and lets us assert on the message list sent to the model.
	 */
	@Test
	void multiRoundReplayMemorySnapshotIsClean() {
		RecordingChatModel model = new RecordingChatModel();

		ChatMemory memory = new ToolCallSanitizingChatMemory(
				MessageWindowChatMemory.builder().chatMemoryRepository(new InMemoryChatMemoryRepository()).build());

		ChatClient client = ChatClient.builder(model)
			.defaultOptions((ChatOptions.Builder) ToolCallingChatOptions.builder())
			.defaultAdvisors(ToolCallingAdvisor.builder().streamToolCallResponses(true).build(),
					MessageChatMemoryAdvisor.builder(memory).build())
			.defaultTools(new Clock())
			.build();

		// Round 1: the tool-calling turn that triggers the bug.
		client.prompt()
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "c1"))
			.user("What time is it?")
			.stream()
			.chatResponse()
			.blockLast();

		// Sanity: the sanitizer should have already produced a clean memory snapshot
		// after round 1. The follow-up "Thanks" turn replays this snapshot.
		List<Message> persisted = memory.get("c1");
		assertThat(persisted).extracting(Message::getMessageType).doesNotContain(MessageType.TOOL);
		assertThat(persisted.stream()
			.filter(m -> m instanceof AssistantMessage)
			.map(m -> ((AssistantMessage) m).hasToolCalls())
			.filter(Boolean::booleanValue)
			.findAny()).as("no persisted AssistantMessage may carry tool_calls (issue #6340)").isEmpty();

		// Round 2: a follow-up turn. The recorded last call is the one that would be
		// sent to the backend.
		client.prompt()
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "c1"))
			.user("Thanks")
			.stream()
			.chatResponse()
			.blockLast();

		List<Prompt> recordedPrompts = model.recorded();
		assertThat(recordedPrompts).isNotEmpty();
		Prompt followUpPrompt = recordedPrompts.get(recordedPrompts.size() - 1);

		List<Message> instructions = new ArrayList<>(followUpPrompt.getInstructions());

		// Critical assertion for #6340: the follow-up prompt's instructions (the
		// replayed memory + the new user turn) must not contain any
		// ToolResponseMessage, and the last assistant message must not carry
		// tool_calls.
		assertThat(instructions).extracting(Message::getMessageType).doesNotContain(MessageType.TOOL);

		Message lastAssistant = instructions.stream()
			.filter(m -> m instanceof AssistantMessage)
			.reduce((a, b) -> b)
			.orElseThrow(() -> new AssertionError("expected at least one AssistantMessage in replay prompt"));
		assertThat(((AssistantMessage) lastAssistant).hasToolCalls())
			.as("replayed assistant message must not carry tool_calls (issue #6340)")
			.isFalse();
	}

	/**
	 * A trivial one-liner {@code @Tool} that returns the current time, matching the
	 * issue's repro tool.
	 */
	static final class Clock {

		@Tool(name = "getDateTime", description = "ISO-8601 local date-time")
		String getDateTime() {
			return LocalDateTime.now().toString();
		}

	}

	/**
	 * A no-network {@link ChatModel} that returns a scripted two-round response. The
	 * script is two calls deep: the first emits a single {@code AssistantMessage} that
	 * has both text and a {@code getDateTime} tool call (this is the round that
	 * {@code ToolCallingAdvisor} will execute, then recurse), and the second (recursive)
	 * emits a plain text reply.
	 * <p>
	 * This mimics the wire shape of the bug report: a streaming round 1 produces chunks
	 * that the outer {@code ChatClientMessageAggregator} folds into a single
	 * {@code AssistantMessage(text+toolCalls)}, then the recursive round 2 appends to the
	 * same fold — which is exactly the malformed transcript that the un-sanitized
	 * {@code MessageChatMemoryAdvisor} would persist.
	 */
	static final class FakeScriptedChatModel implements ChatModel {

		private final AtomicInteger callCount = new AtomicInteger();

		@Override
		public ChatResponse call(Prompt prompt) {
			int n = this.callCount.incrementAndGet();
			return new ChatResponse(List.of(new Generation(scripted(n))));
		}

		@Override
		public Flux<ChatResponse> stream(Prompt prompt) {
			int n = this.callCount.incrementAndGet();
			AssistantMessage message = scripted(n);
			return Flux.just(new ChatResponse(List.of(new Generation(message))));
		}

		@Override
		public ChatOptions getOptions() {
			return ToolCallingChatOptions.builder().build();
		}

		private static AssistantMessage scripted(int n) {
			if (n == 1) {
				AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("call-date-1", "function",
						"getDateTime", "{}");
				// Single AssistantMessage that mirrors what MessageAggregator produces
				// in the multi-round stream case: it carries both text and a tool call.
				// This is the malformed transcript shape that, without sanitization,
				// causes a 400 on the next turn replay.
				return AssistantMessage.builder()
					.content("It's 10:34 AM on June 9, 2026.")
					.toolCalls(List.of(toolCall))
					.build();
			}
			return new AssistantMessage("It's 10:34 AM on June 9, 2026.");
		}

	}

	/**
	 * ChatModel wrapper that records every prompt it receives and replies with a scripted
	 * two-round response (same as {@link FakeScriptedChatModel} but with recording).
	 */
	static final class RecordingChatModel implements ChatModel {

		private final List<Prompt> prompts = new ArrayList<>();

		private final AtomicInteger callCount = new AtomicInteger();

		List<Prompt> recorded() {
			return this.prompts;
		}

		@Override
		public ChatResponse call(Prompt prompt) {
			this.prompts.add(prompt);
			int n = this.callCount.incrementAndGet();
			return new ChatResponse(List.of(new Generation(scripted(n))));
		}

		@Override
		public Flux<ChatResponse> stream(Prompt prompt) {
			this.prompts.add(prompt);
			int n = this.callCount.incrementAndGet();
			AssistantMessage message = scripted(n);
			return Flux.just(new ChatResponse(List.of(new Generation(message))));
		}

		@Override
		public ChatOptions getOptions() {
			return ToolCallingChatOptions.builder().build();
		}

		private static AssistantMessage scripted(int n) {
			if (n == 1) {
				AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("call-date-1", "function",
						"getDateTime", "{}");
				return AssistantMessage.builder()
					.content("It's 10:34 AM on June 9, 2026.")
					.toolCalls(List.of(toolCall))
					.build();
			}
			return new AssistantMessage("It's 10:34 AM on June 9, 2026.");
		}

	}

}
