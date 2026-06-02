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

package org.springframework.ai.anthropic;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.AnthropicClientAsync;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.Headers;
import com.anthropic.core.http.HttpResponseFor;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageDeltaUsage;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawMessageDeltaEvent;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.Usage;
import com.anthropic.services.async.MessageServiceAsync;
import com.anthropic.services.blocking.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.springframework.ai.anthropic.metadata.AnthropicRateLimit;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link AnthropicChatModel}. Tests request building and response parsing
 * with mocked SDK client.
 *
 * @author Soby Chacko
 * @author Sebastien Deleuze
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnthropicChatModelTests {

	@Mock
	private AnthropicClient anthropicClient;

	@Mock
	private AnthropicClientAsync anthropicClientAsync;

	@Mock
	private MessageService messageService;

	@Mock
	private MessageService.WithRawResponse messageServiceWithRawResponse;

	@Mock
	private MessageServiceAsync messageServiceAsync;

	@Mock
	private MessageServiceAsync.WithRawResponse messageServiceAsyncWithRawResponse;

	private AnthropicChatModel chatModel;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		given(this.anthropicClient.messages()).willReturn(this.messageService);
		given(this.messageService.withRawResponse()).willReturn(this.messageServiceWithRawResponse);
		given(this.messageServiceWithRawResponse.create(any(MessageCreateParams.class))).willAnswer(invocation -> {
			MessageCreateParams params = invocation.getArgument(0);
			Message message = this.messageService.create(params);
			HttpResponseFor<Message> rawResponse = mock(HttpResponseFor.class);
			given(rawResponse.parse()).willReturn(message);
			given(rawResponse.headers()).willReturn(Headers.builder().build());
			return rawResponse;
		});

		this.chatModel = AnthropicChatModel.builder()
			.anthropicClient(this.anthropicClient)
			.anthropicClientAsync(this.anthropicClientAsync)
			.options(AnthropicChatOptions.builder()
				.model(Model.CLAUDE_SONNET_4_20250514)
				.maxTokens(1024)
				.temperature(0.7)
				.build())
			.build();
	}

	@Test
	void callWithSimpleUserMessage() {
		Message mockResponse = createMockMessage("Hello! How can I help you today?", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);

		ChatResponse response = this.chatModel.call(new Prompt("Hello"));

		assertThat(response).isNotNull();
		assertThat(response.getResult()).isNotNull();
		assertThat(response.getResult().getOutput().getText()).isEqualTo("Hello! How can I help you today?");

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(this.messageService).create(captor.capture());

		MessageCreateParams request = captor.getValue();
		assertThat(request.model().asString()).isEqualTo("claude-sonnet-4-20250514");
		assertThat(request.maxTokens()).isEqualTo(1024);
	}

	@Test
	void callWithSystemAndUserMessages() {
		Message mockResponse = createMockMessage("I am a helpful assistant.", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);

		SystemMessage systemMessage = new SystemMessage("You are a helpful assistant.");
		UserMessage userMessage = new UserMessage("Who are you?");

		ChatResponse response = this.chatModel.call(new Prompt(List.of(systemMessage, userMessage)));

		assertThat(response.getResult().getOutput().getText()).isEqualTo("I am a helpful assistant.");

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(this.messageService).create(captor.capture());

		MessageCreateParams request = captor.getValue();
		assertThat(request.system()).isPresent();
	}

	@Test
	void callWithRuntimeOptionsOverride() {
		Message mockResponse = createMockMessage("Response with override", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);

		AnthropicChatOptions runtimeOptions = AnthropicChatOptions.builder()
			.model("claude-3-opus-20240229")
			.maxTokens(2048)
			.temperature(0.3)
			.build();

		ChatResponse response = this.chatModel.call(new Prompt("Test", runtimeOptions));

		assertThat(response).isNotNull();

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(this.messageService).create(captor.capture());

		MessageCreateParams request = captor.getValue();
		assertThat(request.model().asString()).isEqualTo("claude-3-opus-20240229");
		assertThat(request.maxTokens()).isEqualTo(2048);
	}

	@Test
	void responseContainsUsageMetadata() {
		Message mockResponse = createMockMessage("Test response", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);

		ChatResponse response = this.chatModel.call(new Prompt("Test"));

		assertThat(response.getMetadata()).isNotNull();
		assertThat(response.getMetadata().getUsage()).isNotNull();
		assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(10);
		assertThat(response.getMetadata().getUsage().getCompletionTokens()).isEqualTo(20);
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isEqualTo(30);
	}

	@Test
	void responseContainsFinishReason() {
		Message mockResponse = createMockMessage("Stopped at max tokens", StopReason.MAX_TOKENS);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);

		ChatResponse response = this.chatModel.call(new Prompt("Test"));

		assertThat(response.getResult().getMetadata().getFinishReason()).isEqualTo("max_tokens");
	}

	@Test
	void responseWithToolUseBlock() {
		Message mockResponse = createMockMessageWithToolUse("toolu_123", "getCurrentWeather",
				JsonValue.from(java.util.Map.of("location", "San Francisco")), StopReason.TOOL_USE);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);

		ChatResponse response = this.chatModel
			.call(new Prompt("What's the weather?", AnthropicChatOptions.builder().build()));

		assertThat(response.getResult()).isNotNull();
		AssistantMessage output = response.getResult().getOutput();
		assertThat(output.getToolCalls()).isNotEmpty();
		assertThat(output.getToolCalls()).hasSize(1);

		var toolCall = output.getToolCalls().get(0);
		assertThat(toolCall.id()).isEqualTo("toolu_123");
		assertThat(toolCall.name()).isEqualTo("getCurrentWeather");
		assertThat(toolCall.arguments()).contains("San Francisco");
	}

	@Test
	void cacheOptionsIsMergedFromRuntimePrompt() {
		AnthropicChatModel model = AnthropicChatModel.builder()
			.anthropicClient(this.anthropicClient)
			.anthropicClientAsync(this.anthropicClientAsync)
			.options(AnthropicChatOptions.builder().model("default-model").maxTokens(1000).build())
			.build();

		AnthropicCacheOptions cacheOptions = AnthropicCacheOptions.builder()
			.strategy(AnthropicCacheStrategy.SYSTEM_ONLY)
			.build();

		AnthropicChatOptions runtimeOptions = AnthropicChatOptions.builder().cacheOptions(cacheOptions).build();

		Prompt originalPrompt = new Prompt("Test", runtimeOptions);
		Prompt requestPrompt = model.buildRequestPrompt(originalPrompt);

		AnthropicChatOptions mergedOptions = (AnthropicChatOptions) requestPrompt.getOptions();
		assertThat(mergedOptions.getCacheOptions()).isNotNull();
		assertThat(mergedOptions.getCacheOptions().getStrategy()).isEqualTo(AnthropicCacheStrategy.SYSTEM_ONLY);
	}

	@Test
	void cacheToolResultsPlacesBreakpointOnLastToolResultBlock() {
		Message mockResponse = createMockMessage("Done.", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);

		AnthropicCacheOptions cacheOptions = AnthropicCacheOptions.builder()
			.strategy(AnthropicCacheStrategy.CONVERSATION_HISTORY)
			.cacheToolResults(true)
			.build();
		AnthropicChatOptions options = AnthropicChatOptions.builder().cacheOptions(cacheOptions).build();

		this.chatModel.call(new Prompt(toolCallingConversation(), options));

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(this.messageService).create(captor.capture());

		assertThat(lastToolResultBlock(captor.getValue()).cacheControl()).isPresent();
	}

	@Test
	void cacheToolResultsDisabledByDefaultLeavesToolResultsUncached() {
		Message mockResponse = createMockMessage("Done.", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);

		// CONVERSATION_HISTORY alone (without cacheToolResults) must not place a
		// breakpoint on tool result blocks.
		AnthropicCacheOptions cacheOptions = AnthropicCacheOptions.builder()
			.strategy(AnthropicCacheStrategy.CONVERSATION_HISTORY)
			.build();
		AnthropicChatOptions options = AnthropicChatOptions.builder().cacheOptions(cacheOptions).build();

		this.chatModel.call(new Prompt(toolCallingConversation(), options));

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(this.messageService).create(captor.capture());

		assertThat(lastToolResultBlock(captor.getValue()).cacheControl()).isEmpty();
	}

	@Test
	void cacheToolResultsOnlyBreaksTheLastToolResultMessage() {
		Message mockResponse = createMockMessage("Done.", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);

		AnthropicCacheOptions cacheOptions = AnthropicCacheOptions.builder()
			.strategy(AnthropicCacheStrategy.CONVERSATION_HISTORY)
			.cacheToolResults(true)
			.build();
		AnthropicChatOptions options = AnthropicChatOptions.builder().cacheOptions(cacheOptions).build();

		// Two tool-calling rounds: only the final tool result should carry a breakpoint.
		List<org.springframework.ai.chat.messages.Message> messages = List.of(
				new UserMessage("What's the weather in Paris and Berlin?"), assistantToolCall("toolu_1", "Paris"),
				toolResult("toolu_1", "Sunny and 25C in Paris."), assistantToolCall("toolu_2", "Berlin"),
				toolResult("toolu_2", "Cloudy and 12C in Berlin."));

		this.chatModel.call(new Prompt(messages, options));

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(this.messageService).create(captor.capture());

		List<ToolResultBlockParam> toolResults = toolResultBlocks(captor.getValue());
		assertThat(toolResults).hasSize(2);
		assertThat(toolResults.get(0).cacheControl()).isEmpty();
		assertThat(toolResults.get(1).cacheControl()).isPresent();
	}

	@Test
	void multiTurnConversation() {
		Message mockResponse = createMockMessage("Paris is the capital of France.", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);

		UserMessage user1 = new UserMessage("What is the capital of France?");
		AssistantMessage assistant1 = new AssistantMessage("The capital of France is Paris.");
		UserMessage user2 = new UserMessage("What is its population?");

		ChatResponse response = this.chatModel.call(new Prompt(List.of(user1, assistant1, user2)));

		assertThat(response.getResult().getOutput().getText()).isEqualTo("Paris is the capital of France.");

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(this.messageService).create(captor.capture());

		MessageCreateParams request = captor.getValue();
		assertThat(request.messages()).hasSize(3);
	}

	@Test
	void callWithOutputConfig() {
		Message mockResponse = createMockMessage("{ \"name\": \"test\" }", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);

		OutputConfig outputConfig = OutputConfig.builder().effort(OutputConfig.Effort.HIGH).build();

		AnthropicChatOptions options = AnthropicChatOptions.builder().outputConfig(outputConfig).build();

		ChatResponse response = this.chatModel.call(new Prompt("Generate JSON", options));

		assertThat(response).isNotNull();

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(this.messageService).create(captor.capture());

		MessageCreateParams request = captor.getValue();
		assertThat(request.outputConfig()).isPresent();
		assertThat(request.outputConfig().get().effort()).isPresent();
		assertThat(request.outputConfig().get().effort().get()).isEqualTo(OutputConfig.Effort.HIGH);
	}

	@Test
	void callWithOutputSchema() {
		Message mockResponse = createMockMessage("{ \"name\": \"France\" }", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);

		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.outputSchema("{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}")
			.build();

		ChatResponse response = this.chatModel.call(new Prompt("Generate JSON", options));

		assertThat(response).isNotNull();

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(this.messageService).create(captor.capture());

		MessageCreateParams request = captor.getValue();
		assertThat(request.outputConfig()).isPresent();
		assertThat(request.outputConfig().get().format()).isPresent();
	}

	@Test
	void callWithHttpHeaders() {
		Message mockResponse = createMockMessage("Hello", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);

		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.httpHeaders(Map.of("X-Custom-Header", "custom-value", "X-Request-Id", "req-123"))
			.build();

		ChatResponse response = this.chatModel.call(new Prompt("Hello", options));

		assertThat(response).isNotNull();

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(this.messageService).create(captor.capture());

		MessageCreateParams request = captor.getValue();
		assertThat(request._additionalHeaders().values("X-Custom-Header")).contains("custom-value");
		assertThat(request._additionalHeaders().values("X-Request-Id")).contains("req-123");
	}

	@Test
	void callWithSkillContainerWiresAdditionalBodyAndBetaHeaders() {
		Message mockResponse = createMockMessage("Created spreadsheet", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);

		AnthropicChatOptions options = AnthropicChatOptions.builder().skill(AnthropicSkill.XLSX).build();

		ChatResponse response = this.chatModel.call(new Prompt("Create an Excel file", options));

		assertThat(response).isNotNull();

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(this.messageService).create(captor.capture());

		MessageCreateParams request = captor.getValue();
		// Verify beta headers are set for skills
		assertThat(request._additionalHeaders().values("anthropic-beta")).isNotEmpty();
		String betaHeader = String.join(",", request._additionalHeaders().values("anthropic-beta"));
		assertThat(betaHeader).contains("skills-2025-10-02");
		assertThat(betaHeader).contains("code-execution-2025-08-25");
		assertThat(betaHeader).contains("files-api-2025-04-14");
		// Verify container body property is set
		assertThat(request._additionalBodyProperties()).containsKey("container");
	}

	private static List<org.springframework.ai.chat.messages.Message> toolCallingConversation() {
		return List.of(new UserMessage("What's the weather in Paris?"), assistantToolCall("toolu_1", "Paris"),
				toolResult("toolu_1", "Sunny and 25C in Paris."));
	}

	private static AssistantMessage assistantToolCall(String id, String city) {
		return AssistantMessage.builder()
			.content("")
			.toolCalls(
					List.of(new AssistantMessage.ToolCall(id, "function", "getWeather", "{\"city\":\"" + city + "\"}")))
			.build();
	}

	private static ToolResponseMessage toolResult(String id, String data) {
		return ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse(id, "getWeather", data)))
			.build();
	}

	private static List<ToolResultBlockParam> toolResultBlocks(MessageCreateParams request) {
		List<ToolResultBlockParam> blocks = new java.util.ArrayList<>();
		for (MessageParam message : request.messages()) {
			if (message.content().isBlockParams()) {
				for (ContentBlockParam block : message.content().asBlockParams()) {
					if (block.isToolResult()) {
						blocks.add(block.asToolResult());
					}
				}
			}
		}
		return blocks;
	}

	private static ToolResultBlockParam lastToolResultBlock(MessageCreateParams request) {
		List<ToolResultBlockParam> blocks = toolResultBlocks(request);
		assertThat(blocks).isNotEmpty();
		return blocks.get(blocks.size() - 1);
	}

	private Message createMockMessage(String text, StopReason stopReason) {
		TextBlock textBlock = mock(TextBlock.class);
		given(textBlock.text()).willReturn(text);

		ContentBlock contentBlock = mock(ContentBlock.class);
		given(contentBlock.isText()).willReturn(true);
		given(contentBlock.isToolUse()).willReturn(false);
		given(contentBlock.asText()).willReturn(textBlock);

		Usage usage = mock(Usage.class);
		given(usage.inputTokens()).willReturn(10L);
		given(usage.outputTokens()).willReturn(20L);

		Message message = mock(Message.class);
		given(message.id()).willReturn("msg_123");
		given(message.model()).willReturn(Model.CLAUDE_SONNET_4_20250514);
		given(message.content()).willReturn(List.of(contentBlock));
		given(message.stopReason()).willReturn(Optional.of(stopReason));
		given(message.usage()).willReturn(usage);

		return message;
	}

	private Message createMockMessageWithToolUse(String toolId, String toolName, JsonValue input,
			StopReason stopReason) {
		ToolUseBlock toolUseBlock = mock(ToolUseBlock.class);
		given(toolUseBlock.id()).willReturn(toolId);
		given(toolUseBlock.name()).willReturn(toolName);
		given(toolUseBlock._input()).willReturn(input);

		ContentBlock contentBlock = mock(ContentBlock.class);
		given(contentBlock.isText()).willReturn(false);
		given(contentBlock.isToolUse()).willReturn(true);
		given(contentBlock.asToolUse()).willReturn(toolUseBlock);

		Usage usage = mock(Usage.class);
		given(usage.inputTokens()).willReturn(15L);
		given(usage.outputTokens()).willReturn(25L);

		Message message = mock(Message.class);
		given(message.id()).willReturn("msg_456");
		given(message.model()).willReturn(Model.CLAUDE_SONNET_4_20250514);
		given(message.content()).willReturn(List.of(contentBlock));
		given(message.stopReason()).willReturn(Optional.of(stopReason));
		given(message.usage()).willReturn(usage);

		return message;
	}

	@Test
	@SuppressWarnings("unchecked")
	void rateLimitHeadersArePopulatedInMetadata() {
		Message mockResponse = createMockMessage("OK", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);

		Instant resetAt = Instant.now().plus(30, ChronoUnit.SECONDS);
		Headers rateLimitHeaders = Headers.builder()
			.put("anthropic-ratelimit-requests-limit", "100")
			.put("anthropic-ratelimit-requests-remaining", "99")
			.put("anthropic-ratelimit-requests-reset", resetAt.toString())
			.put("anthropic-ratelimit-tokens-limit", "50000")
			.put("anthropic-ratelimit-tokens-remaining", "49000")
			.put("anthropic-ratelimit-tokens-reset", resetAt.toString())
			.build();

		given(this.messageServiceWithRawResponse.create(any(MessageCreateParams.class))).willAnswer(invocation -> {
			MessageCreateParams params = invocation.getArgument(0);
			Message message = this.messageService.create(params);
			HttpResponseFor<Message> rawResponse = mock(HttpResponseFor.class);
			given(rawResponse.parse()).willReturn(message);
			given(rawResponse.headers()).willReturn(rateLimitHeaders);
			return rawResponse;
		});

		ChatResponse response = this.chatModel.call(new Prompt("test"));

		ChatResponseMetadata metadata = response.getMetadata();
		RateLimit rateLimit = metadata.getRateLimit();
		assertThat(rateLimit).isNotNull();
		assertThat(rateLimit.getRequestsLimit()).isEqualTo(100L);
		assertThat(rateLimit.getRequestsRemaining()).isEqualTo(99L);
		assertThat(rateLimit.getRequestsReset()).isNotNull().isPositive();
		assertThat(rateLimit.getTokensLimit()).isEqualTo(50000L);
		assertThat(rateLimit.getTokensRemaining()).isEqualTo(49000L);
		assertThat(rateLimit.getTokensReset()).isNotNull().isPositive();
	}

	@Test
	@SuppressWarnings("unchecked")
	void streamingClosesStreamResponse() {
		StreamResponse<RawMessageStreamEvent> streamResponse = mock(StreamResponse.class);
		given(streamResponse.stream()).willReturn(Stream.empty());

		HttpResponseFor<StreamResponse<RawMessageStreamEvent>> rawResponse = mock(HttpResponseFor.class);
		given(rawResponse.parse()).willReturn(streamResponse);
		given(rawResponse.headers()).willReturn(Headers.builder().build());

		given(this.anthropicClientAsync.messages()).willReturn(this.messageServiceAsync);
		given(this.messageServiceAsync.withRawResponse()).willReturn(this.messageServiceAsyncWithRawResponse);
		given(this.messageServiceAsyncWithRawResponse.createStreaming(any(MessageCreateParams.class)))
			.willReturn(CompletableFuture.completedFuture(rawResponse));

		this.chatModel.stream(new Prompt("test")).collectList().block();

		// The blocking StreamResponse must be released once the stream terminates.
		// close() runs in the doFinally callback on the boundedElastic worker, which
		// can lag block() returning, so allow a short window.
		verify(streamResponse, timeout(1000)).close();
	}

	@Test
	@SuppressWarnings("unchecked")
	void streamingAttachesRateLimitHeadersToResponse() {
		Instant resetAt = Instant.now().plus(30, ChronoUnit.SECONDS);
		Headers rateLimitHeaders = Headers.builder()
			.put("anthropic-ratelimit-requests-limit", "100")
			.put("anthropic-ratelimit-requests-remaining", "99")
			.put("anthropic-ratelimit-requests-reset", resetAt.toString())
			.put("anthropic-ratelimit-tokens-limit", "50000")
			.put("anthropic-ratelimit-tokens-remaining", "49000")
			.put("anthropic-ratelimit-tokens-reset", resetAt.toString())
			.build();

		// A message_delta event carries the final usage and triggers the metadata
		// build that attaches the captured rate limit. The SDK builders require every
		// field to be set explicitly, hence the Optional.empty() plumbing.
		RawMessageStreamEvent messageDelta = RawMessageStreamEvent.ofMessageDelta(RawMessageDeltaEvent.builder()
			.delta(RawMessageDeltaEvent.Delta.builder()
				.container(Optional.empty())
				.stopDetails(Optional.empty())
				.stopReason(StopReason.END_TURN)
				.stopSequence(Optional.empty())
				.build())
			.usage(MessageDeltaUsage.builder()
				.cacheCreationInputTokens(Optional.empty())
				.cacheReadInputTokens(Optional.empty())
				.inputTokens(Optional.empty())
				.outputTokens(5L)
				.outputTokensDetails(Optional.empty())
				.serverToolUse(Optional.empty())
				.build())
			.build());

		StreamResponse<RawMessageStreamEvent> streamResponse = mock(StreamResponse.class);
		given(streamResponse.stream()).willReturn(Stream.of(messageDelta));

		HttpResponseFor<StreamResponse<RawMessageStreamEvent>> rawResponse = mock(HttpResponseFor.class);
		given(rawResponse.parse()).willReturn(streamResponse);
		given(rawResponse.headers()).willReturn(rateLimitHeaders);

		given(this.anthropicClientAsync.messages()).willReturn(this.messageServiceAsync);
		given(this.messageServiceAsync.withRawResponse()).willReturn(this.messageServiceAsyncWithRawResponse);
		given(this.messageServiceAsyncWithRawResponse.createStreaming(any(MessageCreateParams.class)))
			.willReturn(CompletableFuture.completedFuture(rawResponse));

		List<ChatResponse> responses = this.chatModel.stream(new Prompt("test")).collectList().block();

		assertThat(responses).isNotNull();
		ChatResponse responseWithRateLimit = responses.stream()
			.filter(response -> response.getMetadata().getRateLimit() instanceof AnthropicRateLimit)
			.findFirst()
			.orElse(null);

		assertThat(responseWithRateLimit).as("The message_delta chunk should carry rate-limit metadata").isNotNull();
		RateLimit rateLimit = responseWithRateLimit.getMetadata().getRateLimit();
		assertThat(rateLimit.getRequestsLimit()).isEqualTo(100L);
		assertThat(rateLimit.getRequestsRemaining()).isEqualTo(99L);
		assertThat(rateLimit.getTokensLimit()).isEqualTo(50000L);
		assertThat(rateLimit.getTokensRemaining()).isEqualTo(49000L);
	}

}
