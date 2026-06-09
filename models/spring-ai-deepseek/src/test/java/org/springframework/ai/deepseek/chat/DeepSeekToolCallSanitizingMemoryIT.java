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

package org.springframework.ai.deepseek.chat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.ToolCallSanitizingChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.annotation.Tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Integration test reproducing
 * <a href= "https://github.com/spring-projects/spring-ai/issues/6340">spring-ai#6340</a>
 * against the live DeepSeek backend. This is the GA release-blocker test: the issue's
 * exact repro is streamed through a {@link ChatClient} with
 * {@code ToolCallingAdvisor.streamToolCallResponses(true)} +
 * {@code MessageChatMemoryAdvisor} wrapped in {@link ToolCallSanitizingChatMemory}, and
 * we verify that round 1 returns a text reply (via the {@code getDateTime} tool) and
 * round 2 does NOT throw an HTTP 400 from the DeepSeek API.
 * <p>
 * Skipped when {@code DEEPSEEK_API_KEY} is not set in the environment.
 *
 * @author redinside-dev
 */
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
class DeepSeekToolCallSanitizingMemoryIT {

	@Test
	void deepSeekStreamingToolCallMemoryTwoRoundReproIsClean() {
		DeepSeekApi api = DeepSeekApi.builder().apiKey(System.getenv("DEEPSEEK_API_KEY")).build();
		org.springframework.ai.deepseek.DeepSeekChatModel chatModel = org.springframework.ai.deepseek.DeepSeekChatModel
			.builder()
			.deepSeekApi(api)
			.build();
		RecordingChatModel model = new RecordingChatModel(chatModel);

		ChatMemory memory = new ToolCallSanitizingChatMemory(
				MessageWindowChatMemory.builder().chatMemoryRepository(new InMemoryChatMemoryRepository()).build());

		ChatClient client = ChatClient.builder(model)
			.defaultOptions((ChatOptions.Builder) ToolCallingChatOptions.builder())
			.defaultAdvisors(ToolCallingAdvisor.builder().streamToolCallResponses(true).build(),
					MessageChatMemoryAdvisor.builder(memory).build())
			.defaultTools(new Clock())
			.build();

		// Round 1: the tool-calling turn. Must stream a text reply and not throw.
		List<String> round1Texts = client.prompt()
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "c1"))
			.user("What time is it? Answer in one short sentence.")
			.stream()
			.chatResponse()
			.flatMap(r -> Flux.fromIterable(r.getResults()).flatMap(gen -> {
				String text = gen.getOutput().getText();
				return (text == null || text.isBlank()) ? Flux.<String>empty() : Flux.just(text);
			}))
			.collectList()
			.block();
		assertThat(round1Texts).as("round 1 should produce at least one text chunk").isNotEmpty();

		// Persisted memory must NOT contain a ToolResponseMessage (issue #6340) and
		// the assistant message must NOT carry orphan tool_calls. (DeepSeek rejects
		// "assistant message with 'tool_calls' must be followed by tool messages
		// responding to each 'tool_call_id'" with HTTP 400 — see
		// https://github.com/spring-projects/spring-ai/issues/6340.)
		List<Message> persisted = memory.get("c1");
		assertThat(persisted).extracting(Message::getMessageType).doesNotContain(MessageType.TOOL);
		assertThat(persisted.stream()
			.filter(m -> m instanceof AssistantMessage)
			.map(m -> ((AssistantMessage) m).hasToolCalls())
			.filter(Boolean::booleanValue)
			.findAny()).as("no persisted AssistantMessage may carry orphan tool_calls (issue #6340)").isEmpty();

		// Round 2: a follow-up turn. Before the fix this would have raised an HTTP
		// 400 from DeepSeek. With ToolCallSanitizingChatMemory in place the replay
		// is clean.
		assertThatNoException().isThrownBy(() -> client.prompt()
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "c1"))
			.user("Thanks")
			.stream()
			.chatResponse()
			.blockLast());

		// Multi-round replay assertion (test #2 from the spec): the recorded follow-up
		// prompt's instructions must not contain a ToolResponseMessage, and the last
		// assistant message must not carry tool_calls.
		List<Prompt> recordedPrompts = model.recorded();
		assertThat(recordedPrompts).isNotEmpty();
		Prompt followUpPrompt = recordedPrompts.get(recordedPrompts.size() - 1);
		List<Message> instructions = new java.util.ArrayList<>(followUpPrompt.getInstructions());
		assertThat(instructions).extracting(Message::getMessageType).doesNotContain(MessageType.TOOL);
		Message lastAssistant = instructions.stream()
			.filter(m -> m instanceof AssistantMessage)
			.reduce((a, b) -> b)
			.orElseThrow(() -> new AssertionError("expected at least one AssistantMessage in replay prompt"));
		assertThat(((AssistantMessage) lastAssistant).hasToolCalls())
			.as("replayed assistant message must not carry tool_calls (issue #6340)")
			.isFalse();
	}

	static final class Clock {

		@Tool(name = "getDateTime", description = "ISO-8601 local date-time")
		String getDateTime() {
			return LocalDateTime.now().toString();
		}

	}

	/**
	 * Recording chat model wrapper used for the multi-round replay assertion.
	 */
	static final class RecordingChatModel implements org.springframework.ai.chat.model.ChatModel {

		private final java.util.List<Prompt> prompts = new java.util.ArrayList<>();

		private final org.springframework.ai.chat.model.ChatModel delegate;

		RecordingChatModel(org.springframework.ai.chat.model.ChatModel delegate) {
			this.delegate = delegate;
		}

		java.util.List<Prompt> recorded() {
			return this.prompts;
		}

		@Override
		public org.springframework.ai.chat.model.ChatResponse call(Prompt prompt) {
			this.prompts.add(prompt);
			return this.delegate.call(prompt);
		}

		@Override
		public Flux<org.springframework.ai.chat.model.ChatResponse> stream(Prompt prompt) {
			this.prompts.add(prompt);
			return this.delegate.stream(prompt);
		}

		@Override
		public ChatOptions getOptions() {
			return this.delegate.getOptions();
		}

	}

}
