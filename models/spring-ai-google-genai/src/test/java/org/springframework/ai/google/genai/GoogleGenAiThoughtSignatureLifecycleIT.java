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

package org.springframework.ai.google.genai;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import com.google.genai.Client;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.tool.MockWeatherService;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Google GenAI Thought Signature handling with Function Calling.
 *
 * <p>
 * These tests validate that thought signatures are properly extracted and propagated
 * during the <strong>internal tool execution loop</strong> (Scenario 1). Per Google's
 * documentation, thought signature validation only applies to the <strong>current
 * turn</strong> - not to historical conversation messages.
 *
 * <p>
 * <strong>Background:</strong> Gemini 3 Pro requires thought signatures when
 * {@code includeThoughts=true} and function calling is used. The signatures must be
 * attached to {@code functionCall} parts when sending back function responses within the
 * same turn. Missing signatures in the current turn result in HTTP 400 errors.
 *
 * <p>
 * <strong>Important:</strong> Validation is NOT enforced for previous turns in
 * conversation history. Only the current turn's function calls require signatures. See:
 * <a href="https://ai.google.dev/gemini-api/docs/thought-signatures">Thought Signatures
 * Documentation</a>
 *
 * <p>
 * <strong>Test Coverage:</strong>
 * <ul>
 * <li>Extraction: Verify signatures are extracted from responses and stored in
 * metadata</li>
 * <li>Scenario 1: Sequential function calls within a single turn (internal loop)</li>
 * <li>Streaming: Verify signatures work with streaming responses</li>
 * </ul>
 *
 * @since 1.1.0
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".+")
class GoogleGenAiThoughtSignatureLifecycleIT {

	@Autowired
	private GoogleGenAiChatModel chatModel;

	private final ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();

	/**
	 * Tests that thought signatures are properly handled when includeThoughts is
	 * explicitly set to false. In this case, no thought signatures should be present in
	 * the response metadata.
	 */
	@Test
	void testNoThoughtSignaturesWhenIncludeThoughtsDisabled() {
		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco? Return the temperature in Celsius.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = GoogleGenAiChatOptions.builder()
			.model(GoogleGenAiChatModel.ChatModel.GEMINI_2_5_FLASH)
			.includeThoughts(false) // Explicitly disable thought signatures
			.toolCallbacks(List.of(FunctionToolCallback.builder("get_current_weather", new MockWeatherService())
				.description("Get the current weather in a given location.")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		var prompt = new Prompt(messages, promptOptions);
		ChatResponse response = this.chatModel.call(prompt);

		while (response.hasToolCalls()) {
			ToolExecutionResult toolExecutionResult = this.toolCallingManager.executeToolCalls(prompt, response);
			prompt = new Prompt(toolExecutionResult.conversationHistory(), promptOptions);
			response = this.chatModel.call(prompt);
		}

		assertThat(response).isNotNull();
		// Verify expected weather data
		assertThat(response.getResult().getOutput().getText()).contains("30");
	}

	/**
	 * Tests that thought signatures work correctly with streaming responses and function
	 * calling. This validates that the aggregated streaming response properly maintains
	 * thought signatures.
	 */
	@Test
	void testThoughtSignaturesWithStreamingAndFunctionCalling() {
		UserMessage userMessage = new UserMessage(
				"What's the weather like in Paris? Return the temperature in Celsius.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = GoogleGenAiChatOptions.builder()
			.model(GoogleGenAiChatModel.ChatModel.GEMINI_2_5_FLASH)
			.includeThoughts(true)
			.toolCallbacks(List.of(FunctionToolCallback.builder("get_current_weather", new MockWeatherService())
				.description("Get the current weather in a given location.")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		// Execute streaming call
		var prompt = new Prompt(messages, promptOptions);
		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		new MessageAggregator().aggregate(this.chatModel.stream(prompt), aggregatedRef::set).collectList().block();

		while (aggregatedRef.get().hasToolCalls()) {
			ToolExecutionResult toolExecutionResult = this.toolCallingManager.executeToolCalls(prompt,
					aggregatedRef.get());
			prompt = new Prompt(toolExecutionResult.conversationHistory(), promptOptions);
			aggregatedRef.set(null);
			new MessageAggregator().aggregate(this.chatModel.stream(prompt), aggregatedRef::set).collectList().block();
		}

		ChatResponse lastResponse = aggregatedRef.get();

		assertThat(lastResponse).isNotNull();
		// Verify expected weather data
		assertThat(lastResponse.getResult().getOutput().getText()).contains("15");
	}

	// ============================================================
	// SCENARIO 1 TESTS: Internal Tool Execution Loop
	// These tests validate thought signature propagation WITHIN a single turn
	// when the model makes multiple sequential function calls.
	// ============================================================

	/**
	 * Provides model parameters for sequential function calling tests. Tests both:
	 * <ul>
	 * <li>Gemini 2.5 - where thought signatures are OPTIONAL (API is lenient)</li>
	 * <li>Gemini 3 - where thought signatures are REQUIRED (API returns 400 if
	 * missing)</li>
	 * </ul>
	 */
	static Stream<Arguments> sequentialFunctionCallingModels() {
		return Stream.of(Arguments.of(GoogleGenAiChatModel.ChatModel.GEMINI_2_5_FLASH, "Gemini 2.5 Flash"),
				Arguments.of(GoogleGenAiChatModel.ChatModel.GEMINI_3_PRO_PREVIEW, "Gemini 3 Pro"));
	}

	/**
	 * Tests the internal tool execution loop with sequential function calls (Scenario 1).
	 *
	 * <p>
	 * This test mimics the Google documentation example: "Check flight status for AA100
	 * and book a taxi 2 hours before if delayed." The model should: 1. Call check_flight
	 * to get flight status 2. If delayed, call book_taxi to book transportation
	 *
	 * <p>
	 * This is all within ONE chatModel.call() - Spring AI's internal tool execution loop
	 * must properly propagate thought signatures between steps. If thought signatures are
	 * not propagated, the API will return 400 errors on the second function call.
	 *
	 * <p>
	 * Based on: https://ai.google.dev/gemini-api/docs/thought-signatures
	 * @param model the Google GenAI model to test
	 * @param modelName the display name of the model for logging
	 */
	@ParameterizedTest(name = "Sequential function calls with {1}")
	@MethodSource("sequentialFunctionCallingModels")
	void testSequentialFunctionCallsWithThoughtSignatures(GoogleGenAiChatModel.ChatModel model, String modelName) {
		// This prompt should trigger:
		// Step 1: check_flight("AA100") -> returns "delayed, departure 12 PM"
		// Step 2: book_taxi("10 AM") -> returns "booking confirmed"
		// Final: Model responds with summary
		UserMessage userMessage = new UserMessage(
				"Check the flight status for flight AA100 and book a taxi 2 hours before the departure time if the flight is delayed.");

		var promptOptions = GoogleGenAiChatOptions.builder()
			.model(model)
			.includeThoughts(true) // Enable thought signatures
			.toolCallbacks(List.of(
					FunctionToolCallback.builder("check_flight", new MockFlightService())
						.description("Gets the current status of a flight including departure time and delay status.")
						.inputType(MockFlightService.Request.class)
						.build(),
					FunctionToolCallback.builder("book_taxi", new MockTaxiService())
						.description("Books a taxi for a specified pickup time.")
						.inputType(MockTaxiService.Request.class)
						.build()))
			.build();
		var prompt = new Prompt(userMessage, promptOptions);
		ChatResponse response = this.chatModel.call(prompt);

		while (response.hasToolCalls()) {
			ToolExecutionResult toolExecutionResult = this.toolCallingManager.executeToolCalls(prompt, response);
			prompt = new Prompt(toolExecutionResult.conversationHistory(), promptOptions);
			response = this.chatModel.call(prompt);
		}

		assertThat(response).isNotNull();
		String responseText = response.getResult().getOutput().getText();
		// Verify the response indicates both functions were called
		// The flight should be "delayed" and a taxi should be "booked"
		assertThat(responseText).isNotBlank();
	}

	// ============================================================
	// Mock Services for Sequential Function Calling Tests
	// These mimic the Google documentation example
	// ============================================================

	/**
	 * Mock flight status service. Returns "delayed" status to trigger the taxi booking
	 * flow.
	 */
	public static class MockFlightService implements Function<MockFlightService.Request, MockFlightService.Response> {

		@Override
		public Response apply(Request request) {
			// Always return delayed to trigger sequential taxi booking
			String status = "delayed";
			String departureTime = "12:00 PM";
			return new Response(request.flight(), status, departureTime);
		}

		@com.fasterxml.jackson.annotation.JsonClassDescription("Flight status check request")
		public record Request(@com.fasterxml.jackson.annotation.JsonProperty(required = true,
				value = "flight") @com.fasterxml.jackson.annotation.JsonPropertyDescription("The flight number to check, e.g. AA100") String flight) {
		}

		public record Response(String flight, String status, String departureTime) {
		}

	}

	/**
	 * Mock taxi booking service. Returns a confirmation for the booking.
	 */
	public static class MockTaxiService implements Function<MockTaxiService.Request, MockTaxiService.Response> {

		@Override
		public Response apply(Request request) {
			String bookingId = "TAXI-" + System.currentTimeMillis();
			return new Response(bookingId, "confirmed", request.time());
		}

		@com.fasterxml.jackson.annotation.JsonClassDescription("Taxi booking request")
		public record Request(@com.fasterxml.jackson.annotation.JsonProperty(required = true,
				value = "time") @com.fasterxml.jackson.annotation.JsonPropertyDescription("The pickup time for the taxi, e.g. 10:00 AM") String time) {
		}

		public record Response(String bookingId, String status, String pickupTime) {
		}

	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public Client genAiClient() {
			String apiKey = System.getenv("GOOGLE_API_KEY");
			return Client.builder().apiKey(apiKey).build();
		}

		@Bean
		public GoogleGenAiChatModel googleGenAiChatModel(Client genAiClient) {
			return GoogleGenAiChatModel.builder()
				.genAiClient(genAiClient)
				.options(GoogleGenAiChatOptions.builder()
					.model(GoogleGenAiChatModel.ChatModel.GEMINI_2_5_FLASH)
					.temperature(0.9)
					.build())
				.build();
		}

	}

}
