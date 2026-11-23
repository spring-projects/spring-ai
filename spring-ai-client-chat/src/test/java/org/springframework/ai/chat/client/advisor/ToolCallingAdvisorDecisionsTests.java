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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.part.OpaquePayload;
import org.springframework.ai.chat.messages.part.StreamingParts;
import org.springframework.ai.chat.messages.part.ToolCallPart;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallConfirmationPolicy;
import org.springframework.ai.model.tool.ToolCallDecision;
import org.springframework.ai.model.tool.ToolCallDecisions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A {@link ToolCallingAdvisor} loop pausing on tool calls that need a person's
 * confirmation, and the application resuming the conversation after running them with
 * {@link ToolCallDecisions}, through a {@link ChatClient} with a scripted model.
 *
 * @author Christian Tzolov
 */
@SuppressWarnings("removal")
class ToolCallingAdvisorDecisionsTests {

	private final ScriptedModel model = new ScriptedModel();

	private final List<String> ran = new ArrayList<>();

	private final ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();

	@Test
	void callThatNeedsConfirmationPausesTheLoop() {
		this.model.thenCall(call("c1", "book_flight"));

		ChatResponse reply = client().prompt().user("Book VX205").call().chatResponse();

		assertThat(ToolCallDecisions.awaitingDecision(reply)).containsExactly("c1");
		assertThat(reply.getResult().getOutput().getToolCalls()).extracting(AssistantMessage.ToolCall::id)
			.containsExactly("c1");
		assertThat(this.ran).isEmpty();
		assertThat(this.model.prompts).hasSize(1);
	}

	@Test
	void callsThatNeedNoConfirmationRunWithoutPausing() {
		this.model.thenCall(call("c1", "weather")).thenAnswer("Sunny.");

		ChatResponse reply = client().prompt().user("Weather?").call().chatResponse();

		assertThat(ToolCallDecisions.isAwaitingDecision(reply)).isFalse();
		assertThat(reply.getResult().getOutput().getText()).isEqualTo("Sunny.");
		assertThat(this.ran).containsExactly("weather");
	}

	@Test
	void policyOnTheManagerNamesTheToolsToConfirm() {
		this.model.thenCall(call("c1", "weather"));
		ChatClient client = client(ToolCallingAdvisor.builder()
			.toolCallingManager(ToolCallingManager.builder()
				.toolCallConfirmationPolicy(ToolCallConfirmationPolicy.forTools("weather"))
				.build())
			.build());

		ChatResponse reply = client.prompt().user("Weather?").call().chatResponse();

		assertThat(ToolCallDecisions.awaitingDecision(reply)).containsExactly("c1");
		assertThat(this.ran).isEmpty();
	}

	@Test
	void toolAnnotatedToNeedConfirmationPausesTheLoop() {
		this.model.thenCall(call("c1", "cancelBooking"));
		ChatClient client = ChatClient.builder(this.model)
			.defaultAdvisors(ToolCallingAdvisor.builder().build())
			.defaultTools(new BookingTools())
			.build();

		ChatResponse reply = client.prompt().user("Cancel VX205").call().chatResponse();

		assertThat(ToolCallDecisions.awaitingDecision(reply)).containsExactly("c1");
	}

	@Test
	void streamedReplyPausesOnLastChunkWithTheToolCalls() {
		this.model.thenCall(call("c1", "book_flight"));
		List<ChatClientResponse> seenAfter = new ArrayList<>();
		BaseAdvisor outsideTheLoop = new BaseAdvisor() {

			@Override
			public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
				return request;
			}

			@Override
			public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
				seenAfter.add(response);
				return response;
			}

			@Override
			public String getName() {
				return "outside the loop";
			}

			@Override
			public int getOrder() {
				return ToolCallingAdvisor.DEFAULT_ORDER - 100;
			}

		};
		ChatClient client = ChatClient.builder(this.model)
			.defaultAdvisors(outsideTheLoop, ToolCallingAdvisor.builder().build())
			.defaultTools(tools().toArray())
			.build();

		List<ChatResponse> chunks = client.prompt().user("Book VX205").stream().chatResponse().collectList().block();

		ChatResponse last = chunks.get(chunks.size() - 1);
		assertThat(ToolCallDecisions.awaitingDecision(last)).containsExactly("c1");
		assertThat(last.getResult().getOutput().getToolCalls()).hasSize(1);
		// The chunk keeps the reply's finish reason, so the advisors outside the loop see
		// the end of the turn
		assertThat(last.getResult().getMetadata().getFinishReason()).isEqualTo("tool_calls");
		assertThat(seenAfter).hasSize(1);
		assertThat(this.ran).isEmpty();
	}

	@Test
	void streamedPauseKeepsTheToolCallPayloads() {
		// The last chunk carries the manager's tool call parts with their payloads, and
		// the message metadata: Gemini keeps its thought signatures in both. The model
		// streams an indexed part, as streaming models do, so the aggregation keeps it.
		OpaquePayload signature = new OpaquePayload("google", "thought_signature", "c2lnbmF0dXJl");
		ToolCallPart toolCall = new ToolCallPart(new AssistantMessage.ToolCall("c1", "function", "book_flight", "{}"),
				signature, Map.of());
		AssistantMessage paused = AssistantMessage.builder()
			.parts(List.of(StreamingParts.complete(toolCall, 0)))
			.properties(Map.of("thoughtSignatures", List.of("c2lnbmF0dXJl")))
			.build();
		this.model.thenCall(paused);

		List<ChatResponse> chunks = client().prompt().user("Book VX205").stream().chatResponse().collectList().block();

		ChatResponse last = chunks.get(chunks.size() - 1);
		assertThat(ToolCallDecisions.awaitingDecision(last)).containsExactly("c1");
		AssistantMessage output = last.getResult().getOutput();
		assertThat(output.getParts()).singleElement()
			.isInstanceOfSatisfying(ToolCallPart.class, part -> assertThat(part.payload()).isEqualTo(signature));
		assertThat(output.getMetadata()).containsKey("thoughtSignatures");
	}

	@Test
	void replyWithoutToolCallIdsPausesWithPositionKeys() {
		// Some providers give tool calls no id; the call is named by its position, and
		// the
		// paused reply is the model's, untouched
		AssistantMessage paused = call(null, "book_flight");
		this.model.thenCall(paused).thenAnswer("Booked.");
		ChatClient client = client();
		UserMessage question = new UserMessage("Book VX205");

		ChatResponse reply = client.prompt().messages(question).call().chatResponse();
		assertThat(ToolCallDecisions.awaitingDecision(reply)).containsExactly("#0");
		assertThat(reply.getResult().getOutput()).isSameAs(paused);

		ToolExecutionResult result = this.toolCallingManager.executeToolCalls(prompt(question), reply,
				ToolCallDecisions.of(ToolCallDecision.allow("#0")));
		String answer = client.prompt().messages(result.conversationHistory()).call().content();

		assertThat(answer).isEqualTo("Booked.");
		assertThat(this.ran).containsExactly("book_flight");
	}

	@Test
	void applicationResumesWithTheToolResults() {
		// Pause, run the allowed call through the manager, send the conversation on
		AssistantMessage paused = call("c1", "book_flight");
		this.model.thenCall(paused).thenAnswer("Booked.");
		ChatClient client = client();
		UserMessage question = new UserMessage("Book VX205");
		ChatResponse reply = client.prompt().messages(question).call().chatResponse();
		assertThat(ToolCallDecisions.awaitingDecision(reply)).containsExactly("c1");

		ToolExecutionResult result = this.toolCallingManager.executeToolCalls(prompt(question), reply,
				ToolCallDecisions.of(ToolCallDecision.allow("c1")));
		assertThat(result.returnDirect()).isFalse();
		String answer = client.prompt().messages(result.conversationHistory()).call().content();

		assertThat(answer).isEqualTo("Booked.");
		assertThat(this.ran).containsExactly("book_flight");
		assertThat(describe(this.model.prompts.get(1))).containsExactly("Book VX205", "call:c1",
				"result:c1=\"booked\"");
	}

	@Test
	void deniedCallAnswersTheModelWithItsMessageWithoutRunning() {
		AssistantMessage paused = call("c1", "book_flight");
		this.model.thenAnswer("Understood.");
		UserMessage question = new UserMessage("Book VX205");

		ToolExecutionResult result = this.toolCallingManager.executeToolCalls(prompt(question), reply(paused),
				ToolCallDecisions.of(ToolCallDecision.deny("c1", "Too expensive.")));
		client().prompt().messages(result.conversationHistory()).call().content();

		assertThat(this.ran).isEmpty();
		assertThat(describe(this.model.prompts.get(0))).endsWith("result:c1=Too expensive.");
	}

	@Test
	void streamedResumptionStreamsTheAnswer() {
		AssistantMessage paused = call("c1", "book_flight");
		this.model.thenAnswer("Booked.");
		UserMessage question = new UserMessage("Book VX205");
		ToolExecutionResult result = this.toolCallingManager.executeToolCalls(prompt(question), reply(paused),
				ToolCallDecisions.of(ToolCallDecision.allow("c1")));

		String answer = String.join("",
				client().prompt().messages(result.conversationHistory()).stream().content().collectList().block());

		assertThat(answer).isEqualTo("Booked.");
		assertThat(this.ran).containsExactly("book_flight");
	}

	@Test
	void memoryAdvisorOutsideTheLoopStoresValidConversation() {
		// The pause stores the reply with the tool calls; the resumption's tool results
		// follow it in memory, as the model requires
		AssistantMessage paused = call("c1", "book_flight");
		this.model.thenCall(paused).thenAnswer("Booked.");
		ChatMemory memory = MessageWindowChatMemory.builder().build();
		ChatClient client = ChatClient.builder(this.model)
			.defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build(),
					ToolCallingAdvisor.builder().toolCallingManager(this.toolCallingManager).build())
			.defaultTools(tools().toArray())
			.build();
		UserMessage question = new UserMessage("Book VX205");

		ChatResponse reply = client.prompt()
			.messages(question)
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "conv"))
			.call()
			.chatResponse();
		ToolExecutionResult result = this.toolCallingManager.executeToolCalls(prompt(question), reply,
				ToolCallDecisions.of(ToolCallDecision.allow("c1")));
		// Memory holds the conversation; only the tool results are new
		List<Message> history = result.conversationHistory();
		String answer = client.prompt()
			.messages(history.get(history.size() - 1))
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "conv"))
			.call()
			.content();

		assertThat(answer).isEqualTo("Booked.");
		assertThat(describe(memory.get("conv"))).containsExactly("Book VX205", "call:c1", "result:c1=\"booked\"",
				"Booked.");
		assertThat(describe(this.model.prompts.get(1))).containsExactly("Book VX205", "call:c1",
				"result:c1=\"booked\"");
	}

	@Test
	void streamedPauseWithMemoryOutsideTheLoopStoresValidConversation() {
		// The memory advisor aggregates the stream before it stores the reply: the reply
		// it stores holds the paused tool calls, the resumption's tool results follow
		AssistantMessage paused = call("c1", "book_flight");
		this.model.thenCall(paused).thenAnswer("Booked.");
		ChatMemory memory = MessageWindowChatMemory.builder().build();
		ChatClient client = ChatClient.builder(this.model)
			.defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build(),
					ToolCallingAdvisor.builder().toolCallingManager(this.toolCallingManager).build())
			.defaultTools(tools().toArray())
			.build();
		UserMessage question = new UserMessage("Book VX205");

		List<ChatResponse> chunks = client.prompt()
			.messages(question)
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "conv"))
			.stream()
			.chatResponse()
			.collectList()
			.block();
		ChatResponse last = chunks.get(chunks.size() - 1);
		assertThat(ToolCallDecisions.awaitingDecision(last)).containsExactly("c1");
		assertThat(describe(memory.get("conv"))).containsExactly("Book VX205", "call:c1");

		ToolExecutionResult result = this.toolCallingManager.executeToolCalls(prompt(question), last,
				ToolCallDecisions.of(ToolCallDecision.allow("c1")));
		List<Message> history = result.conversationHistory();
		String answer = String.join("",
				client.prompt()
					.messages(history.get(history.size() - 1))
					.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "conv"))
					.stream()
					.content()
					.collectList()
					.block());

		assertThat(answer).isEqualTo("Booked.");
		assertThat(describe(memory.get("conv"))).containsExactly("Book VX205", "call:c1", "result:c1=\"booked\"",
				"Booked.");
	}

	@Test
	void memoryAdvisorInsideTheLoopStoresValidConversationToo() {
		// Memory inside the loop sees every round: the loop forwards only the newest
		// message and memory supplies the rest, on the resumption as well
		AssistantMessage paused = call("c1", "book_flight");
		this.model.thenCall(paused).thenAnswer("Booked.");
		ChatMemory memory = MessageWindowChatMemory.builder().build();
		ChatClient client = ChatClient.builder(this.model)
			.defaultAdvisors(
					MessageChatMemoryAdvisor.builder(memory).order(ToolCallingAdvisor.DEFAULT_ORDER + 100).build(),
					ToolCallingAdvisor.builder()
						.toolCallingManager(this.toolCallingManager)
						.disableInternalConversationHistory()
						.build())
			.defaultTools(tools().toArray())
			.build();

		ChatResponse reply = client.prompt()
			.user("Book VX205")
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "conv"))
			.call()
			.chatResponse();
		assertThat(ToolCallDecisions.awaitingDecision(reply)).containsExactly("c1");
		// The application holds no conversation, memory does: the prompt carries the
		// tools
		ToolExecutionResult result = this.toolCallingManager.executeToolCalls(prompt(), reply,
				ToolCallDecisions.of(ToolCallDecision.allow("c1")));
		List<Message> history = result.conversationHistory();
		String answer = client.prompt()
			.messages(history.get(history.size() - 1))
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "conv"))
			.call()
			.content();

		assertThat(answer).isEqualTo("Booked.");
		assertThat(describe(memory.get("conv"))).containsExactly("Book VX205", "call:c1", "result:c1=\"booked\"",
				"Booked.");
		assertThat(describe(this.model.prompts.get(1))).containsExactly("Book VX205", "call:c1",
				"result:c1=\"booked\"");
	}

	private ChatClient client() {
		return client(ToolCallingAdvisor.builder().build());
	}

	private ChatClient client(ToolCallingAdvisor toolCalling) {
		return ChatClient.builder(this.model).defaultAdvisors(toolCalling).defaultTools(tools().toArray()).build();
	}

	private List<ToolCallback> tools() {
		return List.of(tool("book_flight", "booked", ToolMetadata.builder().requiresConfirmation(true).build()),
				tool("weather", "sunny", ToolMetadata.builder().build()), tool("ticket", "VX205-14C",
						ToolMetadata.builder().requiresConfirmation(true).returnDirect(true).build()));
	}

	private ToolCallback tool(String name, String result, ToolMetadata metadata) {
		return FunctionToolCallback.builder(name, (Input input) -> {
			this.ran.add(name);
			return result;
		}).description(name).inputType(Input.class).toolMetadata(metadata).build();
	}

	/** The prompt the model answered with the paused reply, carrying the tools. */
	private Prompt prompt(Message... messages) {
		return new Prompt(List.of(messages), ToolCallingChatOptions.builder().toolCallbacks(tools()).build());
	}

	private static AssistantMessage call(String id, String tool) {
		return AssistantMessage.builder()
			.content("")
			.toolCalls(List.of(new AssistantMessage.ToolCall(id, "function", tool, "{}")))
			.build();
	}

	private static ChatResponse reply(AssistantMessage toolCalls) {
		return new ChatResponse(List.of(new Generation(toolCalls)));
	}

	private static List<String> describe(Prompt prompt) {
		return describe(prompt.getInstructions());
	}

	/**
	 * The text, "call:id" for a tool call, "result:id=data" per tool result; blank system
	 * messages left out.
	 */
	private static List<String> describe(List<Message> messages) {
		List<String> described = new ArrayList<>();
		for (Message message : messages) {
			if (message instanceof SystemMessage && message.getText().isBlank()) {
				continue;
			}
			if (message instanceof AssistantMessage assistant && assistant.hasToolCalls()) {
				described.add("call:" + assistant.getToolCalls().get(0).id());
			}
			else if (message instanceof ToolResponseMessage responses) {
				responses.getResponses().forEach(r -> described.add("result:" + r.id() + "=" + r.responseData()));
			}
			else {
				described.add(message.getText());
			}
		}
		return described;
	}

	record Input() {
	}

	static class BookingTools {

		@Tool(description = "Cancel a booking", requiresConfirmation = true)
		String cancelBooking(String bookingId) {
			return "cancelled";
		}

	}

	/** Answers each prompt with the next scripted reply, and keeps the prompts it got. */
	static class ScriptedModel implements ChatModel {

		private final Deque<Function<Prompt, ChatResponse>> replies = new ArrayDeque<>();

		final List<Prompt> prompts = new ArrayList<>();

		ScriptedModel thenCall(AssistantMessage toolCalls) {
			ChatGenerationMetadata metadata = ChatGenerationMetadata.builder().finishReason("tool_calls").build();
			this.replies.add(prompt -> new ChatResponse(List.of(new Generation(toolCalls, metadata))));
			return this;
		}

		ScriptedModel thenAnswer(String text) {
			this.replies.add(prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
			return this;
		}

		@Override
		public synchronized ChatResponse call(Prompt prompt) {
			this.prompts.add(prompt);
			Function<Prompt, ChatResponse> reply = this.replies.poll();
			if (reply == null) {
				throw new IllegalStateException("No scripted reply left for prompt " + this.prompts.size());
			}
			return reply.apply(prompt);
		}

		@Override
		public Flux<ChatResponse> stream(Prompt prompt) {
			return Flux.defer(() -> Flux.just(call(prompt)));
		}

		@Override
		public ChatOptions getDefaultOptions() {
			return ToolCallingChatOptions.builder().build();
		}

		@Override
		public ChatOptions getOptions() {
			return ToolCallingChatOptions.builder().build();
		}

	}

}
