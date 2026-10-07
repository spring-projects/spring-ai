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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.AnthropicClientAsync;
import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.core.RequestOptions;
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
import com.anthropic.models.messages.RawContentBlockDeltaEvent;
import com.anthropic.models.messages.RawContentBlockStartEvent;
import com.anthropic.models.messages.RawContentBlockStopEvent;
import com.anthropic.models.messages.RawMessageDeltaEvent;
import com.anthropic.models.messages.RawMessageStartEvent;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.RedactedThinkingBlock;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.ThinkingBlock;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUnion;
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
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.part.MediaPart;
import org.springframework.ai.chat.messages.part.MessagePart;
import org.springframework.ai.chat.messages.part.OpaquePayload;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.messages.part.StreamingParts;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.ToolCallPart;
import org.springframework.ai.chat.messages.part.UnknownPart;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link AnthropicChatModel}. Tests request building and response parsing
 * with mocked SDK client.
 *
 * @author Soby Chacko
 * @author Sebastien Deleuze
 * @author Jewoo Shin
 * @author Seeun Kim
 */
@ExtendWith({ MockitoExtension.class, OutputCaptureExtension.class })
@MockitoSettings(strictness = Strictness.LENIENT)
class AnthropicChatModelTests {

	private static final String SERVER_TOOL_USE_JSON = """
			{"type":"server_tool_use","id":"srvtoolu_01","name":"web_search","input":%s}""";

	private static final String WEB_SEARCH_RESULT_JSON = """
			{"type":"web_search_tool_result","tool_use_id":"srvtoolu_01","content":[{"type":"web_search_result",\
			"title":"Spring AI","url":"https://spring.io/projects/spring-ai","encrypted_content":"enc",\
			"page_age":null}]}""";

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
		given(this.messageServiceWithRawResponse.create(any(MessageCreateParams.class), any(RequestOptions.class)))
			.willAnswer(invocation -> {
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
				.model(Model.CLAUDE_SONNET_4_5)
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
		assertThat(request.model().asString()).isEqualTo("claude-sonnet-4-5");
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
	void thinkingBlockIsReplayedBeforeToolUseBlock() {
		Message toolUseResponse = createMockMessageWithThinkingAndToolUse("thinking text", "thinking-signature",
				"toolu_123", "getCurrentWeather", JsonValue.from(java.util.Map.of("location", "Paris")));
		Message finalResponse = createMockMessage("Done.", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(toolUseResponse, finalResponse);

		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.toolCallbacks(List.of(new TestToolCallback("getCurrentWeather")))
			.build();
		Prompt prompt = new Prompt("What's the weather?", options);

		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResults()).hasSize(1);
		AssistantMessage output = response.getResult().getOutput();
		assertThat(output.getParts()).hasSize(2);
		assertThat(output.getParts().get(0)).isInstanceOf(ReasoningPart.class);
		ReasoningPart reasoning = (ReasoningPart) output.getParts().get(0);
		assertThat(reasoning.text()).isEqualTo("thinking text");
		assertThat(reasoning.payload()).isEqualTo(new OpaquePayload("anthropic", "signature", "thinking-signature"));
		assertThat(output.getParts().get(1)).isInstanceOf(ToolCallPart.class);
		assertThat(output.getToolCalls()).hasSize(1);
		assertThat(output.getText()).isEmpty();

		ToolExecutionResult toolExecutionResult = ToolCallingManager.builder()
			.build()
			.executeToolCalls(prompt, response);
		this.chatModel.call(new Prompt(toolExecutionResult.conversationHistory(), options));

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(this.messageService, times(2)).create(captor.capture());

		List<ContentBlockParam> replayedAssistantBlocks = assistantBlockParams(captor.getAllValues().get(1));
		assertThat(replayedAssistantBlocks).hasSize(2);
		assertThat(replayedAssistantBlocks.get(0).isThinking()).isTrue();
		assertThat(replayedAssistantBlocks.get(0).asThinking().thinking()).isEqualTo("thinking text");
		assertThat(replayedAssistantBlocks.get(0).asThinking().signature()).isEqualTo("thinking-signature");
		assertThat(replayedAssistantBlocks.get(1).isToolUse()).isTrue();
		assertThat(toolResultBlocks(captor.getAllValues().get(1))).hasSize(1);
	}

	@Test
	void redactedThinkingBlockIsReplayedBeforeToolUseBlock() {
		Message toolUseResponse = createMockMessageWithRedactedThinkingAndToolUse("redacted-data", "toolu_123",
				"getCurrentWeather", JsonValue.from(java.util.Map.of("location", "Paris")));
		Message finalResponse = createMockMessage("Done.", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(toolUseResponse, finalResponse);

		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.toolCallbacks(List.of(new TestToolCallback("getCurrentWeather")))
			.build();
		Prompt prompt = new Prompt("What's the weather?", options);

		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResults()).hasSize(1);
		AssistantMessage output = response.getResult().getOutput();
		assertThat(output.getParts()).hasSize(2);
		ReasoningPart redacted = (ReasoningPart) output.getParts().get(0);
		assertThat(redacted.text()).isNull();
		assertThat(redacted.payload()).isEqualTo(new OpaquePayload("anthropic", "redacted_thinking", "redacted-data"));
		assertThat(output.getToolCalls()).hasSize(1);

		ToolExecutionResult toolExecutionResult = ToolCallingManager.builder()
			.build()
			.executeToolCalls(prompt, response);
		this.chatModel.call(new Prompt(toolExecutionResult.conversationHistory(), options));

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(this.messageService, times(2)).create(captor.capture());

		List<ContentBlockParam> replayedAssistantBlocks = assistantBlockParams(captor.getAllValues().get(1));
		assertThat(replayedAssistantBlocks).hasSize(2);
		assertThat(replayedAssistantBlocks.get(0).isRedactedThinking()).isTrue();
		assertThat(replayedAssistantBlocks.get(0).asRedactedThinking().data()).isEqualTo("redacted-data");
		assertThat(replayedAssistantBlocks.get(1).isToolUse()).isTrue();
	}

	@Test
	void thinkingAndTextResponseIsOneGenerationWithOrderedParts() {
		Message mockResponse = createMockMessageWithThinkingAndText("thinking text", "thinking-signature",
				"Final answer.");
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);

		ChatResponse response = this.chatModel.call(new Prompt("Explain it"));

		assertThat(response.getResults()).hasSize(1);
		AssistantMessage output = response.getResult().getOutput();
		assertThat(output.getText()).isEqualTo("Final answer.");
		assertThat(output.getParts()).hasSize(2);
		assertThat(output.getParts().get(0)).isInstanceOf(ReasoningPart.class);
		assertThat(output.getParts().get(1)).isEqualTo(TextPart.of("Final answer."));
		assertThat(output.getReasoning()).hasSize(1);
		assertThat(output.getReasoning().get(0).text()).isEqualTo("thinking text");
		assertThat(output.getMetadata()).doesNotContainKey("signature").doesNotContainKey("anthropicThinkingContents");
	}

	@Test
	void unsupportedBlockBecomesUnknownPart() {
		ContentBlock serverToolUse = mock(ContentBlock.class);
		given(serverToolUse.isServerToolUse()).willReturn(true);
		given(serverToolUse._json())
			.willReturn(Optional.of(JsonValue.from(Map.of("type", "server_tool_use", "id", "srvtoolu_01"))));
		Message mockResponse = createMockMessage(List.of(serverToolUse, textContentBlock("Answer.")),
				StopReason.END_TURN, 10L, 20L);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);

		ChatResponse response = this.chatModel.call(new Prompt("Search it"));

		AssistantMessage output = response.getResult().getOutput();
		assertThat(output.getParts()).hasSize(2);
		assertThat(output.getParts().get(0)).isInstanceOf(UnknownPart.class);
		UnknownPart unknown = (UnknownPart) output.getParts().get(0);
		assertThat(unknown.provider()).isEqualTo("anthropic");
		assertThat(unknown.kind()).isEqualTo("server_tool_use");
		assertThat(unknown.rawJson()).contains("srvtoolu_01");
		assertThat(output.getText()).isEqualTo("Answer.");
	}

	@Test
	void assistantPartsAreReplayedInPartOrder() {
		Message mockResponse = createMockMessage("Done.", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);

		AssistantMessage history = AssistantMessage.builder()
			.part(new ReasoningPart("think", null, new OpaquePayload("anthropic", "signature", "sig"), Map.of()))
			.part(ToolCallPart.of(new AssistantMessage.ToolCall("toolu_1", "function", "getWeather", "{}")))
			.part(TextPart.of("Checking."))
			.build();
		this.chatModel.call(new Prompt(List.of(new UserMessage("Weather?"), history, toolResult("toolu_1", "Sunny."),
				new UserMessage("Thanks"))));

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(this.messageService).create(captor.capture());
		List<ContentBlockParam> blocks = assistantBlockParams(captor.getValue());
		assertThat(blocks).hasSize(3);
		assertThat(blocks.get(0).isThinking()).isTrue();
		assertThat(blocks.get(1).isToolUse()).isTrue();
		assertThat(blocks.get(2).isText()).isTrue();
		assertThat(blocks.get(2).asText().text()).isEqualTo("Checking.");
	}

	@Test
	void foreignReasoningPartIsNotReplayed() {
		Message mockResponse = createMockMessage("Done.", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);

		AssistantMessage history = AssistantMessage.builder()
			.part(new ReasoningPart("openai thoughts", null, new OpaquePayload("openai", "encrypted_content", "enc"),
					Map.of()))
			.part(TextPart.of("Earlier answer."))
			.build();
		this.chatModel.call(new Prompt(List.of(new UserMessage("Hi"), history, new UserMessage("Again"))));

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(this.messageService).create(captor.capture());
		MessageParam assistant = captor.getValue().messages().get(1);
		assertThat(assistant.role()).isEqualTo(MessageParam.Role.ASSISTANT);
		assertThat(assistant.content().isBlockParams()).isTrue();
		List<ContentBlockParam> blocks = assistant.content().asBlockParams();
		assertThat(blocks).hasSize(1);
		assertThat(blocks.get(0).isText()).isTrue();
		assertThat(blocks.get(0).asText().text()).isEqualTo("Earlier answer.");
	}

	@Test
	void thinkingEnabledToolTurnWithoutAnthropicReasoningIsStillSent(CapturedOutput output) {
		Message mockResponse = createMockMessage("Done.", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.maxTokens(4096)
			.thinkingEnabled(2048)
			.toolCallbacks(List.of(new TestToolCallback("getWeather")))
			.build();
		Prompt prompt = new Prompt(toolCallingConversation(), options);

		// The missing thinking block is logged as a warning; the API decides.
		this.chatModel.call(prompt);

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(this.messageService).create(captor.capture());
		List<ContentBlockParam> blocks = assistantBlockParams(captor.getValue());
		assertThat(blocks).hasSize(1);
		assertThat(blocks.get(0).isToolUse()).isTrue();
		assertThat(output).contains("has no Anthropic thinking block to replay");
	}

	@Test
	void thinkingEnabledToolTurnWithoutAnthropicReasoningIsNotWarnedOnceTheToolLoopIsClosed(CapturedOutput output) {
		Message mockResponse = createMockMessage("Done.", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.maxTokens(4096)
			.thinkingEnabled(2048)
			.toolCallbacks(List.of(new TestToolCallback("getWeather")))
			.build();
		List<org.springframework.ai.chat.messages.Message> conversation = new ArrayList<>(toolCallingConversation());
		conversation.add(new UserMessage("And tomorrow?"));

		this.chatModel.call(new Prompt(conversation, options));

		assertThat(output).doesNotContain("has no Anthropic thinking block to replay");
	}

	@Test
	void streamingThinkingBlockIsReplayedBeforeToolUseBlock() {
		givenStream(List.of(messageStartEvent(), thinkingStartEvent(), thinkingDeltaEvent("thinking "),
				thinkingDeltaEvent("text"), signatureDeltaEvent("thinking-signature"), contentBlockStopEvent(0),
				toolUseStartEvent("toolu_123", "getCurrentWeather"), inputJsonDeltaEvent("{\"location\":\"Paris\"}"),
				contentBlockStopEvent(1), messageDeltaEvent(StopReason.TOOL_USE)));
		Message finalResponse = createMockMessage("Done.", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(finalResponse);

		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.toolCallbacks(List.of(new TestToolCallback("getCurrentWeather")))
			.build();
		Prompt prompt = new Prompt("What's the weather?", options);
		AtomicReference<ChatResponse> aggregatedResponse = new AtomicReference<>();

		List<ChatResponse> chunks = new MessageAggregator()
			.aggregate(this.chatModel.stream(prompt), aggregatedResponse::set)
			.collectList()
			.block();

		// Chunk contract: indexed partial parts, response id on every chunk, legacy keys
		// kept
		assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.getMetadata().getId()).isEqualTo("msg_stream"));
		ChatResponse firstThinkingChunk = chunks.get(0);
		MessagePart thinkingDelta = firstThinkingChunk.getResult().getOutput().getParts().get(0);
		assertThat(thinkingDelta).isInstanceOf(ReasoningPart.class);
		assertThat(((ReasoningPart) thinkingDelta).text()).isEqualTo("thinking ");
		assertThat(StreamingParts.partIndex(thinkingDelta)).isZero();
		assertThat(StreamingParts.isPartial(thinkingDelta)).isTrue();
		assertThat(firstThinkingChunk.getResult().getOutput().getText()).isEmpty();
		assertThat(firstThinkingChunk.getResult().getOutput().getMetadata()).containsEntry("thinking", Boolean.TRUE);
		ChatResponse signatureChunk = chunks.get(2);
		MessagePart signatureDelta = signatureChunk.getResult().getOutput().getParts().get(0);
		assertThat(signatureDelta.payload())
			.isEqualTo(new OpaquePayload("anthropic", "signature", "thinking-signature"));
		assertThat(StreamingParts.isPartial(signatureDelta)).isTrue();
		assertThat(signatureChunk.getResult().getOutput().getMetadata()).containsEntry("signature",
				"thinking-signature");
		ChatResponse finalChunk = chunks.get(chunks.size() - 1);
		assertThat(finalChunk.getResult().getMetadata().getFinishReason()).isEqualTo("tool_use");
		assertThat(finalChunk.getResult().getOutput().getText()).isEmpty();
		// the complete tool call plus the legacy empty text that keeps getText() non-null
		assertThat(finalChunk.getResult().getOutput().getParts()).hasSize(2);
		assertThat(finalChunk.getResult().getOutput().getParts().get(1)).isEqualTo(TextPart.of(""));
		MessagePart toolCallPart = finalChunk.getResult().getOutput().getParts().get(0);
		assertThat(toolCallPart).isInstanceOf(ToolCallPart.class);
		assertThat(StreamingParts.partIndex(toolCallPart)).isEqualTo(1);
		assertThat(StreamingParts.isPartial(toolCallPart)).isFalse();

		// Aggregated message: reasoning reconstructed from deltas, in block order,
		// attributes stripped
		AssistantMessage aggregated = aggregatedResponse.get().getResult().getOutput();
		assertThat(aggregated.getParts()).containsExactly(
				new ReasoningPart("thinking text", null,
						new OpaquePayload("anthropic", "signature", "thinking-signature"), Map.of()),
				ToolCallPart.of(new AssistantMessage.ToolCall("toolu_123", "function", "getCurrentWeather",
						"{\"location\":\"Paris\"}")));

		ToolExecutionResult toolExecutionResult = ToolCallingManager.builder()
			.build()
			.executeToolCalls(prompt, aggregatedResponse.get());
		this.chatModel.call(new Prompt(toolExecutionResult.conversationHistory(), options));

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(this.messageService).create(captor.capture());

		List<ContentBlockParam> replayedAssistantBlocks = assistantBlockParams(captor.getValue());
		assertThat(replayedAssistantBlocks).hasSize(2);
		assertThat(replayedAssistantBlocks.get(0).isThinking()).isTrue();
		assertThat(replayedAssistantBlocks.get(0).asThinking().thinking()).isEqualTo("thinking text");
		assertThat(replayedAssistantBlocks.get(0).asThinking().signature()).isEqualTo("thinking-signature");
		assertThat(replayedAssistantBlocks.get(1).isToolUse()).isTrue();
	}

	@Test
	void streamingTextToolUseTextKeepsBlockOrder() {
		givenStream(List.of(messageStartEvent(), textStartEvent(0), textDeltaEvent("Let me ", 0),
				textDeltaEvent("check.", 0), contentBlockStopEvent(0),
				toolUseStartEvent("toolu_123", "getCurrentWeather"), inputJsonDeltaEvent("{\"location\":\"Paris\"}"),
				contentBlockStopEvent(1), textStartEvent(2), textDeltaEvent(" Done.", 2), contentBlockStopEvent(2),
				messageDeltaEvent(StopReason.TOOL_USE)));

		AtomicReference<ChatResponse> aggregatedResponse = new AtomicReference<>();
		List<ChatResponse> chunks = new MessageAggregator()
			.aggregate(this.chatModel.stream(new Prompt("What's the weather?")), aggregatedResponse::set)
			.collectList()
			.block();

		MessagePart firstText = chunks.get(0).getResult().getOutput().getParts().get(0);
		assertThat(firstText).isEqualTo(StreamingParts.partial(TextPart.of("Let me "), 0));
		assertThat(chunks.get(0).getResult().getOutput().getText()).isEqualTo("Let me ");

		AssistantMessage aggregated = aggregatedResponse.get().getResult().getOutput();
		assertThat(aggregated.getText()).isEqualTo("Let me check. Done.");
		assertThat(aggregated.getParts()).containsExactly(TextPart.of("Let me check."),
				ToolCallPart.of(new AssistantMessage.ToolCall("toolu_123", "function", "getCurrentWeather",
						"{\"location\":\"Paris\"}")),
				TextPart.of(" Done."));
	}

	@Test
	void streamingToolUseCarriesOnlyTheCompleteCall() {
		givenStream(List.of(messageStartEvent(), textStartEvent(0), textDeltaEvent("Checking.", 0),
				contentBlockStopEvent(0), toolUseStartEvent("toolu_123", "getCurrentWeather"),
				inputJsonDeltaEvent("{\"location\":"), inputJsonDeltaEvent("\"Paris\"}"), contentBlockStopEvent(1),
				messageDeltaEvent(StopReason.TOOL_USE)));

		AtomicReference<ChatResponse> aggregatedResponse = new AtomicReference<>();
		List<ChatResponse> chunks = new MessageAggregator()
			.aggregate(this.chatModel.stream(new Prompt("What's the weather?")), aggregatedResponse::set)
			.collectList()
			.block();

		// The text delta and the final chunk with the complete call, no argument-delta
		// chunks
		assertThat(chunks).hasSize(2);
		assertThat(chunks.get(0).getResult().getOutput().getText()).isEqualTo("Checking.");
		AssistantMessage.ToolCall complete = new AssistantMessage.ToolCall("toolu_123", "function", "getCurrentWeather",
				"{\"location\":\"Paris\"}");
		assertThat(chunks.get(1).getResult().getOutput().getToolCalls()).containsExactly(complete);
		assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.getResult().getOutput().getParts())
			.noneMatch(part -> part instanceof ToolCallPart && StreamingParts.isPartial(part)));
		assertThat(aggregatedResponse.get().getResult().getOutput().getParts())
			.containsExactly(TextPart.of("Checking."), ToolCallPart.of(complete));
	}

	@Test
	void streamingThinkingWithoutSignatureIsKeptAsText() {
		givenStream(List.of(messageStartEvent(), thinkingStartEvent(), thinkingDeltaEvent("unsigned"),
				contentBlockStopEvent(0), textStartEvent(1), textDeltaEvent("Answer.", 1), contentBlockStopEvent(1),
				messageDeltaEvent(StopReason.END_TURN)));

		AtomicReference<ChatResponse> aggregatedResponse = new AtomicReference<>();
		new MessageAggregator().aggregate(this.chatModel.stream(new Prompt("Explain")), aggregatedResponse::set)
			.blockLast();

		AssistantMessage aggregated = aggregatedResponse.get().getResult().getOutput();
		assertThat(aggregated.getParts()).containsExactly(ReasoningPart.of("unsigned"), TextPart.of("Answer."));
		assertThat(aggregated.getReasoning().get(0).payload()).isNull();
	}

	@Test
	void streamingRedactedThinkingIsCompletePart() {
		givenStream(List.of(messageStartEvent(), redactedThinkingStartEvent("redacted-data", 0),
				contentBlockStopEvent(0), textStartEvent(1), textDeltaEvent("Answer.", 1), contentBlockStopEvent(1),
				messageDeltaEvent(StopReason.END_TURN)));

		AtomicReference<ChatResponse> aggregatedResponse = new AtomicReference<>();
		List<ChatResponse> chunks = new MessageAggregator()
			.aggregate(this.chatModel.stream(new Prompt("Explain")), aggregatedResponse::set)
			.collectList()
			.block();

		MessagePart redactedChunkPart = chunks.get(0).getResult().getOutput().getParts().get(0);
		assertThat(StreamingParts.isPartial(redactedChunkPart)).isFalse();
		assertThat(StreamingParts.partIndex(redactedChunkPart)).isZero();
		assertThat(chunks.get(0).getResult().getOutput().getMetadata()).containsEntry("data", "redacted-data");

		AssistantMessage aggregated = aggregatedResponse.get().getResult().getOutput();
		assertThat(aggregated.getParts()).containsExactly(new ReasoningPart(null, null,
				new OpaquePayload("anthropic", "redacted_thinking", "redacted-data"), Map.of()),
				TextPart.of("Answer."));
	}

	@Test
	@SuppressWarnings("removal")
	void streamingThinkingDeltasKeepDeprecatedThinkingTextMetadata() {
		givenStream(List.of(messageStartEvent(), thinkingStartEvent(), thinkingDeltaEvent("thinking "),
				thinkingDeltaEvent("text"), signatureDeltaEvent("thinking-signature"), contentBlockStopEvent(0),
				messageDeltaEvent(StopReason.END_TURN)));

		List<ChatResponse> responses = this.chatModel.stream(new Prompt("Think about it.")).collectList().block();

		assertThat(responses).isNotNull();
		List<AssistantMessage> thinkingDeltas = responses.stream()
			.map(response -> response.getResult().getOutput())
			.filter(message -> message.getMetadata().containsKey(AnthropicChatModel.THINKING_METADATA_KEY))
			.toList();
		assertThat(thinkingDeltas)
			.extracting(message -> message.getMetadata().get(AnthropicChatModel.THINKING_TEXT_METADATA_KEY))
			.containsExactly("thinking ", "text");
		// The thinking text is a reasoning part, never part of the answer text
		assertThat(thinkingDeltas).extracting(message -> message.getReasoning().get(0).text())
			.containsExactly("thinking ", "text");
		assertThat(thinkingDeltas).allSatisfy(message -> assertThat(message.getText()).isEmpty());
	}

	@Test
	void streamingThinkingWithOmittedDisplayIsReplayedWithEmptyThinking() {
		givenStream(List.of(messageStartEvent(), thinkingStartEvent(), signatureDeltaEvent("thinking-signature"),
				contentBlockStopEvent(0), textStartEvent(1), textDeltaEvent("Answer.", 1), contentBlockStopEvent(1),
				messageDeltaEvent(StopReason.END_TURN)));
		Message finalResponse = createMockMessage("Done.", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(finalResponse);

		AtomicReference<ChatResponse> aggregatedResponse = new AtomicReference<>();
		new MessageAggregator().aggregate(this.chatModel.stream(new Prompt("Explain")), aggregatedResponse::set)
			.blockLast();

		AssistantMessage aggregated = aggregatedResponse.get().getResult().getOutput();
		assertThat(aggregated.getParts()).containsExactly(new ReasoningPart("", null,
				new OpaquePayload("anthropic", "signature", "thinking-signature"), Map.of()), TextPart.of("Answer."));

		this.chatModel.call(new Prompt(List.of(new UserMessage("Explain"), aggregated, new UserMessage("More"))));

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(this.messageService).create(captor.capture());
		List<ContentBlockParam> blocks = assistantBlockParams(captor.getValue());
		assertThat(blocks).hasSize(2);
		assertThat(blocks.get(0).asThinking().thinking()).isEmpty();
		assertThat(blocks.get(0).asThinking().signature()).isEqualTo("thinking-signature");
		assertThat(blocks.get(1).asText().text()).isEqualTo("Answer.");
	}

	@Test
	void reasoningPartIsReplayedByPayloadKind() {
		Message mockResponse = createMockMessage("Done.", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);

		// A redacted part and a thinking part without text: the payload kind alone
		// decides the block type
		AssistantMessage history = AssistantMessage.builder()
			.part(new ReasoningPart(null, null, new OpaquePayload("anthropic", "redacted_thinking", "redacted-data"),
					Map.of()))
			.part(new ReasoningPart(null, null, new OpaquePayload("anthropic", "signature", "sig"), Map.of()))
			.part(TextPart.of("Earlier answer."))
			.build();
		this.chatModel.call(new Prompt(List.of(new UserMessage("Hi"), history, new UserMessage("Again"))));

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(this.messageService).create(captor.capture());
		List<ContentBlockParam> blocks = assistantBlockParams(captor.getValue());
		assertThat(blocks).hasSize(3);
		assertThat(blocks.get(0).asRedactedThinking().data()).isEqualTo("redacted-data");
		assertThat(blocks.get(1).asThinking().thinking()).isEmpty();
		assertThat(blocks.get(1).asThinking().signature()).isEqualTo("sig");
		assertThat(blocks.get(2).asText().text()).isEqualTo("Earlier answer.");
	}

	@Test
	void serverToolBlocksBecomeUnknownPartsAndAreReplayed() throws Exception {
		ContentBlock serverToolUse = ObjectMappers.jsonMapper()
			.readValue(SERVER_TOOL_USE_JSON.formatted("{\"query\":\"spring ai\"}"), ContentBlock.class);
		ContentBlock webSearchResult = ObjectMappers.jsonMapper().readValue(WEB_SEARCH_RESULT_JSON, ContentBlock.class);
		Message mockResponse = createMockMessage(List.of(serverToolUse, webSearchResult, textContentBlock("Answer.")),
				StopReason.END_TURN, 10L, 20L);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);

		ChatResponse response = this.chatModel.call(new Prompt("Search it"));

		AssistantMessage output = response.getResult().getOutput();
		assertThat(output.getParts()).hasSize(3);
		UnknownPart serverToolUsePart = (UnknownPart) output.getParts().get(0);
		assertThat(serverToolUsePart.provider()).isEqualTo("anthropic");
		assertThat(serverToolUsePart.kind()).isEqualTo("server_tool_use");
		assertThat(serverToolUsePart.rawJson()).contains("srvtoolu_01").contains("spring ai");
		UnknownPart webSearchResultPart = (UnknownPart) output.getParts().get(1);
		assertThat(webSearchResultPart.kind()).isEqualTo("web_search_tool_result");
		assertThat(webSearchResultPart.rawJson()).contains("https://spring.io/projects/spring-ai");
		assertThat(output.getParts().get(2)).isEqualTo(TextPart.of("Answer."));
		assertThat(output.getToolCalls()).isEmpty();
		assertThat(response.getMetadata().<List<AnthropicWebSearchResult>>get("web-search-results")).hasSize(1);

		this.chatModel.call(new Prompt(List.of(new UserMessage("Search it"), output, new UserMessage("Thanks"))));

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(this.messageService, times(2)).create(captor.capture());
		List<ContentBlockParam> blocks = assistantBlockParams(captor.getAllValues().get(1));
		assertThat(blocks).hasSize(3);
		assertThat(blocks.get(0).asServerToolUse().id()).isEqualTo("srvtoolu_01");
		assertThat(blocks.get(0).asServerToolUse().input()._additionalProperties()).containsEntry("query",
				JsonValue.from("spring ai"));
		assertThat(blocks.get(1).asWebSearchToolResult().toolUseId()).isEqualTo("srvtoolu_01");
		assertThat(ObjectMappers.jsonMapper().writeValueAsString(blocks.get(1)))
			.contains("\"encrypted_content\":\"enc\"");
		assertThat(blocks.get(2).asText().text()).isEqualTo("Answer.");
	}

	@Test
	void unknownPartOfABlockTypeTheSdkDoesNotKnowIsReplayedAsIs() throws Exception {
		Message mockResponse = createMockMessage("Done.", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);
		AssistantMessage assistant = AssistantMessage.builder()
			.part(new UnknownPart("anthropic", "future_block", "{\"type\":\"future_block\",\"foo\":\"bar\"}", null,
					Map.of()))
			.part(TextPart.of("Answer."))
			.build();

		this.chatModel.call(new Prompt(List.of(new UserMessage("Hi"), assistant, new UserMessage("Again"))));

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(this.messageService).create(captor.capture());
		List<ContentBlockParam> blocks = assistantBlockParams(captor.getValue());
		assertThat(blocks).hasSize(2);
		assertThat(ObjectMappers.jsonMapper().writeValueAsString(blocks.get(0)))
			.isEqualTo("{\"type\":\"future_block\",\"foo\":\"bar\"}");
		assertThat(blocks.get(1).asText().text()).isEqualTo("Answer.");
	}

	@Test
	void unknownPartFromAnotherProviderOrWithInvalidJsonIsNotReplayed(CapturedOutput output) {
		Message mockResponse = createMockMessage("Done.", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);
		AssistantMessage assistant = AssistantMessage.builder()
			.part(new UnknownPart("openai", "web_search_call", "{\"type\":\"web_search_call\"}", null, Map.of()))
			.part(new UnknownPart("anthropic", "server_tool_use", "{", null, Map.of()))
			.part(TextPart.of("Answer."))
			.build();

		this.chatModel.call(new Prompt(List.of(new UserMessage("Hi"), assistant, new UserMessage("Again"))));

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(this.messageService).create(captor.capture());
		List<ContentBlockParam> blocks = assistantBlockParams(captor.getValue());
		assertThat(blocks).hasSize(1);
		assertThat(blocks.get(0).asText().text()).isEqualTo("Answer.");
		assertThat(output).contains("Could not replay the Anthropic content block of kind server_tool_use");
	}

	@Test
	void streamingServerToolBlocksBecomeUnknownPartsWithTheStreamedInput() throws Exception {
		RawMessageStreamEvent serverToolUseStart = ObjectMappers.jsonMapper()
			.readValue("{\"type\":\"content_block_start\",\"index\":0,\"content_block\":"
					+ SERVER_TOOL_USE_JSON.formatted("{}") + "}", RawMessageStreamEvent.class);
		RawMessageStreamEvent webSearchResultStart = ObjectMappers.jsonMapper()
			.readValue(
					"{\"type\":\"content_block_start\",\"index\":1,\"content_block\":" + WEB_SEARCH_RESULT_JSON + "}",
					RawMessageStreamEvent.class);
		givenStream(List.of(messageStartEvent(), serverToolUseStart, inputJsonDeltaEvent("{\"query\":", 0),
				inputJsonDeltaEvent("\"spring ai\"}", 0), contentBlockStopEvent(0), webSearchResultStart,
				contentBlockStopEvent(1), textStartEvent(2), textDeltaEvent("Answer.", 2), contentBlockStopEvent(2),
				messageDeltaEvent(StopReason.END_TURN)));

		AtomicReference<ChatResponse> aggregatedResponse = new AtomicReference<>();
		List<ChatResponse> chunks = new MessageAggregator()
			.aggregate(this.chatModel.stream(new Prompt("Search it")), aggregatedResponse::set)
			.collectList()
			.block();

		MessagePart serverToolUseChunkPart = chunks.get(0).getResult().getOutput().getParts().get(0);
		assertThat(StreamingParts.isPartial(serverToolUseChunkPart)).isFalse();
		assertThat(StreamingParts.partIndex(serverToolUseChunkPart)).isZero();
		assertThat(chunks).noneSatisfy(chunk -> assertThat(chunk.hasToolCalls()).isTrue());

		AssistantMessage aggregated = aggregatedResponse.get().getResult().getOutput();
		assertThat(aggregated.getParts()).hasSize(3);
		UnknownPart serverToolUsePart = (UnknownPart) aggregated.getParts().get(0);
		assertThat(serverToolUsePart.kind()).isEqualTo("server_tool_use");
		assertThat(serverToolUsePart.attributes()).isEmpty();
		assertThat(ObjectMappers.jsonMapper().readTree(serverToolUsePart.rawJson()).get("input").get("query").asText())
			.isEqualTo("spring ai");
		assertThat(((UnknownPart) aggregated.getParts().get(1)).kind()).isEqualTo("web_search_tool_result");
		assertThat(aggregated.getParts().get(2)).isEqualTo(TextPart.of("Answer."));
		ChatResponseMetadata finalChunkMetadata = chunks.get(chunks.size() - 1).getMetadata();
		assertThat(finalChunkMetadata.<List<AnthropicWebSearchResult>>get("web-search-results")).hasSize(1);
	}

	@Test
	void promptCopyKeepsReasoningPartsReplayable() {
		Message toolUseResponse = createMockMessageWithRedactedThinkingAndToolUse("redacted-data", "toolu_123",
				"getCurrentWeather", JsonValue.from(Map.of("location", "Paris")));
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(toolUseResponse);
		AssistantMessage output = this.chatModel.call(new Prompt("What's the weather?")).getResult().getOutput();

		Prompt copy = new Prompt(List.of(new UserMessage("What's the weather?"), output)).copy();

		AssistantMessage copied = (AssistantMessage) copy.getInstructions().get(1);
		assertThat(copied).isNotSameAs(output);
		assertThat(copied.getParts()).isEqualTo(output.getParts());
		assertThat(copied.getReasoning().get(0).payload())
			.isEqualTo(new OpaquePayload("anthropic", AnthropicChatModel.PAYLOAD_REDACTED_THINKING, "redacted-data"));
	}

	@Test
	void assistantTurnWithNothingReplayableIsLeftOut() {
		Message mockResponse = createMockMessage("Done.", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);

		AssistantMessage foreignOnly = AssistantMessage.builder()
			.part(new ReasoningPart("openai thoughts", null, new OpaquePayload("openai", "encrypted_content", "enc"),
					Map.of()))
			.build();
		this.chatModel.call(new Prompt(List.of(new UserMessage("Hi"), foreignOnly, new UserMessage("Again"))));

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(this.messageService).create(captor.capture());
		assertThat(captor.getValue().messages()).hasSize(2);
		assertThat(captor.getValue().messages())
			.allSatisfy(message -> assertThat(message.role()).isEqualTo(MessageParam.Role.USER));
	}

	@Test
	void userMessagePartsInterleaveTextAndMediaOnTheWire() {
		Message mockResponse = createMockMessage("Done.", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);
		Media image = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data(new byte[] { 1, 2, 3 }).build();
		UserMessage interleaved = UserMessage.builder()
			.part(TextPart.of("Look at "))
			.part(MediaPart.of(image))
			.part(TextPart.of("this."))
			.build();

		this.chatModel.call(new Prompt(List.of(interleaved)));

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(this.messageService).create(captor.capture());
		List<ContentBlockParam> blocks = captor.getValue().messages().get(0).content().asBlockParams();
		assertThat(blocks).hasSize(3);
		assertThat(blocks.get(0).asText().text()).isEqualTo("Look at ");
		assertThat(blocks.get(1).isImage()).isTrue();
		assertThat(blocks.get(2).asText().text()).isEqualTo("this.");
	}

	@Test
	void cacheBreakpointGoesOnTheLastTextBlockOfAnInterleavedUserMessage() {
		Message mockResponse = createMockMessage("Done.", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);
		AnthropicCacheOptions cacheOptions = AnthropicCacheOptions.builder()
			.strategy(AnthropicCacheStrategy.CONVERSATION_HISTORY)
			.messageTypeMinContentLengths(Map.of(MessageType.USER, 0))
			.build();
		AnthropicChatOptions options = AnthropicChatOptions.builder().cacheOptions(cacheOptions).build();
		Media image = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data(new byte[] { 1, 2, 3 }).build();
		TextPart repeated = TextPart.of("Same text");
		UserMessage interleaved = UserMessage.builder()
			.part(repeated)
			.part(MediaPart.of(image))
			.part(repeated)
			.part(TextPart.of(""))
			.build();

		this.chatModel.call(new Prompt(List.of(interleaved), options));

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(this.messageService).create(captor.capture());
		List<ContentBlockParam> blocks = captor.getValue().messages().get(0).content().asBlockParams();
		// The empty text part is skipped; only the last text block carries the
		// breakpoint,
		// even though the same TextPart instance appears twice
		assertThat(blocks).hasSize(3);
		assertThat(blocks.get(0).asText().cacheControl()).isEmpty();
		assertThat(blocks.get(1).isImage()).isTrue();
		assertThat(blocks.get(2).asText().cacheControl()).isPresent();
	}

	@Test
	void legacyUserMessageKeepsTextThenMediaOrder() {
		Message mockResponse = createMockMessage("Done.", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);
		Media image = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data(new byte[] { 1, 2, 3 }).build();
		UserMessage legacy = UserMessage.builder().media(image).text("describe").build();
		UserMessage mediaOnly = UserMessage.builder().part(MediaPart.of(image)).build();

		this.chatModel.call(new Prompt(List.of(legacy, new AssistantMessage("Sure."), mediaOnly)));

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(this.messageService).create(captor.capture());
		List<ContentBlockParam> legacyBlocks = captor.getValue().messages().get(0).content().asBlockParams();
		assertThat(legacyBlocks).hasSize(2);
		assertThat(legacyBlocks.get(0).asText().text()).isEqualTo("describe");
		assertThat(legacyBlocks.get(1).isImage()).isTrue();
		List<ContentBlockParam> mediaOnlyBlocks = captor.getValue().messages().get(2).content().asBlockParams();
		assertThat(mediaOnlyBlocks).hasSize(1);
		assertThat(mediaOnlyBlocks.get(0).isImage()).isTrue();
		// A text-only message is still sent as a plain string
		assertThat(captor.getValue().messages().get(1).content().isString()).isTrue();
	}

	@Test
	void assistantMediaIsNotReplayed() {
		Message mockResponse = createMockMessage("Done.", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);

		Media image = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data(new byte[] { 1, 2, 3 }).build();
		AssistantMessage history = AssistantMessage.builder()
			.part(TextPart.of("Here is the chart."))
			.part(MediaPart.of(image))
			.build();
		this.chatModel.call(new Prompt(List.of(new UserMessage("Chart?"), history, new UserMessage("Thanks"))));

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(this.messageService).create(captor.capture());
		List<ContentBlockParam> blocks = assistantBlockParams(captor.getValue());
		assertThat(blocks).hasSize(1);
		assertThat(blocks.get(0).isText()).isTrue();
	}

	@SuppressWarnings("unchecked")
	private void givenStream(List<RawMessageStreamEvent> events) {
		StreamResponse<RawMessageStreamEvent> streamResponse = mock(StreamResponse.class);
		given(streamResponse.stream()).willReturn(events.stream());

		HttpResponseFor<StreamResponse<RawMessageStreamEvent>> rawResponse = mock(HttpResponseFor.class);
		given(rawResponse.parse()).willReturn(streamResponse);
		given(rawResponse.headers()).willReturn(Headers.builder().build());

		given(this.anthropicClientAsync.messages()).willReturn(this.messageServiceAsync);
		given(this.messageServiceAsync.withRawResponse()).willReturn(this.messageServiceAsyncWithRawResponse);
		given(this.messageServiceAsyncWithRawResponse.createStreaming(any(MessageCreateParams.class),
				any(RequestOptions.class)))
			.willReturn(CompletableFuture.completedFuture(rawResponse));
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

		Prompt requestPrompt = new Prompt("Test", runtimeOptions);

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

	private static List<ContentBlockParam> assistantBlockParams(MessageCreateParams request) {
		for (MessageParam message : request.messages()) {
			if (message.role() == MessageParam.Role.ASSISTANT && message.content().isBlockParams()) {
				return message.content().asBlockParams();
			}
		}
		return List.of();
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

	private Message createMockMessageWithThinkingAndText(String thinking, String signature, String text) {
		ThinkingBlock thinkingBlock = mock(ThinkingBlock.class);
		given(thinkingBlock.thinking()).willReturn(thinking);
		given(thinkingBlock.signature()).willReturn(signature);

		ContentBlock thinkingContentBlock = mock(ContentBlock.class);
		given(thinkingContentBlock.isText()).willReturn(false);
		given(thinkingContentBlock.isToolUse()).willReturn(false);
		given(thinkingContentBlock.isThinking()).willReturn(true);
		given(thinkingContentBlock.asThinking()).willReturn(thinkingBlock);

		TextBlock textBlock = mock(TextBlock.class);
		given(textBlock.text()).willReturn(text);

		ContentBlock textContentBlock = mock(ContentBlock.class);
		given(textContentBlock.isText()).willReturn(true);
		given(textContentBlock.asText()).willReturn(textBlock);

		return createMockMessage(List.of(thinkingContentBlock, textContentBlock), StopReason.END_TURN, 10L, 20L);
	}

	private Message createMockMessageWithThinkingAndToolUse(String thinking, String signature, String toolId,
			String toolName, JsonValue input) {
		ThinkingBlock thinkingBlock = mock(ThinkingBlock.class);
		given(thinkingBlock.thinking()).willReturn(thinking);
		given(thinkingBlock.signature()).willReturn(signature);

		ContentBlock thinkingContentBlock = mock(ContentBlock.class);
		given(thinkingContentBlock.isText()).willReturn(false);
		given(thinkingContentBlock.isToolUse()).willReturn(false);
		given(thinkingContentBlock.isThinking()).willReturn(true);
		given(thinkingContentBlock.asThinking()).willReturn(thinkingBlock);

		ContentBlock toolUseContentBlock = toolUseContentBlock(toolId, toolName, input);

		return createMockMessage(List.of(thinkingContentBlock, toolUseContentBlock), StopReason.TOOL_USE, 15L, 25L);
	}

	private Message createMockMessageWithRedactedThinkingAndToolUse(String data, String toolId, String toolName,
			JsonValue input) {
		RedactedThinkingBlock redactedThinkingBlock = mock(RedactedThinkingBlock.class);
		given(redactedThinkingBlock.data()).willReturn(data);

		ContentBlock redactedThinkingContentBlock = mock(ContentBlock.class);
		given(redactedThinkingContentBlock.isText()).willReturn(false);
		given(redactedThinkingContentBlock.isToolUse()).willReturn(false);
		given(redactedThinkingContentBlock.isThinking()).willReturn(false);
		given(redactedThinkingContentBlock.isRedactedThinking()).willReturn(true);
		given(redactedThinkingContentBlock.asRedactedThinking()).willReturn(redactedThinkingBlock);

		ContentBlock toolUseContentBlock = toolUseContentBlock(toolId, toolName, input);

		return createMockMessage(List.of(redactedThinkingContentBlock, toolUseContentBlock), StopReason.TOOL_USE, 15L,
				25L);
	}

	private ContentBlock toolUseContentBlock(String toolId, String toolName, JsonValue input) {
		ToolUseBlock toolUseBlock = mock(ToolUseBlock.class);
		given(toolUseBlock.id()).willReturn(toolId);
		given(toolUseBlock.name()).willReturn(toolName);
		given(toolUseBlock._input()).willReturn(input);

		ContentBlock contentBlock = mock(ContentBlock.class);
		given(contentBlock.isText()).willReturn(false);
		given(contentBlock.isToolUse()).willReturn(true);
		given(contentBlock.asToolUse()).willReturn(toolUseBlock);
		return contentBlock;
	}

	private Message createMockMessage(List<ContentBlock> contentBlocks, StopReason stopReason, long inputTokens,
			long outputTokens) {
		Usage usage = mock(Usage.class);
		given(usage.inputTokens()).willReturn(inputTokens);
		given(usage.outputTokens()).willReturn(outputTokens);

		Message message = mock(Message.class);
		given(message.id()).willReturn("msg_123");
		given(message.model()).willReturn(Model.CLAUDE_SONNET_4_5);
		given(message.content()).willReturn(contentBlocks);
		given(message.stopReason()).willReturn(Optional.of(stopReason));
		given(message.usage()).willReturn(usage);

		return message;
	}

	private ContentBlock textContentBlock(String text) {
		TextBlock textBlock = mock(TextBlock.class);
		given(textBlock.text()).willReturn(text);

		ContentBlock contentBlock = mock(ContentBlock.class);
		given(contentBlock.isText()).willReturn(true);
		given(contentBlock.asText()).willReturn(textBlock);
		return contentBlock;
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
		given(message.model()).willReturn(Model.CLAUDE_SONNET_4_5);
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
		given(message.model()).willReturn(Model.CLAUDE_SONNET_4_5);
		given(message.content()).willReturn(List.of(contentBlock));
		given(message.stopReason()).willReturn(Optional.of(stopReason));
		given(message.usage()).willReturn(usage);

		return message;
	}

	private RawMessageStreamEvent messageStartEvent() {
		Usage usage = mock(Usage.class);
		given(usage.inputTokens()).willReturn(10L);

		Message message = mock(Message.class);
		given(message.id()).willReturn("msg_stream");
		given(message.model()).willReturn(Model.CLAUDE_SONNET_4_5);
		given(message.usage()).willReturn(usage);

		return RawMessageStreamEvent.ofMessageStart(RawMessageStartEvent.builder().message(message).build());
	}

	private RawMessageStreamEvent thinkingStartEvent() {
		ThinkingBlock thinkingBlock = mock(ThinkingBlock.class);
		return RawMessageStreamEvent
			.ofContentBlockStart(RawContentBlockStartEvent.builder().contentBlock(thinkingBlock).index(0L).build());
	}

	private RawMessageStreamEvent toolUseStartEvent(String id, String name) {
		ToolUseBlock toolUseBlock = mock(ToolUseBlock.class);
		given(toolUseBlock.id()).willReturn(id);
		given(toolUseBlock.name()).willReturn(name);

		return RawMessageStreamEvent
			.ofContentBlockStart(RawContentBlockStartEvent.builder().contentBlock(toolUseBlock).index(1L).build());
	}

	private RawMessageStreamEvent textStartEvent(long index) {
		TextBlock textBlock = mock(TextBlock.class);
		return RawMessageStreamEvent
			.ofContentBlockStart(RawContentBlockStartEvent.builder().contentBlock(textBlock).index(index).build());
	}

	private RawMessageStreamEvent redactedThinkingStartEvent(String data, long index) {
		RedactedThinkingBlock redactedBlock = mock(RedactedThinkingBlock.class);
		given(redactedBlock.data()).willReturn(data);
		return RawMessageStreamEvent
			.ofContentBlockStart(RawContentBlockStartEvent.builder().contentBlock(redactedBlock).index(index).build());
	}

	private RawMessageStreamEvent textDeltaEvent(String text, long index) {
		return RawMessageStreamEvent
			.ofContentBlockDelta(RawContentBlockDeltaEvent.builder().textDelta(text).index(index).build());
	}

	private RawMessageStreamEvent thinkingDeltaEvent(String thinking) {
		return RawMessageStreamEvent
			.ofContentBlockDelta(RawContentBlockDeltaEvent.builder().thinkingDelta(thinking).index(0L).build());
	}

	private RawMessageStreamEvent signatureDeltaEvent(String signature) {
		return RawMessageStreamEvent
			.ofContentBlockDelta(RawContentBlockDeltaEvent.builder().signatureDelta(signature).index(0L).build());
	}

	private RawMessageStreamEvent inputJsonDeltaEvent(String partialJson) {
		return inputJsonDeltaEvent(partialJson, 1L);
	}

	private RawMessageStreamEvent inputJsonDeltaEvent(String partialJson, long index) {
		return RawMessageStreamEvent
			.ofContentBlockDelta(RawContentBlockDeltaEvent.builder().inputJsonDelta(partialJson).index(index).build());
	}

	private RawMessageStreamEvent contentBlockStopEvent(long index) {
		return RawMessageStreamEvent.ofContentBlockStop(RawContentBlockStopEvent.builder().index(index).build());
	}

	private RawMessageStreamEvent messageDeltaEvent(StopReason stopReason) {
		return RawMessageStreamEvent.ofMessageDelta(RawMessageDeltaEvent.builder()
			.delta(RawMessageDeltaEvent.Delta.builder()
				.container(Optional.empty())
				.stopDetails(Optional.empty())
				.stopReason(stopReason)
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

		given(this.messageServiceWithRawResponse.create(any(MessageCreateParams.class), any(RequestOptions.class)))
			.willAnswer(invocation -> {
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
		given(this.messageServiceAsyncWithRawResponse.createStreaming(any(MessageCreateParams.class),
				any(RequestOptions.class)))
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
		given(this.messageServiceAsyncWithRawResponse.createStreaming(any(MessageCreateParams.class),
				any(RequestOptions.class)))
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

	@Test
	void citationDocumentsAreSentOnlyInFirstUserMessage() {
		Message mockResponse = createMockMessage("Answer", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);

		AnthropicCitationDocument document = AnthropicCitationDocument.builder()
			.plainText("Reference material")
			.title("Reference")
			.citationsEnabled(true)
			.build();
		AnthropicChatOptions options = AnthropicChatOptions.builder().citationDocuments(document).build();

		UserMessage user1 = new UserMessage("First question");
		AssistantMessage assistant1 = new AssistantMessage("First answer");
		UserMessage user2 = new UserMessage("Second question");

		this.chatModel.call(new Prompt(List.of(user1, assistant1, user2), options));

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(this.messageService).create(captor.capture());

		List<MessageParam> messages = captor.getValue().messages();
		assertThat(messages).hasSize(3);

		List<ContentBlockParam> firstUserBlocks = messages.get(0).content().blockParams().orElseThrow();
		assertThat(firstUserBlocks).hasSize(2);
		assertThat(firstUserBlocks.get(0).isDocument()).isTrue();
		assertThat(firstUserBlocks.get(1).asText().text()).isEqualTo("First question");

		assertThat(messages.get(2).content().string()).contains("Second question");
		assertThat(messages.get(2).content().blockParams()).isEmpty();

		long documentBlocks = messages.stream()
			.flatMap(message -> message.content().blockParams().stream().flatMap(List::stream))
			.filter(ContentBlockParam::isDocument)
			.count();
		assertThat(documentBlocks).isEqualTo(1);
	}

	@Test
	void citationDocumentCacheBreakpointPerStrategy() {
		assertThat(lastDocumentHasCacheControl(AnthropicCacheStrategy.NONE)).isFalse();
		assertThat(lastDocumentHasCacheControl(AnthropicCacheStrategy.TOOLS_ONLY)).isFalse();
		assertThat(lastDocumentHasCacheControl(AnthropicCacheStrategy.SYSTEM_ONLY)).isTrue();
		assertThat(lastDocumentHasCacheControl(AnthropicCacheStrategy.SYSTEM_AND_TOOLS)).isTrue();
		assertThat(lastDocumentHasCacheControl(AnthropicCacheStrategy.CONVERSATION_HISTORY)).isTrue();
	}

	@Test
	void citationDocumentCacheBreakpointGoesOnLastDocumentOnly() {
		AnthropicCitationDocument first = AnthropicCitationDocument.builder().plainText("First").build();
		AnthropicCitationDocument second = AnthropicCitationDocument.builder().plainText("Second").build();
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.citationDocuments(first, second)
			.cacheOptions(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.SYSTEM_ONLY).build())
			.build();

		MessageCreateParams request = this.chatModel.createRequest(new Prompt("Question", options), false);

		List<ContentBlockParam> blocks = request.messages().get(0).content().blockParams().orElseThrow();
		assertThat(blocks).hasSize(3);
		assertThat(blocks.get(0).asDocument().cacheControl()).isEmpty();
		assertThat(blocks.get(1).asDocument().cacheControl()).isPresent();
		// SYSTEM_ONLY does not cache the user text
		assertThat(blocks.get(2).asText().cacheControl()).isEmpty();
	}

	@Test
	void citationDocumentAndUserTextBothCachedUnderConversationHistory() {
		// Batch Q&A over the same document: each request is a fresh single-turn
		// prompt, so the document breakpoint is what produces cache hits across
		// requests while the user text breakpoint changes every time.
		AnthropicCitationDocument document = AnthropicCitationDocument.builder().plainText("Reference").build();
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.citationDocuments(document)
			.cacheOptions(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.CONVERSATION_HISTORY).build())
			.build();

		MessageCreateParams request = this.chatModel.createRequest(new Prompt("Question", options), false);

		List<ContentBlockParam> blocks = request.messages().get(0).content().blockParams().orElseThrow();
		assertThat(blocks).hasSize(2);
		assertThat(blocks.get(0).asDocument().cacheControl()).isPresent();
		assertThat(blocks.get(1).asText().cacheControl()).isPresent();
	}

	@Test
	void citationDocumentBreakpointTakesPrecedenceOverToolDefinitions() {
		// System, document, last user text, and last tool result each take a
		// breakpoint, which is all four. Tool definitions are resolved last and get
		// none. That is harmless: tools precede the system prompt in the request and
		// are inside the prefix cached by the system breakpoint.
		AnthropicCitationDocument document = AnthropicCitationDocument.builder().plainText("Reference").build();
		AnthropicCacheOptions cacheOptions = AnthropicCacheOptions.builder()
			.strategy(AnthropicCacheStrategy.CONVERSATION_HISTORY)
			.cacheToolResults(true)
			.build();
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.citationDocuments(document)
			.cacheOptions(cacheOptions)
			.toolCallbacks(List.of(new TestToolCallback("getWeather")))
			.build();

		List<org.springframework.ai.chat.messages.Message> messages = new java.util.ArrayList<>();
		messages.add(new SystemMessage("You are a helpful assistant."));
		messages.addAll(toolCallingConversation());

		MessageCreateParams request = this.chatModel.createRequest(new Prompt(messages, options), false);

		assertThat(request.system().orElseThrow().asTextBlockParams().get(0).cacheControl()).isPresent();

		List<ContentBlockParam> firstUserBlocks = request.messages().get(0).content().blockParams().orElseThrow();
		assertThat(firstUserBlocks.get(0).asDocument().cacheControl()).isPresent();
		assertThat(firstUserBlocks.get(1).asText().cacheControl()).isPresent();

		assertThat(lastToolResultBlock(request).cacheControl()).isPresent();

		List<ToolUnion> tools = request.tools().orElseThrow();
		assertThat(tools).hasSize(1);
		assertThat(tools.get(0).asTool().cacheControl()).isEmpty();
	}

	private boolean lastDocumentHasCacheControl(AnthropicCacheStrategy strategy) {
		AnthropicCitationDocument document = AnthropicCitationDocument.builder().plainText("Reference").build();
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.citationDocuments(document)
			.cacheOptions(AnthropicCacheOptions.builder().strategy(strategy).build())
			.build();

		MessageCreateParams request = this.chatModel.createRequest(new Prompt("Question", options), false);

		List<ContentBlockParam> blocks = request.messages().get(0).content().blockParams().orElseThrow();
		return blocks.get(0).asDocument().cacheControl().isPresent();
	}

	static class TestToolCallback implements ToolCallback {

		private final ToolDefinition toolDefinition;

		TestToolCallback(String name) {
			this.toolDefinition = DefaultToolDefinition.builder().name(name).inputSchema("{}").build();
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return this.toolDefinition;
		}

		@Override
		public String call(String toolInput) {
			return "Mission accomplished!";
		}

	}

}
