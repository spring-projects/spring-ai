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

package org.springframework.ai.chat.client.advisor;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
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
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MessageChatMemoryAdvisor}.
 *
 * @author Mark Pollack
 * @author Thomas Vitale
 * @author Jewoo Shin
 */
public class MessageChatMemoryAdvisorTests {

	// -------------------------------------------------------------------------
	// Builder validation
	// -------------------------------------------------------------------------

	@Test
	void whenChatMemoryIsNullThenThrow() {
		assertThatThrownBy(() -> MessageChatMemoryAdvisor.builder(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("chatMemory cannot be null");
	}

	@Test
	void whenSchedulerIsNullThenThrow() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder().build();
		assertThatThrownBy(() -> MessageChatMemoryAdvisor.builder(chatMemory).scheduler(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("scheduler cannot be null");
	}

	@Test
	void whenBuilderWithDefaultsThenSuccess() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();
		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
		assertThat(advisor.getOrder()).isEqualTo(Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER);
	}

	@Test
	void whenCustomOrderIsSetThenGetOrderReturnsIt() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder().build();
		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory)
			.order(42)
			.scheduler(Schedulers.immediate())
			.build();
		assertThat(advisor.getOrder()).isEqualTo(42);
	}

	// -------------------------------------------------------------------------
	// Conversation ID resolution from request context
	// -------------------------------------------------------------------------

	@Test
	void whenConversationIdAbsentFromContextThenThrow() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder().build();
		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory).build();

		assertThatThrownBy(() -> advisor.getConversationId(Map.of())).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null");
	}

	@Test
	void whenConversationIdPresentInContextThenReturn() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder().build();
		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory).build();

		String result = advisor.getConversationId(Map.of(ChatMemory.CONVERSATION_ID, "session-42"));

		assertThat(result).isEqualTo("session-42");
	}

	// -------------------------------------------------------------------------
	// before() behavior
	// -------------------------------------------------------------------------

	@Test
	void whenBeforeWithUserMessageThenStoreInMemory() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();
		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
		Prompt prompt = Prompt.builder().messages(new UserMessage("Hello")).build();
		ChatClientRequest request = ChatClientRequest.builder()
			.prompt(prompt)
			.context(ChatMemory.CONVERSATION_ID, "test-conversation")
			.build();
		AdvisorChain chain = mock(AdvisorChain.class);

		advisor.before(request, chain);

		List<Message> messages = chatMemory.get("test-conversation");
		assertThat(messages).hasSize(1);
		assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
		assertThat(messages.get(0).getText()).isEqualTo("Hello");
	}

	@Test
	void whenBeforeWithToolResponseMessageThenStoreInMemory() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();
		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
		ToolResponseMessage toolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("weatherTool", "getWeather", "Sunny, 72°F")))
			.build();
		Prompt prompt = Prompt.builder()
			.messages(new UserMessage("What's the weather?"), new AssistantMessage("Let me check..."), toolResponse)
			.build();
		ChatClientRequest request = ChatClientRequest.builder()
			.prompt(prompt)
			.context(ChatMemory.CONVERSATION_ID, "test-conversation")
			.build();
		AdvisorChain chain = mock(AdvisorChain.class);

		advisor.before(request, chain);

		List<Message> messages = chatMemory.get("test-conversation");
		assertThat(messages).hasSize(1);
		assertThat(messages.get(0)).isInstanceOf(ToolResponseMessage.class);
	}

	@Test
	void whenBeforeWithMemoryAlreadyInPromptThenDoesNotDuplicateMemory() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();
		UserMessage userMessage = new UserMessage("When can I pick up dog 45?");
		AssistantMessage assistantMessage = AssistantMessage.builder()
			.content("")
			.toolCalls(
					List.of(new AssistantMessage.ToolCall("call-45", "function", "schedulePickup", "{\"dogId\":45}")))
			.build();
		chatMemory.add("test-conversation", List.of(userMessage, assistantMessage));
		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
		ToolResponseMessage toolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("call-45", "schedulePickup",
					"Pickup scheduled for Tuesday morning")))
			.build();
		Prompt prompt = Prompt.builder().messages(userMessage, assistantMessage, toolResponse).build();
		ChatClientRequest request = ChatClientRequest.builder()
			.prompt(prompt)
			.context(ChatMemory.CONVERSATION_ID, "test-conversation")
			.build();
		AdvisorChain chain = mock(AdvisorChain.class);

		ChatClientRequest processedRequest = advisor.before(request, chain);

		assertThat(processedRequest.prompt().getInstructions()).containsExactly(userMessage, assistantMessage,
				toolResponse);
		assertThat(chatMemory.get("test-conversation")).containsExactly(userMessage, assistantMessage, toolResponse);
	}

	@Test
	void whenBeforeWithWindowedMemoryAlreadyInPromptThenDoesNotDuplicateMemory() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.maxMessages(3)
			.build();
		UserMessage userMessage = new UserMessage("When can I pick up dog 45?");
		AssistantMessage firstAssistantMessage = AssistantMessage.builder()
			.content("")
			.toolCalls(
					List.of(new AssistantMessage.ToolCall("call-45", "function", "schedulePickup", "{\"dogId\":45}")))
			.build();
		ToolResponseMessage firstToolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("call-45", "schedulePickup",
					"Pickup scheduled for Tuesday morning")))
			.build();
		AssistantMessage secondAssistantMessage = AssistantMessage.builder()
			.content("")
			.toolCalls(List.of(new AssistantMessage.ToolCall("call-46", "function", "confirmPickup",
					"{\"dogId\":45,\"location\":\"Lisbon\"}")))
			.build();
		chatMemory.add("test-conversation",
				List.of(userMessage, firstAssistantMessage, firstToolResponse, secondAssistantMessage));
		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
		ToolResponseMessage secondToolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("call-46", "confirmPickup",
					"Pickup confirmed for the Lisbon location")))
			.build();
		Prompt prompt = Prompt.builder()
			.messages(userMessage, firstAssistantMessage, firstToolResponse, secondAssistantMessage, secondToolResponse)
			.build();
		ChatClientRequest request = ChatClientRequest.builder()
			.prompt(prompt)
			.context(ChatMemory.CONVERSATION_ID, "test-conversation")
			.build();
		AdvisorChain chain = mock(AdvisorChain.class);

		ChatClientRequest processedRequest = advisor.before(request, chain);

		assertThat(processedRequest.prompt().getInstructions()).containsExactly(userMessage, firstAssistantMessage,
				firstToolResponse, secondAssistantMessage, secondToolResponse);
		// Turn-boundary snapping: the initial add of [U, A, TR, A] with maxMessages=3
		// places the raw cut at position 1 (firstAssistantMessage). Snapping advances
		// through A, TR, A without finding a USER boundary, so all four messages are
		// evicted.
		assertThat(chatMemory.get("test-conversation")).containsExactly(secondToolResponse);
	}

	@Test
	void whenBeforeMovesSystemMessageToFirstPosition() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();
		chatMemory.add("test-conversation",
				List.of(new UserMessage("Previous question"), new AssistantMessage("Previous answer")));
		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
		Prompt prompt = Prompt.builder()
			.messages(new UserMessage("Hello"), new SystemMessage("You are a helpful assistant"))
			.build();
		ChatClientRequest request = ChatClientRequest.builder()
			.prompt(prompt)
			.context(ChatMemory.CONVERSATION_ID, "test-conversation")
			.build();
		AdvisorChain chain = mock(AdvisorChain.class);

		ChatClientRequest processedRequest = advisor.before(request, chain);

		List<Message> processedMessages = processedRequest.prompt().getInstructions();
		assertThat(processedMessages.get(0)).isInstanceOf(SystemMessage.class);
		assertThat(processedMessages.get(0).getText()).isEqualTo("You are a helpful assistant");
	}

	@Test
	void whenBeforeSystemMessageAlreadyFirstThenKeepOrder() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();
		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
		Prompt prompt = Prompt.builder()
			.messages(new SystemMessage("You are a helpful assistant"), new UserMessage("Hello"))
			.build();
		ChatClientRequest request = ChatClientRequest.builder()
			.prompt(prompt)
			.context(ChatMemory.CONVERSATION_ID, "test-conversation")
			.build();
		AdvisorChain chain = mock(AdvisorChain.class);

		ChatClientRequest processedRequest = advisor.before(request, chain);

		List<Message> processedMessages = processedRequest.prompt().getInstructions();
		assertThat(processedMessages.get(0)).isInstanceOf(SystemMessage.class);
		assertThat(processedMessages.get(0).getText()).isEqualTo("You are a helpful assistant");
		assertThat(processedMessages.get(1)).isInstanceOf(UserMessage.class);
	}

	// -------------------------------------------------------------------------
	// after(): tool-call round-trips must not leak into cross-turn memory (gh-6340)
	// -------------------------------------------------------------------------

	@Test
	void whenAfterAssistantMessageCarriesTextAndToolCallsThenToolCallsStrippedTextKept() {
		// Reproduces gh-6340: stream() + ToolCallingAdvisor.streamToolCallResponses(true)
		// makes the MessageAggregator fold the tool-call round and the recursive text
		// round into a single AssistantMessage(text + tool_calls). Persisting that orphan
		// makes the next turn fail with HTTP 400 on OpenAI-compatible backends.
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();
		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
		AssistantMessage merged = AssistantMessage.builder()
			.content("It's 10:34 AM.")
			.toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "getDateTime", "{}")))
			.build();
		ChatClientResponse response = ChatClientResponse.builder()
			.chatResponse(new ChatResponse(List.of(new Generation(merged))))
			.context(ChatMemory.CONVERSATION_ID, "test-conversation")
			.build();

		advisor.after(response, mock(AdvisorChain.class));

		List<Message> messages = chatMemory.get("test-conversation");
		assertThat(messages).hasSize(1);
		assertThat(messages.get(0)).isInstanceOf(AssistantMessage.class);
		AssistantMessage persisted = (AssistantMessage) messages.get(0);
		assertThat(persisted.getText()).isEqualTo("It's 10:34 AM.");
		assertThat(persisted.hasToolCalls()).isFalse();
	}

	@Test
	void whenAfterStripsToolCallsThenMetadataAndMediaArePreserved() {
		// gh-6340: stripping the orphan tool_calls must not drop the rest of the
		// message. The metadata keys attached by the MessageAggregator (thoughts,
		// outputWithoutThoughts) and any media must survive; otherwise replayed
		// history would silently lose reasoning or attachments.
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();
		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
		Media media = Media.builder()
			.mimeType(MimeTypeUtils.IMAGE_PNG)
			.data(URI.create("https://example.com/clock.png"))
			.build();
		AssistantMessage merged = AssistantMessage.builder()
			.content("It's 10:34 AM.")
			.properties(Map.of("thoughts", "Let me check the clock.", "outputWithoutThoughts", "It's 10:34 AM."))
			.media(List.of(media))
			.toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "getDateTime", "{}")))
			.build();
		ChatClientResponse response = ChatClientResponse.builder()
			.chatResponse(new ChatResponse(List.of(new Generation(merged))))
			.context(ChatMemory.CONVERSATION_ID, "test-conversation")
			.build();

		advisor.after(response, mock(AdvisorChain.class));

		List<Message> messages = chatMemory.get("test-conversation");
		assertThat(messages).hasSize(1);
		AssistantMessage persisted = (AssistantMessage) messages.get(0);
		assertThat(persisted.hasToolCalls()).isFalse();
		assertThat(persisted.getText()).isEqualTo("It's 10:34 AM.");
		assertThat(persisted.getMetadata()).containsEntry("thoughts", "Let me check the clock.")
			.containsEntry("outputWithoutThoughts", "It's 10:34 AM.");
		assertThat(persisted.getMedia()).containsExactly(media);
	}

	@Test
	void whenAfterPureToolCallFrameHasNoTextThenDroppedEvenWithMetadataAndMedia() {
		// gh-6340: a tool-call frame with no text is an intra-turn artifact and is
		// dropped even when it still carries metadata or media, because that transient
		// payload is not cross-turn history (durable output lives on the text round).
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();
		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
		Media media = Media.builder()
			.mimeType(MimeTypeUtils.IMAGE_PNG)
			.data(URI.create("https://example.com/clock.png"))
			.build();
		AssistantMessage pureToolCall = AssistantMessage.builder()
			.content("")
			.properties(Map.of("thoughts", "Let me check the clock."))
			.media(List.of(media))
			.toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "getDateTime", "{}")))
			.build();
		ChatClientResponse response = ChatClientResponse.builder()
			.chatResponse(new ChatResponse(List.of(new Generation(pureToolCall))))
			.context(ChatMemory.CONVERSATION_ID, "test-conversation")
			.build();

		advisor.after(response, mock(AdvisorChain.class));

		assertThat(chatMemory.get("test-conversation")).isEmpty();
	}

	@Test
	void whenAfterAssistantMessageHasNoToolCallsThenStoredUnchanged() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();
		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
		AssistantMessage plain = new AssistantMessage("It's 10:34 AM.");
		ChatClientResponse response = ChatClientResponse.builder()
			.chatResponse(new ChatResponse(List.of(new Generation(plain))))
			.context(ChatMemory.CONVERSATION_ID, "test-conversation")
			.build();

		advisor.after(response, mock(AdvisorChain.class));

		List<Message> messages = chatMemory.get("test-conversation");
		assertThat(messages).hasSize(1);
		assertThat(messages.get(0)).isInstanceOf(AssistantMessage.class);
		AssistantMessage persisted = (AssistantMessage) messages.get(0);
		assertThat(persisted.getText()).isEqualTo("It's 10:34 AM.");
		assertThat(persisted.hasToolCalls()).isFalse();
	}

	@Test
	void whenAdviseStreamFoldsToolCallRoundThenPersistedMemoryIsReplayClean() {
		// End-to-end reproduction of gh-6340 through the real streaming path
		// (before -> nextStream -> ChatClientMessageAggregator -> after). The downstream
		// stream is the two-round shape produced with
		// ToolCallingAdvisor.streamToolCallResponses(true): a tool-call round
		// (text + tool_calls) followed by the recursive post-tool text round. The
		// aggregator folds them into a single AssistantMessage(text + tool_calls); the
		// advisor must persist it without the orphan tool_calls so the next turn's replay
		// does not trip an HTTP 400 on OpenAI-compatible backends.
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();
		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory)
			.scheduler(Schedulers.immediate())
			.build();
		Prompt prompt = Prompt.builder().messages(new UserMessage("What time is it?")).build();
		ChatClientRequest request = ChatClientRequest.builder()
			.prompt(prompt)
			.context(ChatMemory.CONVERSATION_ID, "test-conversation")
			.build();

		AssistantMessage toolCallRound = AssistantMessage.builder()
			.content("Let me check. ")
			.toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "getDateTime", "{}")))
			.build();
		AssistantMessage textRound = new AssistantMessage("It's 10:34 AM.");
		StreamAdvisorChain chain = mock(StreamAdvisorChain.class);
		when(chain.nextStream(any())).thenReturn(Flux.just(
				ChatClientResponse.builder()
					.chatResponse(new ChatResponse(List.of(new Generation(toolCallRound))))
					.context(ChatMemory.CONVERSATION_ID, "test-conversation")
					.build(),
				ChatClientResponse.builder()
					.chatResponse(new ChatResponse(List.of(new Generation(textRound))))
					.context(ChatMemory.CONVERSATION_ID, "test-conversation")
					.build()));

		advisor.adviseStream(request, chain).blockLast();

		List<Message> persisted = chatMemory.get("test-conversation");
		assertThat(persisted).extracting(Message::getMessageType)
			.containsExactly(MessageType.USER, MessageType.ASSISTANT);
		AssistantMessage assistant = (AssistantMessage) persisted.get(1);
		assertThat(assistant.hasToolCalls()).isFalse();
		assertThat(assistant.getText()).isEqualTo("Let me check. It's 10:34 AM.");
	}

}
