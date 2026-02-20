/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.anthropic;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.anthropic.api.AnthropicCacheOptions;
import org.springframework.ai.anthropic.api.AnthropicCacheStrategy;
import org.springframework.ai.anthropic.api.AnthropicCacheTtl;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.support.ToolDefinitions;
import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mock tests for Anthropic prompt caching functionality with tool calling validation.
 * Tests the wire format and cache control headers without requiring real API calls.
 *
 * @author Mark Pollack
 * @author Austin Dase
 * @since 1.1.0
 */
class AnthropicPromptCachingMockTest {

	private MockWebServer mockWebServer;

	private AnthropicChatModel chatModel;

	@BeforeEach
	void setUp() throws IOException {
		this.mockWebServer = new MockWebServer();
		this.mockWebServer.start();

		String baseUrl = this.mockWebServer.url("/").toString();
		AnthropicApi anthropicApi = AnthropicApi.builder().apiKey("test-api-key").baseUrl(baseUrl).build();
		this.chatModel = AnthropicChatModel.builder().anthropicApi(anthropicApi).build();
	}

	@AfterEach
	void tearDown() throws IOException {
		this.mockWebServer.shutdown();
	}

	@Test
	void testSystemOnlyCacheStrategy() throws Exception {
		// Mock response
		String mockResponse = """
				{
					"id": "msg_test123",
					"type": "message",
					"role": "assistant",
					"content": [
						{
							"type": "text",
							"text": "Hello! I understand you want to test caching."
						}
					],
					"model": "claude-haiku-4-5",
					"stop_reason": "end_turn",
					"stop_sequence": null,
					"usage": {
						"input_tokens": 50,
						"output_tokens": 20
					}
				}
				""";

		this.mockWebServer
			.enqueue(new MockResponse().setBody(mockResponse).setHeader("Content-Type", "application/json"));

		// Create tool callback to test that tools are NOT cached with SYSTEM_ONLY
		var toolMethod = ReflectionUtils.findMethod(TestTools.class, "getWeather", String.class);
		MethodToolCallback toolCallback = MethodToolCallback.builder()
			.toolDefinition(ToolDefinitions.builder(toolMethod).description("Get weather for a location").build())
			.toolMethod(toolMethod)
			.build();

		// Test with SYSTEM_ONLY cache strategy
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.cacheOptions(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.SYSTEM_ONLY).build())
			.toolCallbacks(List.of(toolCallback))
			.build();

		Prompt prompt = new Prompt(
				List.of(new SystemMessage("You are a helpful assistant."), new UserMessage("Test message")), options);

		ChatResponse response = this.chatModel.call(prompt);

		// Verify request was made
		RecordedRequest recordedRequest = this.mockWebServer.takeRequest(1, TimeUnit.SECONDS);
		assertThat(recordedRequest).isNotNull();

		// Parse and validate request body
		JsonNode requestBody = JsonMapper.shared().readTree(recordedRequest.getBody().readUtf8());

		// Verify system message has cache control
		assertThat(requestBody.has("system")).isTrue();
		JsonNode systemNode = requestBody.get("system");
		if (systemNode.isArray()) {
			JsonNode lastSystemBlock = systemNode.get(systemNode.size() - 1);
			assertThat(lastSystemBlock.has("cache_control")).isTrue();
			assertThat(lastSystemBlock.get("cache_control").get("type").asText()).isEqualTo("ephemeral");
		}

		// Verify tools exist but DO NOT have cache_control (key difference from
		// SYSTEM_AND_TOOLS)
		if (requestBody.has("tools")) {
			JsonNode toolsArray = requestBody.get("tools");
			assertThat(toolsArray.isArray()).isTrue();
			// Verify NO tool has cache_control
			for (int i = 0; i < toolsArray.size(); i++) {
				JsonNode tool = toolsArray.get(i);
				assertThat(tool.has("cache_control")).isFalse();
			}
		}

		// Verify response
		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput().getText()).contains("Hello!");
	}

	@Test
	void testSystemMinLengthDisablesCaching() throws Exception {
		String mockResponse = """
				{
					"id": "msg_test123",
					"type": "message",
					"role": "assistant",
					"content": [ { "type": "text", "text": "ok" } ],
					"model": "claude-haiku-4-5",
					"stop_reason": "end_turn",
					"usage": { "input_tokens": 10, "output_tokens": 2 }
				}
				""";
		this.mockWebServer
			.enqueue(new MockResponse().setBody(mockResponse).setHeader("Content-Type", "application/json"));

		AnthropicCacheOptions cacheOptions = AnthropicCacheOptions.builder()
			.strategy(AnthropicCacheStrategy.SYSTEM_ONLY)
			.messageTypeMinContentLength(MessageType.SYSTEM, 1000)
			.build();

		AnthropicChatOptions options = AnthropicChatOptions.builder().cacheOptions(cacheOptions).build();
		Prompt prompt = new Prompt(List.of(new SystemMessage("short"), new UserMessage("Test message")), options);
		this.chatModel.call(prompt);

		RecordedRequest recordedRequest = this.mockWebServer.takeRequest(1, TimeUnit.SECONDS);
		assertThat(recordedRequest).isNotNull();
		JsonNode requestBody = JsonMapper.shared().readTree(recordedRequest.getBody().readUtf8());

		// Ensure no cache_control present since system content was below min length
		String req = requestBody.toString();
		assertThat(req).doesNotContain("\"cache_control\"");
	}

	@Test
	void testCustomContentLengthFunctionEnablesCaching() throws Exception {
		String mockResponse = """
				{
					"id": "msg_test123",
					"type": "message",
					"role": "assistant",
					"content": [ { "type": "text", "text": "ok" } ],
					"model": "claude-haiku-4-5",
					"stop_reason": "end_turn",
					"usage": { "input_tokens": 10, "output_tokens": 2 }
				}
				""";
		this.mockWebServer
			.enqueue(new MockResponse().setBody(mockResponse).setHeader("Content-Type", "application/json"));

		AnthropicCacheOptions cacheOptions = AnthropicCacheOptions.builder()
			.strategy(AnthropicCacheStrategy.SYSTEM_ONLY)
			.messageTypeMinContentLength(MessageType.SYSTEM, 1000)
			.contentLengthFunction(s -> 2000) // force eligibility even for short text
			.build();

		AnthropicChatOptions options = AnthropicChatOptions.builder().cacheOptions(cacheOptions).build();
		Prompt prompt = new Prompt(List.of(new SystemMessage("short"), new UserMessage("Test message")), options);
		this.chatModel.call(prompt);

		RecordedRequest recordedRequest = this.mockWebServer.takeRequest(1, TimeUnit.SECONDS);
		assertThat(recordedRequest).isNotNull();
		JsonNode requestBody = JsonMapper.shared().readTree(recordedRequest.getBody().readUtf8());
		JsonNode systemNode = requestBody.get("system");
		if (systemNode != null && systemNode.isArray()) {
			JsonNode lastSystemBlock = systemNode.get(systemNode.size() - 1);
			assertThat(lastSystemBlock.has("cache_control")).isTrue();
		}
	}

	@Test
	void testSystemAndToolsCacheStrategy() throws Exception {
		// Mock response
		String mockResponse = """
				{
					"id": "msg_test123",
					"type": "message",
					"role": "assistant",
					"content": [
						{
							"type": "text",
							"text": "I'll help you with the weather information."
						}
					],
					"model": "claude-haiku-4-5",
					"stop_reason": "end_turn",
					"usage": {
						"input_tokens": 150,
						"output_tokens": 25
					}
				}
				""";

		this.mockWebServer
			.enqueue(new MockResponse().setBody(mockResponse).setHeader("Content-Type", "application/json"));

		// Create tool callback
		var toolMethod = ReflectionUtils.findMethod(TestTools.class, "getWeather", String.class);
		MethodToolCallback toolCallback = MethodToolCallback.builder()
			.toolDefinition(ToolDefinitions.builder(toolMethod).description("Get weather for a location").build())
			.toolMethod(toolMethod)
			.build();

		// Test with SYSTEM_AND_TOOLS cache strategy
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.cacheOptions(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.SYSTEM_AND_TOOLS).build())
			.toolCallbacks(List.of(toolCallback))
			.build();

		ChatClient chatClient = ChatClient.create(this.chatModel);
		String response = chatClient.prompt()
			.user("What's the weather like in San Francisco?")
			.options(options)
			.call()
			.content();

		// Verify request was made
		RecordedRequest recordedRequest = this.mockWebServer.takeRequest(1, TimeUnit.SECONDS);
		assertThat(recordedRequest).isNotNull();

		// Parse and validate request body
		JsonNode requestBody = JsonMapper.shared().readTree(recordedRequest.getBody().readUtf8());

		// Verify tools array exists and last tool has cache control
		assertThat(requestBody.has("tools")).isTrue();
		JsonNode toolsArray = requestBody.get("tools");
		assertThat(toolsArray.isArray()).isTrue();
		assertThat(toolsArray.size()).isGreaterThan(0);

		JsonNode lastTool = toolsArray.get(toolsArray.size() - 1);
		assertThat(lastTool.has("cache_control")).isTrue();
		assertThat(lastTool.get("cache_control").get("type").asText()).isEqualTo("ephemeral");

		// Verify system message also has cache control
		if (requestBody.has("system")) {
			JsonNode systemNode = requestBody.get("system");
			if (systemNode.isArray()) {
				JsonNode lastSystemBlock = systemNode.get(systemNode.size() - 1);
				assertThat(lastSystemBlock.has("cache_control")).isTrue();
			}
		}

		// Verify response
		assertThat(response).contains("weather information");
	}

	@Test
	void testConversationHistoryCacheStrategy() throws Exception {
		// Mock response
		String mockResponse = """
				{
					"id": "msg_test123",
					"type": "message",
					"role": "assistant",
					"content": [
						{
							"type": "text",
							"text": "Based on our previous conversation, I can help with that."
						}
					],
					"model": "claude-haiku-4-5",
					"stop_reason": "end_turn",
					"usage": {
						"input_tokens": 200,
						"output_tokens": 30
					}
				}
				""";

		this.mockWebServer
			.enqueue(new MockResponse().setBody(mockResponse).setHeader("Content-Type", "application/json"));

		// Test with CONVERSATION_HISTORY cache strategy
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.cacheOptions(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.CONVERSATION_HISTORY).build())
			.build();

		// Create a prompt with conversation history
		Prompt prompt = new Prompt(List.of(new UserMessage("Previous question about weather"),
				new UserMessage("What about tomorrow's forecast?")), options);

		ChatResponse response = this.chatModel.call(prompt);

		// Verify request was made
		RecordedRequest recordedRequest = this.mockWebServer.takeRequest(1, TimeUnit.SECONDS);
		assertThat(recordedRequest).isNotNull();

		// Parse and validate request body
		JsonNode requestBody = JsonMapper.shared().readTree(recordedRequest.getBody().readUtf8());

		// Verify messages array exists
		assertThat(requestBody.has("messages")).isTrue();
		JsonNode messagesArray = requestBody.get("messages");
		assertThat(messagesArray.isArray()).isTrue();
		assertThat(messagesArray.size()).isGreaterThan(1);

		// Verify the last message has cache control (conversation history)
		if (messagesArray.size() >= 1) {
			JsonNode lastMessage = messagesArray.get(messagesArray.size() - 1);
			assertThat(lastMessage.has("content")).isTrue();
			JsonNode contentArray = lastMessage.get("content");
			if (contentArray.isArray() && contentArray.size() > 0) {
				JsonNode lastContentBlock = contentArray.get(contentArray.size() - 1);
				assertThat(lastContentBlock.has("cache_control")).isTrue();
				assertThat(lastContentBlock.get("cache_control").get("type").asText()).isEqualTo("ephemeral");
			}
		}

		// Verify response
		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput().getText()).contains("previous conversation");
	}

	@Test
	void testNoCacheStrategy() throws Exception {
		// Mock response
		String mockResponse = """
				{
					"id": "msg_test123",
					"type": "message",
					"role": "assistant",
					"content": [
						{
							"type": "text",
							"text": "Simple response without caching."
						}
					],
					"model": "claude-haiku-4-5",
					"stop_reason": "end_turn",
					"usage": {
						"input_tokens": 20,
						"output_tokens": 10
					}
				}
				""";

		this.mockWebServer
			.enqueue(new MockResponse().setBody(mockResponse).setHeader("Content-Type", "application/json"));

		// Test with NONE cache strategy (default)
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.cacheOptions(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.NONE).build())
			.build();

		Prompt prompt = new Prompt("Simple test message", options);
		ChatResponse response = this.chatModel.call(prompt);

		// Verify request was made
		RecordedRequest recordedRequest = this.mockWebServer.takeRequest(1, TimeUnit.SECONDS);
		assertThat(recordedRequest).isNotNull();

		// Parse and validate request body
		JsonNode requestBody = JsonMapper.shared().readTree(recordedRequest.getBody().readUtf8());

		// Verify NO cache_control fields exist anywhere
		String requestBodyString = requestBody.toString();
		assertThat(requestBodyString).doesNotContain("cache_control");

		// Verify response
		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput().getText()).contains("Simple response");
	}

	@Test
	void testCacheTtlHeader() throws Exception {
		// Mock response
		String mockResponse = """
				{
					"id": "msg_test123",
					"type": "message",
					"role": "assistant",
					"content": [
						{
							"type": "text",
							"text": "Response with 1-hour cache TTL."
						}
					],
					"model": "claude-haiku-4-5",
					"stop_reason": "end_turn",
					"usage": {
						"input_tokens": 30,
						"output_tokens": 15
					}
				}
				""";

		this.mockWebServer
			.enqueue(new MockResponse().setBody(mockResponse).setHeader("Content-Type", "application/json"));

		// Test with 1-hour cache TTL
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.cacheOptions(AnthropicCacheOptions.builder()
				.strategy(AnthropicCacheStrategy.SYSTEM_ONLY)
				.messageTypeTtl(MessageType.SYSTEM, AnthropicCacheTtl.ONE_HOUR)
				.build())
			.build();

		Prompt prompt = new Prompt(
				List.of(new SystemMessage("You are a helpful assistant."), new UserMessage("Test message")), options);

		this.chatModel.call(prompt);

		// Verify request was made
		RecordedRequest recordedRequest = this.mockWebServer.takeRequest(1, TimeUnit.SECONDS);
		assertThat(recordedRequest).isNotNull();

		// Verify the beta header is present for 1-hour cache
		assertThat(recordedRequest.getHeader("anthropic-beta")).contains("extended-cache-ttl-2025-04-11");
	}

	@Test
	void testFourBreakpointLimitEnforcement() throws Exception {
		// Mock response
		String mockResponse = """
				{
					"id": "msg_test123",
					"type": "message",
					"role": "assistant",
					"content": [
						{
							"type": "text",
							"text": "Response with maximum cache breakpoints."
						}
					],
					"model": "claude-haiku-4-5",
					"stop_reason": "end_turn",
					"usage": {
						"input_tokens": 500,
						"output_tokens": 20
					}
				}
				""";

		this.mockWebServer
			.enqueue(new MockResponse().setBody(mockResponse).setHeader("Content-Type", "application/json"));

		// Create multiple tools to test breakpoint limits
		var weatherMethod = ReflectionUtils.findMethod(TestTools.class, "getWeather", String.class);
		var calculateMethod = ReflectionUtils.findMethod(TestTools.class, "calculate", String.class);
		var searchMethod = ReflectionUtils.findMethod(TestTools.class, "search", String.class);

		MethodToolCallback weatherTool = MethodToolCallback.builder()
			.toolDefinition(ToolDefinitions.builder(weatherMethod).description("Get weather information").build())
			.toolMethod(weatherMethod)
			.build();

		MethodToolCallback calculateTool = MethodToolCallback.builder()
			.toolDefinition(ToolDefinitions.builder(calculateMethod).description("Calculate expressions").build())
			.toolMethod(calculateMethod)
			.build();

		MethodToolCallback searchTool = MethodToolCallback.builder()
			.toolDefinition(ToolDefinitions.builder(searchMethod).description("Search for information").build())
			.toolMethod(searchMethod)
			.build();

		// Test with SYSTEM_AND_TOOLS strategy and multiple large system messages
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.cacheOptions(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.SYSTEM_AND_TOOLS).build())
			.toolCallbacks(List.of(weatherTool, calculateTool, searchTool))
			.build();

		// Create multiple large system messages and user messages to potentially exceed 4
		// breakpoints
		String largeSystemMsg1 = "System message 1: " + "A".repeat(1200);
		String largeSystemMsg2 = "System message 2: " + "B".repeat(1200);
		String largeUserMsg1 = "User message 1: " + "C".repeat(1200);
		String largeUserMsg2 = "User message 2: " + "D".repeat(1200);

		Prompt prompt = new Prompt(List.of(new SystemMessage(largeSystemMsg1), new SystemMessage(largeSystemMsg2),
				new UserMessage(largeUserMsg1), new UserMessage(largeUserMsg2)), options);

		this.chatModel.call(prompt);

		// Verify request was made
		RecordedRequest recordedRequest = this.mockWebServer.takeRequest(1, TimeUnit.SECONDS);
		assertThat(recordedRequest).isNotNull();

		// Parse and validate request body
		JsonNode requestBody = JsonMapper.shared().readTree(recordedRequest.getBody().readUtf8());

		// Count cache_control occurrences in the entire request
		int cacheControlCount = countCacheControlOccurrences(requestBody);

		// Verify we don't exceed Anthropic's 4-breakpoint limit
		assertThat(cacheControlCount)
			.withFailMessage("Cache breakpoints should not exceed 4, but found %d", cacheControlCount)
			.isLessThanOrEqualTo(4);
	}

	@Test
	void testWireFormatConsistency() throws Exception {
		// Mock response
		String mockResponse = """
				{
					"id": "msg_test123",
					"type": "message",
					"role": "assistant",
					"content": [
						{
							"type": "text",
							"text": "Response for wire format test."
						}
					],
					"model": "claude-haiku-4-5",
					"stop_reason": "end_turn",
					"usage": {
						"input_tokens": 200,
						"output_tokens": 15
					}
				}
				""";

		this.mockWebServer
			.enqueue(new MockResponse().setBody(mockResponse).setHeader("Content-Type", "application/json"));

		// Test with SYSTEM_ONLY caching strategy
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.cacheOptions(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.SYSTEM_ONLY).build())
			.build();

		Prompt prompt = new Prompt(
				List.of(new SystemMessage("You are a helpful assistant."), new UserMessage("Hello!")), options);

		this.chatModel.call(prompt);

		// Verify request was made
		RecordedRequest recordedRequest = this.mockWebServer.takeRequest(1, TimeUnit.SECONDS);
		assertThat(recordedRequest).isNotNull();

		// Parse and validate request body
		JsonNode requestBody = JsonMapper.shared().readTree(recordedRequest.getBody().readUtf8());

		// Verify that cache_control is included in the wire format for SYSTEM_ONLY
		// strategy
		// Anthropic's API will handle token threshold validation

		// For SYSTEM_ONLY caching, system message should be in the "system" field with
		// cache_control
		assertThat(requestBody.has("system")).withFailMessage("SYSTEM_ONLY strategy should include system field")
			.isTrue();

		JsonNode systemNode = requestBody.get("system");
		if (systemNode.isArray()) {
			JsonNode lastSystemBlock = systemNode.get(systemNode.size() - 1);
			assertThat(lastSystemBlock.has("cache_control"))
				.withFailMessage("SYSTEM_ONLY strategy should include cache_control in wire format")
				.isTrue();
			assertThat(lastSystemBlock.get("cache_control").get("type").asText()).isEqualTo("ephemeral");
		}
		else if (systemNode.isTextual()) {
			// Simple text system message should still have cache_control applied at the
			// message level
			// Check if there's a cache_control field at the system level or in a wrapper
			assertThat(requestBody.toString())
				.withFailMessage("SYSTEM_ONLY strategy should include cache_control in wire format")
				.contains("cache_control");
		}
	}

	@Test
	void testComplexMultiBreakpointScenario() throws Exception {
		// Mock response
		String mockResponse = """
				{
					"id": "msg_test123",
					"type": "message",
					"role": "assistant",
					"content": [
						{
							"type": "text",
							"text": "Response for complex multi-breakpoint scenario."
						}
					],
					"model": "claude-haiku-4-5",
					"stop_reason": "end_turn",
					"usage": {
						"input_tokens": 800,
						"output_tokens": 25
					}
				}
				""";

		this.mockWebServer
			.enqueue(new MockResponse().setBody(mockResponse).setHeader("Content-Type", "application/json"));

		// Create tools for complex scenario
		var toolMethod = ReflectionUtils.findMethod(TestTools.class, "getWeather", String.class);
		MethodToolCallback toolCallback = MethodToolCallback.builder()
			.toolDefinition(ToolDefinitions.builder(toolMethod).description("Complex weather tool").build())
			.toolMethod(toolMethod)
			.build();

		// Test SYSTEM_AND_TOOLS with large content and conversation history
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.cacheOptions(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.SYSTEM_AND_TOOLS).build())
			.toolCallbacks(List.of(toolCallback))
			.build();

		// Create large system message (should get cached)
		String largeSystemMessage = "System: You are a weather assistant. " + "X".repeat(1200);

		// Create conversation with multiple user messages (history scenario)
		String userMessage1 = "Previous question about weather in NYC " + "Y".repeat(1200);
		String userMessage2 = "Follow-up question about tomorrow's forecast " + "Z".repeat(1200);
		String currentUserMessage = "What about this weekend?";

		Prompt prompt = new Prompt(List.of(new SystemMessage(largeSystemMessage), new UserMessage(userMessage1),
				new UserMessage(userMessage2), new UserMessage(currentUserMessage)), options);

		this.chatModel.call(prompt);

		// Verify request was made
		RecordedRequest recordedRequest = this.mockWebServer.takeRequest(1, TimeUnit.SECONDS);
		assertThat(recordedRequest).isNotNull();

		// Parse and validate request body
		JsonNode requestBody = JsonMapper.shared().readTree(recordedRequest.getBody().readUtf8());

		// Verify system message has cache control (SYSTEM_AND_TOOLS strategy)
		assertThat(requestBody.has("system")).isTrue();
		JsonNode systemNode = requestBody.get("system");
		if (systemNode.isArray()) {
			JsonNode lastSystemBlock = systemNode.get(systemNode.size() - 1);
			assertThat(lastSystemBlock.has("cache_control")).isTrue();
		}

		// Verify tools have cache control (SYSTEM_AND_TOOLS strategy)
		assertThat(requestBody.has("tools")).isTrue();
		JsonNode toolsArray = requestBody.get("tools");
		if (toolsArray.isArray() && toolsArray.size() > 0) {
			JsonNode lastTool = toolsArray.get(toolsArray.size() - 1);
			assertThat(lastTool.has("cache_control")).isTrue();
		}

		// Verify proper ordering and cache control placement
		int cacheControlCount = countCacheControlOccurrences(requestBody);
		assertThat(cacheControlCount)
			.withFailMessage("Complex scenario should not exceed 4 cache breakpoints, found %d", cacheControlCount)
			.isLessThanOrEqualTo(4);

		// Verify cache_control is only on the LAST blocks of each section (system, tools)
		// This ensures proper breakpoint placement according to Anthropic's requirements
		verifyCacheControlPlacement(requestBody);
	}

	/**
	 * Helper method to count cache_control occurrences in the request JSON.
	 */
	private int countCacheControlOccurrences(JsonNode node) {
		int count = 0;
		if (node.isObject()) {
			if (node.has("cache_control")) {
				count++;
			}
			for (JsonNode child : node.values()) {
				count += countCacheControlOccurrences(child);
			}
		}
		else if (node.isArray()) {
			for (JsonNode child : node) {
				count += countCacheControlOccurrences(child);
			}
		}
		return count;
	}

	/**
	 * Helper method to verify cache_control is only placed on the last blocks of each
	 * section.
	 */
	private void verifyCacheControlPlacement(JsonNode requestBody) {
		// Verify system cache control is only on the last system block
		if (requestBody.has("system")) {
			JsonNode systemNode = requestBody.get("system");
			if (systemNode.isArray()) {
				for (int i = 0; i < systemNode.size() - 1; i++) {
					JsonNode systemBlock = systemNode.get(i);
					assertThat(systemBlock.has("cache_control"))
						.withFailMessage("Only the last system block should have cache_control, but block %d has it", i)
						.isFalse();
				}
			}
		}

		// Verify tools cache control is only on the last tool
		if (requestBody.has("tools")) {
			JsonNode toolsArray = requestBody.get("tools");
			if (toolsArray.isArray()) {
				for (int i = 0; i < toolsArray.size() - 1; i++) {
					JsonNode tool = toolsArray.get(i);
					assertThat(tool.has("cache_control"))
						.withFailMessage("Only the last tool should have cache_control, but tool %d has it", i)
						.isFalse();
				}
			}
		}

		// Verify messages cache control is only on the last content block of the
		// appropriate message
		if (requestBody.has("messages")) {
			JsonNode messagesArray = requestBody.get("messages");
			if (messagesArray.isArray()) {
				// For conversation history caching, only second-to-last message should
				// have cache control
				for (int i = 0; i < messagesArray.size(); i++) {
					JsonNode message = messagesArray.get(i);
					if (message.has("content") && message.get("content").isArray()) {
						JsonNode contentArray = message.get("content");
						for (int j = 0; j < contentArray.size() - 1; j++) {
							JsonNode contentBlock = contentArray.get(j);
							if (i != messagesArray.size() - 2 || j != contentArray.size() - 1) {
								// Only the last content block of the second-to-last
								// message should have cache_control
								assertThat(contentBlock.has("cache_control"))
									.withFailMessage(
											"Unexpected cache_control placement in message %d, content block %d", i, j)
									.isFalse();
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Test tools class for mock testing.
	 */
	public static class TestTools {

		@Tool(description = "Get weather information for a location")
		public static String getWeather(String location) {
			return "Weather in " + location + " is sunny, 22°C";
		}

		@Tool(description = "Calculate mathematical expressions")
		public static String calculate(String expression) {
			return "Result: 42";
		}

		@Tool(description = "Search for information")
		public static String search(String query) {
			return "Search results for: " + query;
		}

	}

}
