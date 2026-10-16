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

package org.springframework.ai.test.chat.client.advisor;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallDecision;
import org.springframework.ai.model.tool.ToolCallDecisions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for integration tests of tool call confirmation: a tool marked
 * {@code requiresConfirmation} pauses the {@link ToolCallingAdvisor} loop, and the
 * application resumes the conversation after running the calls with
 * {@link ToolCallDecisions}, against a real model.
 * <p>
 * Each test runs a full round trip: the model asks for the gated call, the loop pauses,
 * the manager carries out the decisions, and the model answers with the tool results in a
 * second request. That second request is where a provider rejects a reply that lost
 * provider data on the way, such as a thought signature.
 * <p>
 * Subclasses must implement {@link #getChatModel()} to provide the ChatModel to test.
 *
 * @author Christian Tzolov
 */
public abstract class AbstractToolCallConfirmationIT {

	/** The confirmation code the booking tool returns, to find in the model's answer. */
	protected static final String CONFIRMATION_CODE = "ZX-9431";

	private static final String BOOKING_REQUEST = "Book flight VX205 for me and tell me the confirmation code.";

	private final List<String> bookings = new CopyOnWriteArrayList<>();

	private final ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();

	/**
	 * Returns the ChatModel instance to be used in tests.
	 * @return the ChatModel to test
	 */
	protected abstract ChatModel getChatModel();

	@BeforeEach
	void resetBookings() {
		this.bookings.clear();
	}

	@Test
	void allowedCallRunsAndTheModelAnswersWithItsResult() {
		ChatClient chatClient = ChatClient.builder(getChatModel()).build();
		UserMessage question = new UserMessage(BOOKING_REQUEST);

		ChatResponse reply = Objects
			.requireNonNull(chatClient.prompt().messages(question).tools(bookingTool()).call().chatResponse());

		assertPaused(reply);
		assertThat(this.bookings).isEmpty();

		ToolExecutionResult result = this.toolCallingManager.executeToolCalls(prompt(question), reply,
				decideAll(reply, true));
		String answer = chatClient.prompt()
			.messages(result.conversationHistory())
			.tools(bookingTool())
			.call()
			.content();

		assertThat(this.bookings).containsExactly("VX205");
		assertThat(answer).contains(CONFIRMATION_CODE);
	}

	@Test
	void deniedCallDoesNotRunAndTheModelAnswers() {
		ChatClient chatClient = ChatClient.builder(getChatModel()).build();
		UserMessage question = new UserMessage(BOOKING_REQUEST);

		ChatResponse reply = Objects
			.requireNonNull(chatClient.prompt().messages(question).tools(bookingTool()).call().chatResponse());
		assertPaused(reply);

		ToolExecutionResult result = this.toolCallingManager.executeToolCalls(prompt(question), reply,
				decideAll(reply, false));
		ChatResponse answer = Objects.requireNonNull(
				chatClient.prompt().messages(result.conversationHistory()).tools(bookingTool()).call().chatResponse());

		assertThat(this.bookings).isEmpty();
		assertThat(answer).isNotNull();
		// The model may try again; it must not get the booking without a new decision
		if (!ToolCallDecisions.isAwaitingDecision(answer)) {
			assertThat(Objects.requireNonNull(answer.getResult()).getOutput().getText()).isNotBlank()
				.doesNotContain(CONFIRMATION_CODE);
		}
	}

	@Test
	void streamedPauseResumesWithStreamedAnswer() {
		ChatClient chatClient = ChatClient.builder(getChatModel()).build();
		UserMessage question = new UserMessage(BOOKING_REQUEST);

		List<ChatResponse> chunks = chatClient.prompt()
			.messages(question)
			.tools(bookingTool())
			.stream()
			.chatResponse()
			.collectList()
			.block();
		assertThat(chunks).isNotEmpty();
		ChatResponse last = Objects.requireNonNull(chunks).get(chunks.size() - 1);
		assertPaused(last);
		assertThat(this.bookings).isEmpty();

		ToolExecutionResult result = this.toolCallingManager.executeToolCalls(prompt(question), last,
				decideAll(last, true));
		List<String> answer = chatClient.prompt()
			.messages(result.conversationHistory())
			.tools(bookingTool())
			.stream()
			.content()
			.collectList()
			.block();

		assertThat(this.bookings).containsExactly("VX205");
		assertThat(String.join("", Objects.requireNonNull(answer))).contains(CONFIRMATION_CODE);
	}

	@Test
	void withTheMemoryInsideTheLoopTheEarlierRoundsSurviveThePause() {
		// The weather lookup may run in an earlier round, before the booking pauses; the
		// memory inside the loop keeps that round, so the model resumes with it
		ChatMemory chatMemory = MessageWindowChatMemory.builder().build();
		ChatClient chatClient = ChatClient.builder(getChatModel())
			.defaultAdvisors(
					MessageChatMemoryAdvisor.builder(chatMemory).order(ToolCallingAdvisor.DEFAULT_ORDER + 100).build())
			.build();
		String conversationId = "confirmation-" + System.nanoTime();

		ChatResponse reply = Objects.requireNonNull(chatClient.prompt()
			.user("First check the current weather in Paris in Celsius. Then book flight VX205 for me. "
					+ "Finally tell me the temperature and the confirmation code.")
			.tools(weatherTool(), bookingTool())
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
			.call()
			.chatResponse());
		assertPaused(reply);
		assertThat(this.bookings).isEmpty();

		// The memory holds the conversation; the prompt only carries the tools
		ToolExecutionResult result = this.toolCallingManager.executeToolCalls(prompt(), reply, decideAll(reply, true));
		List<Message> history = result.conversationHistory();
		String answer = chatClient.prompt()
			.messages(history.get(history.size() - 1))
			.tools(weatherTool(), bookingTool())
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
			.call()
			.content();

		assertThat(this.bookings).containsExactly("VX205");
		assertThat(answer).contains(CONFIRMATION_CODE).contains("15");
		assertThat(chatMemory.get(conversationId)).filteredOn(ToolResponseMessage.class::isInstance)
			.flatExtracting(message -> ((ToolResponseMessage) message).getResponses())
			.extracting(ToolResponseMessage.ToolResponse::name)
			.contains("getCurrentWeather", "bookFlight");
	}

	/** The booking tool, marked to need a person's confirmation. */
	protected ToolCallback bookingTool() {
		return FunctionToolCallback.builder("bookFlight", (BookingRequest request) -> {
			this.bookings.add(request.flightNumber());
			return new Booking(request.flightNumber(), CONFIRMATION_CODE);
		})
			.description("Book a seat on the given flight. Returns the booking confirmation code.")
			.inputType(BookingRequest.class)
			.toolMetadata(ToolMetadata.builder().requiresConfirmation(true).build())
			.build();
	}

	/** The weather tool, which needs no confirmation. */
	protected ToolCallback weatherTool() {
		return FunctionToolCallback
			.builder("getCurrentWeather",
					(WeatherRequest request) -> new Weather(request.city(),
							request.city().toLowerCase(Locale.ROOT).contains("paris") ? 15 : 20))
			.description("Get the current temperature in Celsius in the given city.")
			.inputType(WeatherRequest.class)
			.build();
	}

	/** The prompt the manager resumes with: the conversation, carrying the tools. */
	private Prompt prompt(Message... conversation) {
		return new Prompt(List.of(conversation),
				ToolCallingChatOptions.builder().toolCallbacks(List.of(weatherTool(), bookingTool())).build());
	}

	private static void assertPaused(ChatResponse reply) {
		assertThat(reply).isNotNull();
		assertThat(ToolCallDecisions.awaitingDecision(reply)).isNotEmpty();
		assertThat(toolCalls(reply)).extracting(AssistantMessage.ToolCall::name).contains("bookFlight");
	}

	/**
	 * A decision on every call of the reply: allow them all, or deny them all. The
	 * decisions name the calls by key, since some providers give tool calls no id.
	 */
	private static ToolCallDecisions decideAll(ChatResponse reply, boolean allowed) {
		return new ToolCallDecisions(ToolCallDecisions.toolCallKeys(toolCallsMessage(reply))
			.stream()
			.map(key -> allowed ? ToolCallDecision.allow(key)
					: ToolCallDecision.deny(key, "The user declined this booking."))
			.toList());
	}

	private static List<AssistantMessage.ToolCall> toolCalls(ChatResponse reply) {
		return toolCallsMessage(reply).getToolCalls();
	}

	/** The reply's message with the tool calls, the one the manager looks at. */
	private static AssistantMessage toolCallsMessage(ChatResponse reply) {
		return reply.getResults()
			.stream()
			.filter(generation -> generation.getOutput().hasToolCalls())
			.findFirst()
			.map(generation -> generation.getOutput())
			.orElseGet(() -> AssistantMessage.builder().content("").build());
	}

	/** The booking request the model sends. */
	public record BookingRequest(@JsonProperty(required = true,
			value = "flightNumber") @JsonPropertyDescription("The flight number, e.g. VX205") String flightNumber) {
	}

	/** The booking the tool returns. */
	public record Booking(String flightNumber, String confirmationCode) {
	}

	/** The weather request the model sends: one argument, so small models manage it. */
	public record WeatherRequest(@JsonProperty(required = true,
			value = "city") @JsonPropertyDescription("The city name, e.g. Paris") String city) {
	}

	/** The weather the tool returns. */
	public record Weather(String city, int temperatureCelsius) {
	}

}
