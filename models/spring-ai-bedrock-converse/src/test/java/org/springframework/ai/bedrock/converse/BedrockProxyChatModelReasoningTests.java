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

package org.springframework.ai.bedrock.converse;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.core.document.internal.MapDocument;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStart;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseMetrics;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningContentBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningTextBlock;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlockStart;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Mapping tests for Bedrock Converse {@code reasoningContent} preservation and replay
 * through the tool-calling loop. See gh-6413.
 *
 * @author Jewoo Shin
 */
@ExtendWith(MockitoExtension.class)
class BedrockProxyChatModelReasoningTests {

	private static final String REASONING_TEXT = "The user wants the weather in Paris. I will call the weather tool.";

	private static final String REASONING_SIGNATURE = "EsoBCkgIBhABGAIiQ-signature-token";

	private @Mock BedrockRuntimeClient bedrockRuntimeClient;

	private @Mock BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient;

	private BedrockProxyChatModel chatModel;

	@BeforeEach
	void beforeEach() {
		this.chatModel = BedrockProxyChatModel.builder()
			.bedrockRuntimeClient(this.bedrockRuntimeClient)
			.bedrockRuntimeAsyncClient(this.bedrockRuntimeAsyncClient)
			.build();
	}

	@Test
	void toolUseResponseReplaysSignedReasoningContentBeforeToolUse() {
		ContentBlock reasoningBlock = ContentBlock.fromReasoningContent(ReasoningContentBlock.builder()
			.reasoningText(ReasoningTextBlock.builder().text(REASONING_TEXT).signature(REASONING_SIGNATURE).build())
			.build());

		given(this.bedrockRuntimeClient.converse(isA(ConverseRequest.class)))
			.willReturn(toolUseResponse(reasoningBlock))
			.willReturn(finalResponse());

		runToolLoop();

		Message assistantMessage = capturedSecondRequestAssistantMessage();
		List<ContentBlock> content = assistantMessage.content();

		int reasoningIndex = indexOfType(content, ContentBlock.Type.REASONING_CONTENT);
		int toolUseIndex = indexOfType(content, ContentBlock.Type.TOOL_USE);

		// The signed reasoning block must be replayed, unmodified, and ordered before the
		// tool-use block it belongs to.
		assertThat(reasoningIndex).isGreaterThanOrEqualTo(0);
		assertThat(toolUseIndex).isGreaterThanOrEqualTo(0);
		assertThat(reasoningIndex).isLessThan(toolUseIndex);

		ReasoningContentBlock replayed = content.get(reasoningIndex).reasoningContent();
		assertThat(replayed.reasoningText().text()).isEqualTo(REASONING_TEXT);
		assertThat(replayed.reasoningText().signature()).isEqualTo(REASONING_SIGNATURE);
		assertThat(content.get(toolUseIndex).toolUse().name()).isEqualTo("getCurrentWeather");
	}

	@Test
	void redactedReasoningContentIsPreservedAndReplayed() {
		byte[] redacted = "redacted-reasoning-bytes".getBytes(StandardCharsets.UTF_8);
		ContentBlock reasoningBlock = ContentBlock.fromReasoningContent(
				ReasoningContentBlock.builder().redactedContent(SdkBytes.fromByteArray(redacted)).build());

		given(this.bedrockRuntimeClient.converse(isA(ConverseRequest.class)))
			.willReturn(toolUseResponse(reasoningBlock))
			.willReturn(finalResponse());

		runToolLoop();

		Message assistantMessage = capturedSecondRequestAssistantMessage();
		List<ContentBlock> content = assistantMessage.content();

		int reasoningIndex = indexOfType(content, ContentBlock.Type.REASONING_CONTENT);
		assertThat(reasoningIndex).isGreaterThanOrEqualTo(0);
		assertThat(reasoningIndex).isLessThan(indexOfType(content, ContentBlock.Type.TOOL_USE));

		ReasoningContentBlock replayed = content.get(reasoningIndex).reasoningContent();
		assertThat(replayed.reasoningText()).isNull();
		assertThat(replayed.redactedContent().asByteArray()).isEqualTo(redacted);
	}

	@Test
	void reasoningOnlyResponseExposesReasoningWithoutDisplacingFinalText() {
		ContentBlock reasoningBlock = ContentBlock.fromReasoningContent(ReasoningContentBlock.builder()
			.reasoningText(ReasoningTextBlock.builder().text(REASONING_TEXT).signature(REASONING_SIGNATURE).build())
			.build());

		ConverseResponse response = ConverseResponse.builder()
			.output(ConverseOutput.builder()
				.message(Message.builder()
					.role(ConversationRole.ASSISTANT)
					.content(reasoningBlock, ContentBlock.fromText("The weather in Paris is 15°C."))
					.build())
				.build())
			.usage(TokenUsage.builder().inputTokens(20).outputTokens(30).totalTokens(50).build())
			.stopReason(StopReason.END_TURN)
			.build();

		given(this.bedrockRuntimeClient.converse(isA(ConverseRequest.class))).willReturn(response);

		ChatResponse chatResponse = this.chatModel.call(new Prompt("What is the weather in Paris?"));

		AssistantMessage output = chatResponse.getResult().getOutput();
		// The final text returned by getResult() must be unchanged.
		assertThat(output.getText()).isEqualTo("The weather in Paris is 15°C.");
		// The reasoning state must still be observable on the final assistant message.
		assertThat(output).isInstanceOf(BedrockAssistantMessage.class);
		BedrockReasoningContent reasoning = ((BedrockAssistantMessage) output).getReasoningContents().get(0);
		assertThat(reasoning.getText()).isEqualTo(REASONING_TEXT);
		assertThat(reasoning.getSignature()).isEqualTo(REASONING_SIGNATURE);
	}

	@Test
	void nonReasoningToolUseResponseAddsNoReasoningBlock() {
		ConverseResponse toolUse = ConverseResponse.builder()
			.output(ConverseOutput.builder()
				.message(Message.builder()
					.role(ConversationRole.ASSISTANT)
					.content(ContentBlock.fromText("Let me check the weather for you."), toolUseBlock())
					.build())
				.build())
			.usage(TokenUsage.builder().inputTokens(200).outputTokens(50).totalTokens(250).build())
			.stopReason(StopReason.TOOL_USE)
			.metrics(ConverseMetrics.builder().latencyMs(1000L).build())
			.build();

		given(this.bedrockRuntimeClient.converse(isA(ConverseRequest.class))).willReturn(toolUse)
			.willReturn(finalResponse());

		runToolLoop();

		Message assistantMessage = capturedSecondRequestAssistantMessage();
		List<ContentBlock> content = assistantMessage.content();

		// Backward compatibility: a normal text + toolUse response must not introduce any
		// reasoning block on replay.
		assertThat(indexOfType(content, ContentBlock.Type.REASONING_CONTENT)).isEqualTo(-1);
		assertThat(indexOfType(content, ContentBlock.Type.TOOL_USE)).isGreaterThanOrEqualTo(0);
	}

	@Test
	void streamingReasoningContentSurvivesAggregationAndReplay() {
		byte[] redacted = "redacted-reasoning".getBytes(StandardCharsets.UTF_8);
		AtomicReference<ConverseStreamResponseHandler> handlerRef = new AtomicReference<>();
		doAnswer(invocation -> {
			handlerRef.set(invocation.getArgument(1));
			return CompletableFuture.completedFuture(null);
		}).when(this.bedrockRuntimeAsyncClient)
			.converseStream(isA(ConverseStreamRequest.class), isA(ConverseStreamResponseHandler.class));

		CompletableFuture<List<ChatResponse>> chunksFuture = this.chatModel.stream(new Prompt("Use the weather tool"))
			.collectList()
			.toFuture();
		ConverseStreamResponseHandler handler = handlerRef.get();
		handler.onEventStream(SdkPublisher.fromIterable(streamEvents(redacted)));
		handler.complete();
		List<ChatResponse> chunks = chunksFuture.join();
		AtomicReference<ChatResponse> aggregated = new AtomicReference<>();
		new MessageAggregator().aggregate(Flux.fromIterable(chunks), aggregated::set).blockLast();

		AssistantMessage output = aggregated.get().getResult().getOutput();
		assertThat(output).isInstanceOf(BedrockAssistantMessage.class);
		assertThat(output.getText()).isEqualTo("Ordinary text");
		assertThat(output.getToolCalls()).singleElement().satisfies(toolCall -> {
			assertThat(toolCall.name()).isEqualTo("getCurrentWeather");
			assertThat(toolCall.arguments()).isEqualTo("{\"location\":\"Paris\"}");
		});

		List<BedrockReasoningContent> reasoningContents = ((BedrockAssistantMessage) output).getReasoningContents();
		assertThat(reasoningContents).hasSize(2);
		assertThat(reasoningContents.get(0).getRedactedContent()).isEqualTo(redacted);
		assertThat(reasoningContents.get(1).getText()).isEqualTo("Think before using the tool.");
		assertThat(reasoningContents.get(1).getSignature()).isEqualTo("signed-reasoning");

		ConverseRequest replay = this.chatModel
			.createRequest(new Prompt(List.of(output), BedrockChatOptions.builder().build()));
		List<ContentBlock> replayedContent = replay.messages().get(0).content();
		assertThat(replayedContent.stream().filter(block -> block.type() == ContentBlock.Type.REASONING_CONTENT))
			.map(ContentBlock::reasoningContent)
			.containsExactly(reasoningContents.get(0).toContentBlock().reasoningContent(),
					reasoningContents.get(1).toContentBlock().reasoningContent());
		assertThat(indexOfType(replayedContent, ContentBlock.Type.REASONING_CONTENT))
			.isLessThan(indexOfType(replayedContent, ContentBlock.Type.TOOL_USE));
	}

	private static List<ConverseStreamOutput> streamEvents(byte[] redacted) {
		List<ConverseStreamOutput> events = new ArrayList<>();
		events.add(reasoningDelta(1, ReasoningContentBlockDelta.fromText("Think before ")));
		events.add(reasoningDelta(0, ReasoningContentBlockDelta.fromRedactedContent(SdkBytes.fromByteArray(redacted))));
		events.add(reasoningDelta(1, ReasoningContentBlockDelta.fromText("using the tool.")));
		events.add(reasoningDelta(1, ReasoningContentBlockDelta.fromSignature("signed-")));
		events.add(reasoningDelta(1, ReasoningContentBlockDelta.fromSignature("reasoning")));
		events.add(ConverseStreamOutput.contentBlockStartBuilder()
			.contentBlockIndex(2)
			.start(ContentBlockStart
				.fromToolUse(ToolUseBlockStart.builder().toolUseId("tooluse_123").name("getCurrentWeather").build()))
			.build());
		events.add(ConverseStreamOutput.contentBlockDeltaBuilder()
			.contentBlockIndex(2)
			.delta(ContentBlockDelta.fromToolUse(ToolUseBlockDelta.builder().input("{\"location\":\"Paris\"}").build()))
			.build());
		events.add(ConverseStreamOutput.contentBlockDeltaBuilder()
			.contentBlockIndex(3)
			.delta(ContentBlockDelta.fromText("Ordinary text"))
			.build());
		events.add(ConverseStreamOutput.messageStopBuilder().stopReason(StopReason.TOOL_USE).build());
		events.add(ConverseStreamOutput.metadataBuilder()
			.usage(TokenUsage.builder().inputTokens(10).outputTokens(20).totalTokens(30).build())
			.build());
		return events;
	}

	private static ContentBlockDeltaEvent reasoningDelta(int index, ReasoningContentBlockDelta delta) {
		return ConverseStreamOutput.contentBlockDeltaBuilder()
			.contentBlockIndex(index)
			.delta(ContentBlockDelta.fromReasoningContent(delta))
			.build();
	}

	private void runToolLoop() {
		ToolCallback toolCallback = FunctionToolCallback.builder("getCurrentWeather", (Request request) -> "15°C")
			.description("Gets the weather in location")
			.inputType(Request.class)
			.build();

		ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();

		Prompt prompt = new Prompt("What is the weather in Paris?",
				BedrockChatOptions.builder().toolCallbacks(toolCallback).build());

		ChatResponse result = this.chatModel.call(prompt);

		while (result.hasToolCalls()) {
			ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, result);
			prompt = new Prompt(toolExecutionResult.conversationHistory(),
					BedrockChatOptions.builder().toolCallbacks(toolCallback).build());
			result = this.chatModel.call(prompt);
		}
	}

	private Message capturedSecondRequestAssistantMessage() {
		ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
		verify(this.bedrockRuntimeClient, times(2)).converse(captor.capture());
		ConverseRequest secondRequest = captor.getAllValues().get(1);
		return secondRequest.messages()
			.stream()
			.filter(m -> m.role() == ConversationRole.ASSISTANT)
			.findFirst()
			.orElseThrow(() -> new AssertionError("No assistant message in the replayed request"));
	}

	private static ConverseResponse toolUseResponse(ContentBlock reasoningBlock) {
		return ConverseResponse.builder()
			.output(ConverseOutput.builder()
				.message(Message.builder()
					.role(ConversationRole.ASSISTANT)
					.content(reasoningBlock, toolUseBlock())
					.build())
				.build())
			.usage(TokenUsage.builder().inputTokens(200).outputTokens(50).totalTokens(250).build())
			.stopReason(StopReason.TOOL_USE)
			.metrics(ConverseMetrics.builder().latencyMs(1000L).build())
			.build();
	}

	private static ConverseResponse finalResponse() {
		return ConverseResponse.builder()
			.output(ConverseOutput.builder()
				.message(Message.builder()
					.role(ConversationRole.ASSISTANT)
					.content(ContentBlock.fromText("The weather in Paris is 15°C."))
					.build())
				.build())
			.usage(TokenUsage.builder().inputTokens(300).outputTokens(30).totalTokens(330).build())
			.stopReason(StopReason.END_TURN)
			.metrics(ConverseMetrics.builder().latencyMs(500L).build())
			.build();
	}

	private static ContentBlock toolUseBlock() {
		return ContentBlock.fromToolUse(ToolUseBlock.builder()
			.toolUseId("tooluse_123")
			.name("getCurrentWeather")
			.input(MapDocument.mapBuilder().putString("location", "Paris, France").putString("unit", "C").build())
			.build());
	}

	private static int indexOfType(List<ContentBlock> content, ContentBlock.Type type) {
		for (int i = 0; i < content.size(); i++) {
			if (content.get(i).type() == type) {
				return i;
			}
		}
		return -1;
	}

	public record Request(String location, String unit) {
	}

}
