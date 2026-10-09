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

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.awscore.DefaultAwsResponseMetadata;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.BedrockRuntimeResponseMetadata;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStart;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningContentBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningTextBlock;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlockStart;

import org.springframework.ai.bedrock.converse.api.BedrockCacheOptions;
import org.springframework.ai.bedrock.converse.api.BedrockCacheStrategy;
import org.springframework.ai.bedrock.converse.api.BedrockCacheTtl;
import org.springframework.ai.bedrock.converse.api.ConverseApiUtils;
import org.springframework.ai.bedrock.converse.api.MediaFetcher;
import org.springframework.ai.chat.messages.AssistantMessage;
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
import org.springframework.ai.chat.messages.part.ToolResultPart;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.util.MimeType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BedrockProxyChatModelTest {

	private static final String REASONING_TEXT = "The user wants the weather in Paris.";

	private static final String SIGNATURE = "EsoBCkgIBhABGAIiQ-signature-token";

	private static final byte[] REDACTED = "redacted-reasoning-bytes".getBytes(StandardCharsets.UTF_8);

	private static final OpaquePayload SIGNATURE_PAYLOAD = new OpaquePayload(ConverseApiUtils.BEDROCK_PROVIDER,
			ConverseApiUtils.PAYLOAD_SIGNATURE, SIGNATURE);

	private static final OpaquePayload REDACTED_PAYLOAD = new OpaquePayload(ConverseApiUtils.BEDROCK_PROVIDER,
			ConverseApiUtils.PAYLOAD_REDACTED_CONTENT, Base64.getEncoder().encodeToString(REDACTED));

	private static final Map<String, String> BEDROCK_ATTRIBUTES = Map.of(StreamingParts.PROVIDER_ATTRIBUTE,
			ConverseApiUtils.BEDROCK_PROVIDER);

	private static final ReasoningPart SIGNED_REASONING = new ReasoningPart(REASONING_TEXT, null, SIGNATURE_PAYLOAD,
			BEDROCK_ATTRIBUTES);

	private static final ReasoningPart REDACTED_REASONING = new ReasoningPart(null, null, REDACTED_PAYLOAD,
			BEDROCK_ATTRIBUTES);

	private static final ReasoningPart UNSIGNED_REASONING = new ReasoningPart(REASONING_TEXT, null, null,
			BEDROCK_ATTRIBUTES);

	private static final AssistantMessage.ToolCall WEATHER_CALL = new AssistantMessage.ToolCall("tooluse_123",
			"function", "getCurrentWeather", "{\"location\":\"Paris\"}");

	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	private DefaultAwsRegionProviderChain.Builder awsRegionProviderBuilder;

	@Mock
	private BedrockRuntimeClient syncClient;

	@Mock
	private BedrockRuntimeAsyncClient asyncClient;

	private BedrockProxyChatModel newModel() {
		return new BedrockProxyChatModel(this.syncClient, this.asyncClient, BedrockChatOptions.builder().build(),
				ObservationRegistry.NOOP, ToolCallingManager.builder().build());
	}

	@Test
	void shouldIgnoreExceptionAndUseDefault() {
		try (MockedStatic<DefaultAwsRegionProviderChain> mocked = mockStatic(DefaultAwsRegionProviderChain.class)) {
			when(this.awsRegionProviderBuilder.build().getRegion())
				.thenThrow(SdkClientException.builder().message("failed load").build());
			mocked.when(DefaultAwsRegionProviderChain::builder).thenReturn(this.awsRegionProviderBuilder);
			BedrockProxyChatModel.builder().build();
		}
	}

	@Test
	void sanitizeDocumentNameShouldReplaceDotsWithHyphens() {
		String name = "media-vnd.openxmlformats-officedocument.spreadsheetml.sheet-abc123";
		assertThat(BedrockProxyChatModel.sanitizeDocumentName(name))
			.isEqualTo("media-vnd-openxmlformats-officedocument-spreadsheetml-sheet-abc123");
	}

	@Test
	void sanitizeDocumentNameShouldPreserveValidName() {
		String name = "media-pdf-abc123";
		assertThat(BedrockProxyChatModel.sanitizeDocumentName(name)).isEqualTo(name);
	}

	@Test
	void sanitizeDocumentNameShouldPreserveAllowedSpecialCharacters() {
		String name = "my document (1) [draft]";
		assertThat(BedrockProxyChatModel.sanitizeDocumentName(name)).isEqualTo(name);
	}

	// -------------------------------------------------------------------------
	// Protocol rejection for URL-object media
	// -------------------------------------------------------------------------

	@Test
	void fileProtocolUrlMediaThrowsIllegalArgumentException() throws Exception {
		BedrockProxyChatModel model = newModel();
		Media media = Media.builder()
			.mimeType(MimeType.valueOf("image/png"))
			.data(new URL("file:///etc/passwd"))
			.build();

		assertThatThrownBy(() -> model.mapMediaToContentBlock(media)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Failed to read media data from URL")
			.cause()
			.isInstanceOf(SecurityException.class)
			.hasMessageContaining("Unsupported URL protocol: file");
	}

	@Test
	void ftpProtocolUrlMediaThrowsIllegalArgumentException() throws Exception {
		BedrockProxyChatModel model = newModel();
		Media media = Media.builder()
			.mimeType(MimeType.valueOf("image/png"))
			.data(new URL("ftp://internal-server/data.png"))
			.build();

		assertThatThrownBy(() -> model.mapMediaToContentBlock(media)).isInstanceOf(IllegalArgumentException.class)
			.cause()
			.isInstanceOf(SecurityException.class)
			.hasMessageContaining("Unsupported URL protocol: ftp");
	}

	// -------------------------------------------------------------------------
	// Pre-flight SSRF block for URL-object media
	// -------------------------------------------------------------------------

	@Test
	void loopbackHttpUrlMediaThrowsIllegalArgumentException() throws Exception {
		BedrockProxyChatModel model = newModel();
		Media media = Media.builder()
			.mimeType(MimeType.valueOf("image/png"))
			.data(new URL("http://127.0.0.1/image.png"))
			.build();

		assertThatThrownBy(() -> model.mapMediaToContentBlock(media)).isInstanceOf(IllegalArgumentException.class)
			.cause()
			.isInstanceOf(SecurityException.class);
	}

	@Test
	void awsImdsHttpUrlMediaThrowsIllegalArgumentException() throws Exception {
		// Primary scenario: AWS IMDS credential theft via URL object
		BedrockProxyChatModel model = newModel();
		Media media = Media.builder()
			.mimeType(MimeType.valueOf("image/png"))
			.data(new URL("http://169.254.169.254/latest/meta-data/iam/security-credentials/"))
			.build();

		assertThatThrownBy(() -> model.mapMediaToContentBlock(media)).isInstanceOf(IllegalArgumentException.class)
			.cause()
			.isInstanceOf(SecurityException.class);
	}

	// -------------------------------------------------------------------------
	// Pre-flight SSRF block for String URL media
	// -------------------------------------------------------------------------

	@Test
	void loopbackStringUrlMediaThrowsRuntimeException() {
		BedrockProxyChatModel model = newModel();
		// 127.0.0.1 passes isValidURLStrict (has dots) but is blocked by
		// assertNoInternalAddress
		Media media = Media.builder()
			.mimeType(MimeType.valueOf("image/png"))
			.data("http://127.0.0.1/image.png")
			.build();

		assertThatThrownBy(() -> model.mapMediaToContentBlock(media)).isInstanceOf(RuntimeException.class)
			.hasMessageContaining("URL is not valid under strict validation rules")
			.isInstanceOf(SecurityException.class);
	}

	@Test
	void awsImdsStringUrlMediaThrowsRuntimeException() {
		// Primary scenario: AWS IMDS credential theft via String URL
		BedrockProxyChatModel model = newModel();
		Media media = Media.builder()
			.mimeType(MimeType.valueOf("image/png"))
			.data("http://169.254.169.254/latest/meta-data/iam/security-credentials/")
			.build();

		assertThatThrownBy(() -> model.mapMediaToContentBlock(media)).isInstanceOf(RuntimeException.class)
			.isInstanceOf(SecurityException.class);
	}

	// -------------------------------------------------------------------------
	// MediaFetcher injection allows restricting media sources (allowlist)
	// -------------------------------------------------------------------------

	@Test
	void allowlistRejectsUnlistedStringUrlMediaThrowsRuntimeException() {
		BedrockProxyChatModel model = new BedrockProxyChatModel(this.syncClient, this.asyncClient,
				BedrockChatOptions.builder().build(), ObservationRegistry.NOOP, ToolCallingManager.builder().build(),
				new MediaFetcher(java.util.Set.of("trusted-cdn.com")));
		Media media = Media.builder().mimeType(MimeType.valueOf("image/png")).data("http://evil.com/image.png").build();

		assertThatThrownBy(() -> model.mapMediaToContentBlock(media)).isInstanceOf(RuntimeException.class)
			.cause()
			.isInstanceOf(SecurityException.class)
			.hasMessageContaining("evil.com");
	}

	@Test
	void requestParametersWithMixedFlatAndNestedValuesBuildAdditionalModelRequestFields() {
		BedrockProxyChatModel model = newModel();

		BedrockChatOptions options = BedrockChatOptions.builder()
			.requestParameters(
					Map.of("anthropic_version", "bedrock-2023-05-31", "output_config", Map.of("effort", "low")))
			.build();

		Prompt prompt = new Prompt(List.of(new UserMessage("Question?")), options);

		ConverseRequest request = model.createRequest(prompt);

		Document additionalModelRequestFields = request.additionalModelRequestFields();
		assertThat(additionalModelRequestFields.isMap()).isTrue();

		Document anthropicVersion = additionalModelRequestFields.asMap().get("anthropic_version");
		assertThat(anthropicVersion.asString()).isEqualTo("bedrock-2023-05-31");

		Document outputConfig = additionalModelRequestFields.asMap().get("output_config");
		assertThat(outputConfig.isMap()).isTrue();
		assertThat(outputConfig.asMap().get("effort").asString()).isEqualTo("low");
	}

	@Test
	void multiBlockSystemCachingPlacesCachePointBeforeLastSystemBlock() {
		BedrockProxyChatModel model = newModel();

		BedrockChatOptions options = BedrockChatOptions.builder()
			.cacheOptions(BedrockCacheOptions.builder()
				.strategy(BedrockCacheStrategy.SYSTEM_ONLY)
				.multiBlockSystemCaching(true)
				.build())
			.build();

		Prompt prompt = new Prompt(List.of(new SystemMessage("Static system instructions."),
				new SystemMessage("Dynamic RAG context."), new UserMessage("Question?")), options);

		ConverseRequest request = model.createRequest(prompt);
		List<SystemContentBlock> system = request.system();

		// Expect: [text(static), cachePoint, text(dynamic)]
		assertThat(system).hasSize(3);
		assertThat(system.get(0).text()).isEqualTo("Static system instructions.");
		assertThat(system.get(0).cachePoint()).isNull();
		assertThat(system.get(1).text()).isNull();
		assertThat(system.get(1).cachePoint()).isNotNull();
		assertThat(system.get(1).cachePoint().typeAsString()).isEqualTo("default");
		assertThat(system.get(2).text()).isEqualTo("Dynamic RAG context.");
		assertThat(system.get(2).cachePoint()).isNull();
	}

	@Test
	void multiBlockSystemCachingWithSingleMessageFallsBackToLastBlockPlacement() {
		BedrockProxyChatModel model = newModel();

		BedrockChatOptions options = BedrockChatOptions.builder()
			.cacheOptions(BedrockCacheOptions.builder()
				.strategy(BedrockCacheStrategy.SYSTEM_ONLY)
				.multiBlockSystemCaching(true)
				.build())
			.build();

		Prompt prompt = new Prompt(List.of(new SystemMessage("Only system message."), new UserMessage("Question?")),
				options);

		ConverseRequest request = model.createRequest(prompt);
		List<SystemContentBlock> system = request.system();

		// Single message: cache point goes after the only text block.
		assertThat(system).hasSize(2);
		assertThat(system.get(0).text()).isEqualTo("Only system message.");
		assertThat(system.get(1).cachePoint()).isNotNull();
	}

	@Test
	void multiBlockSystemCachingDisabledByDefaultPlacesCachePointAfterLastBlock() {
		BedrockProxyChatModel model = newModel();

		// multiBlockSystemCaching not set: defaults to false.
		BedrockChatOptions options = BedrockChatOptions.builder()
			.cacheOptions(BedrockCacheOptions.builder().strategy(BedrockCacheStrategy.SYSTEM_ONLY).build())
			.build();

		Prompt prompt = new Prompt(List.of(new SystemMessage("Static system instructions."),
				new SystemMessage("Dynamic RAG context."), new UserMessage("Question?")), options);

		ConverseRequest request = model.createRequest(prompt);
		List<SystemContentBlock> system = request.system();

		// Expect: [text(static), text(dynamic), cachePoint] - backward compatible.
		assertThat(system).hasSize(3);
		assertThat(system.get(0).text()).isEqualTo("Static system instructions.");
		assertThat(system.get(1).text()).isEqualTo("Dynamic RAG context.");
		assertThat(system.get(2).cachePoint()).isNotNull();
	}

	@Test
	void multiBlockSystemCachingHonorsSystemAndToolsStrategy() {
		BedrockProxyChatModel model = newModel();

		BedrockChatOptions options = BedrockChatOptions.builder()
			.cacheOptions(BedrockCacheOptions.builder()
				.strategy(BedrockCacheStrategy.SYSTEM_AND_TOOLS)
				.multiBlockSystemCaching(true)
				.build())
			.build();

		Prompt prompt = new Prompt(
				List.of(new SystemMessage("Static."), new SystemMessage("Dynamic."), new UserMessage("Question?")),
				options);

		ConverseRequest request = model.createRequest(prompt);
		List<SystemContentBlock> system = request.system();

		// SYSTEM_AND_TOOLS still places the system cache point between blocks.
		assertThat(system).hasSize(3);
		assertThat(system.get(0).text()).isEqualTo("Static.");
		assertThat(system.get(1).cachePoint()).isNotNull();
		assertThat(system.get(2).text()).isEqualTo("Dynamic.");
	}

	@Test
	void multiBlockSystemCachingHasNoEffectWhenStrategyIsNone() {
		BedrockProxyChatModel model = newModel();

		BedrockChatOptions options = BedrockChatOptions.builder()
			.cacheOptions(BedrockCacheOptions.builder()
				.strategy(BedrockCacheStrategy.NONE)
				.multiBlockSystemCaching(true)
				.build())
			.build();

		Prompt prompt = new Prompt(
				List.of(new SystemMessage("Static."), new SystemMessage("Dynamic."), new UserMessage("Question?")),
				options);

		ConverseRequest request = model.createRequest(prompt);
		List<SystemContentBlock> system = request.system();

		// No cache point added when caching is off.
		assertThat(system).hasSize(2);
		assertThat(system.get(0).cachePoint()).isNull();
		assertThat(system.get(1).cachePoint()).isNull();
	}

	@Test
	void shouldApplyCacheTtlOnCachePointBlock() {
		BedrockProxyChatModel model = newModel();

		BedrockChatOptions options = BedrockChatOptions.builder()
			.cacheOptions(BedrockCacheOptions.builder()
				.strategy(BedrockCacheStrategy.SYSTEM_ONLY)
				.ttl(BedrockCacheTtl.ONE_HOUR)
				.build())
			.build();

		Prompt prompt = new Prompt(List.of(new SystemMessage("Only system message."), new UserMessage("Question?")),
				options);

		ConverseRequest request = model.createRequest(prompt);
		List<SystemContentBlock> system = request.system();

		assertThat(system).hasSize(2);
		assertThat(system.get(0).text()).isEqualTo("Only system message.");
		assertThat(system.get(1).cachePoint()).isNotNull();
		assertThat(system.get(1).cachePoint().typeAsString()).isEqualTo("default");
		assertThat(system.get(1).cachePoint().ttlAsString()).isEqualTo("1h");
	}

	@Test
	void shouldApplyCacheTtlOnConversationHistoryCachePoint() {
		BedrockProxyChatModel model = newModel();

		BedrockChatOptions options = BedrockChatOptions.builder()
			.cacheOptions(BedrockCacheOptions.builder()
				.strategy(BedrockCacheStrategy.CONVERSATION_HISTORY)
				.ttl(BedrockCacheTtl.ONE_HOUR)
				.build())
			.build();

		Prompt prompt = new Prompt(List.of(new UserMessage("Question?")), options);

		ConverseRequest request = model.createRequest(prompt);
		List<Message> messages = request.messages();

		// Expect: single user message with [text("Question?"), cachePoint]
		assertThat(messages).hasSize(1);
		List<ContentBlock> contents = messages.get(0).content();
		assertThat(contents).hasSize(2);
		assertThat(contents.get(0).text()).isEqualTo("Question?");
		assertThat(contents.get(1).cachePoint()).isNotNull();
		assertThat(contents.get(1).cachePoint().typeAsString()).isEqualTo("default");
		assertThat(contents.get(1).cachePoint().ttlAsString()).isEqualTo("1h");
	}

	@Test
	void shouldApplyCacheTtlOnToolsCachePoint() {
		BedrockProxyChatModel model = newModel();

		ToolCallback toolCallback = FunctionToolCallback.builder("getCurrentWeather", (WeatherRequest req) -> "15.0°C")
			.description("Gets the weather in location")
			.inputType(WeatherRequest.class)
			.build();

		BedrockChatOptions options = BedrockChatOptions.builder()
			.toolCallbacks(toolCallback)
			.cacheOptions(BedrockCacheOptions.builder()
				.strategy(BedrockCacheStrategy.TOOLS_ONLY)
				.ttl(BedrockCacheTtl.ONE_HOUR)
				.build())
			.build();

		Prompt prompt = new Prompt(List.of(new UserMessage("Question?")), options);

		ConverseRequest request = model.createRequest(prompt);
		List<Tool> tools = request.toolConfig().tools();

		// Expect: [toolSpec, cachePoint]
		assertThat(tools).hasSize(2);
		assertThat(tools.get(0).toolSpec()).isNotNull();
		assertThat(tools.get(1).cachePoint()).isNotNull();
		assertThat(tools.get(1).cachePoint().typeAsString()).isEqualTo("default");
		assertThat(tools.get(1).cachePoint().ttlAsString()).isEqualTo("1h");
	}

	// -------------------------------------------------------------------------
	// Empty user message text (gh-6695)
	// -------------------------------------------------------------------------

	@Test
	void mediaOnlyUserMessageOmitsEmptyTextContentBlock() {
		BedrockProxyChatModel model = newModel();

		Prompt prompt = new Prompt(List.of(UserMessage.builder().text("").media(pngMedia()).build()),
				BedrockChatOptions.builder().build());

		List<ContentBlock> contents = model.createRequest(prompt).messages().get(0).content();

		assertThat(contents).hasSize(1);
		assertThat(contents.get(0).image()).isNotNull();
		assertThat(contents).noneMatch(content -> content.text() != null);
	}

	@Test
	void blankUserMessageTextOmitsEmptyTextContentBlock() {
		BedrockProxyChatModel model = newModel();

		Prompt prompt = new Prompt(List.of(UserMessage.builder().text("   \n\t").media(pngMedia()).build()),
				BedrockChatOptions.builder().build());

		List<ContentBlock> contents = model.createRequest(prompt).messages().get(0).content();

		assertThat(contents).hasSize(1);
		assertThat(contents.get(0).image()).isNotNull();
	}

	@Test
	void userMessageWithTextAndMediaKeepsBothContentBlocks() {
		BedrockProxyChatModel model = newModel();

		Prompt prompt = new Prompt(List.of(UserMessage.builder().text("Describe the image").media(pngMedia()).build()),
				BedrockChatOptions.builder().build());

		List<ContentBlock> contents = model.createRequest(prompt).messages().get(0).content();

		assertThat(contents).hasSize(2);
		assertThat(contents.get(0).text()).isEqualTo("Describe the image");
		assertThat(contents.get(1).image()).isNotNull();
	}

	// -------------------------------------------------------------------------
	// User message parts are sent in order (gh-7012)
	// -------------------------------------------------------------------------

	@Test
	void userMessagePartsAreSentInPartOrder() {
		BedrockProxyChatModel model = newModel();

		UserMessage userMessage = UserMessage.builder()
			.part(MediaPart.of(pngMedia()))
			.part(TextPart.of("What is in the first image?"))
			.part(MediaPart.of(pngMedia()))
			.part(TextPart.of("And in the second one?"))
			.build();

		List<ContentBlock> contents = model
			.createRequest(new Prompt(List.of(userMessage), BedrockChatOptions.builder().build()))
			.messages()
			.get(0)
			.content();

		assertThat(contents).extracting(ContentBlock::type)
			.containsExactly(ContentBlock.Type.IMAGE, ContentBlock.Type.TEXT, ContentBlock.Type.IMAGE,
					ContentBlock.Type.TEXT);
		assertThat(contents.get(1).text()).isEqualTo("What is in the first image?");
		assertThat(contents.get(3).text()).isEqualTo("And in the second one?");
	}

	@Test
	void blankUserTextPartIsSkippedAndCachePointStaysLast() {
		BedrockProxyChatModel model = newModel();

		UserMessage userMessage = UserMessage.builder()
			.part(TextPart.of("Describe the image"))
			.part(TextPart.of("  "))
			.part(MediaPart.of(pngMedia()))
			.build();
		BedrockChatOptions options = BedrockChatOptions.builder()
			.cacheOptions(BedrockCacheOptions.builder().strategy(BedrockCacheStrategy.CONVERSATION_HISTORY).build())
			.build();

		List<ContentBlock> contents = model.createRequest(new Prompt(List.of(userMessage), options))
			.messages()
			.get(0)
			.content();

		assertThat(contents).extracting(ContentBlock::type)
			.containsExactly(ContentBlock.Type.TEXT, ContentBlock.Type.IMAGE, ContentBlock.Type.CACHE_POINT);
	}

	// -------------------------------------------------------------------------
	// Response content blocks become ordered parts of a single generation
	// -------------------------------------------------------------------------

	@Test
	void responseContentBlocksBecomeOrderedPartsOfASingleGeneration() {
		given(this.syncClient.converse(isA(ConverseRequest.class)))
			.willReturn(converseResponse(StopReason.TOOL_USE, signedReasoningBlock(), redactedReasoningBlock(),
					ContentBlock.fromText("Let me check."), toolUseBlock()));

		ChatResponse chatResponse = newModel().call(new Prompt("Weather in Paris?"));

		assertThat(chatResponse.getResults()).hasSize(1);
		AssistantMessage output = chatResponse.getResult().getOutput();
		assertThat(output.getParts()).containsExactly(SIGNED_REASONING, REDACTED_REASONING,
				TextPart.of("Let me check."), ToolCallPart.of(WEATHER_CALL));
		assertThat(output.getReasoning()).containsExactly(SIGNED_REASONING, REDACTED_REASONING);
		assertThat(output.getText()).isEqualTo("Let me check.");
		assertThat(output.getToolCalls()).containsExactly(WEATHER_CALL);
		assertThat(chatResponse.hasToolCalls()).isTrue();
		assertThat(chatResponse.getResult().getMetadata().getFinishReason()).isEqualTo("tool_use");
	}

	@Test
	void toolUseOnlyResponseHasAnEmptyText() {
		given(this.syncClient.converse(isA(ConverseRequest.class)))
			.willReturn(converseResponse(StopReason.TOOL_USE, toolUseBlock()));

		ChatResponse chatResponse = newModel().call(new Prompt("Weather in Paris?"));

		assertThat(chatResponse.getResults()).hasSize(1);
		AssistantMessage output = chatResponse.getResult().getOutput();
		assertThat(output.getParts()).containsExactly(ToolCallPart.of(WEATHER_CALL));
		assertThat(output.getText()).isEmpty();
		assertThat(chatResponse.hasToolCalls()).isTrue();
	}

	@Test
	void unsignedReasoningIsAReasoningPartMarkedAsProducedByBedrock() {
		// Some models, such as OpenAI gpt-oss, return reasoning text without a signature.
		ContentBlock unsignedReasoning = ContentBlock.fromReasoningContent(ReasoningContentBlock.builder()
			.reasoningText(ReasoningTextBlock.builder().text(REASONING_TEXT).build())
			.build());
		given(this.syncClient.converse(isA(ConverseRequest.class)))
			.willReturn(converseResponse(StopReason.END_TURN, unsignedReasoning, ContentBlock.fromText("4")));

		AssistantMessage output = newModel().call(new Prompt("What is 2+2?")).getResult().getOutput();

		assertThat(output.getParts()).containsExactly(UNSIGNED_REASONING, TextPart.of("4"));
		assertThat(output.getText()).isEqualTo("4");
	}

	// -------------------------------------------------------------------------
	// Assistant messages are replayed from their parts, in order
	// -------------------------------------------------------------------------

	@Test
	void assistantPartsAreReplayedInOrder() {
		AssistantMessage assistant = AssistantMessage.builder()
			.part(SIGNED_REASONING)
			.part(REDACTED_REASONING)
			.part(TextPart.of("Let me check."))
			.part(ToolCallPart.of(WEATHER_CALL))
			.build();

		List<ContentBlock> content = replayedAssistantContent(assistant);

		assertThat(content).extracting(ContentBlock::type)
			.containsExactly(ContentBlock.Type.REASONING_CONTENT, ContentBlock.Type.REASONING_CONTENT,
					ContentBlock.Type.TEXT, ContentBlock.Type.TOOL_USE);
		assertThat(content.get(0).reasoningContent().reasoningText().text()).isEqualTo(REASONING_TEXT);
		assertThat(content.get(0).reasoningContent().reasoningText().signature()).isEqualTo(SIGNATURE);
		assertThat(content.get(1).reasoningContent().redactedContent().asByteArray()).isEqualTo(REDACTED);
		assertThat(content.get(2).text()).isEqualTo("Let me check.");
		assertThat(content.get(3).toolUse().toolUseId()).isEqualTo("tooluse_123");
		assertThat(content.get(3).toolUse().input().asMap().get("location").asString()).isEqualTo("Paris");
	}

	@Test
	void unsignedReasoningProducedByBedrockIsReplayedWithoutSignature() {
		AssistantMessage assistant = AssistantMessage.builder()
			.part(UNSIGNED_REASONING)
			.part(ToolCallPart.of(WEATHER_CALL))
			.build();

		List<ContentBlock> content = replayedAssistantContent(assistant);

		assertThat(content).extracting(ContentBlock::type)
			.containsExactly(ContentBlock.Type.REASONING_CONTENT, ContentBlock.Type.TOOL_USE);
		assertThat(content.get(0).reasoningContent().reasoningText().text()).isEqualTo(REASONING_TEXT);
		assertThat(content.get(0).reasoningContent().reasoningText().signature()).isNull();
	}

	@Test
	void reasoningNotProducedByBedrockIsNotReplayed() {
		AssistantMessage assistant = AssistantMessage.builder()
			.part(new ReasoningPart("anthropic thoughts", null, new OpaquePayload("anthropic", "signature", "sig"),
					Map.of()))
			.part(ReasoningPart.of("unsigned thoughts"))
			.part(new ReasoningPart("unknown kind", null,
					new OpaquePayload(ConverseApiUtils.BEDROCK_PROVIDER, "future_kind", "data"), Map.of()))
			.part(TextPart.of(""))
			.part(ToolCallPart.of(WEATHER_CALL))
			.build();

		List<ContentBlock> content = replayedAssistantContent(assistant);

		assertThat(content).extracting(ContentBlock::type).containsExactly(ContentBlock.Type.TOOL_USE);
	}

	@Test
	void toolResultPartInAnAssistantMessageIsRejected() {
		AssistantMessage assistant = AssistantMessage.builder()
			.part(ToolResultPart.of(new ToolResponseMessage.ToolResponse("tooluse_123", "getCurrentWeather", "15°C")))
			.build();

		assertThatIllegalArgumentException().isThrownBy(() -> replayedAssistantContent(assistant))
			.withMessageContaining("tool response message");
	}

	// -------------------------------------------------------------------------
	// Streaming emits indexed parts
	// -------------------------------------------------------------------------

	@Test
	void streamingEmitsIndexedPartsAndBuffersToolUseUntilTheFinalChunk() {
		givenStream(reasoningDelta(0, "Think "), reasoningDelta(0, "hard."), signatureDelta(0, SIGNATURE), blockStop(0),
				textDelta(1, "Let me "), textDelta(1, "check."), blockStop(1),
				ConverseStreamOutput.contentBlockStartBuilder()
					.contentBlockIndex(2)
					.start(ContentBlockStart.builder()
						.toolUse(ToolUseBlockStart.builder().toolUseId("tooluse_123").name("getCurrentWeather").build())
						.build())
					.build(),
				toolUseDelta(2, "{\"location\":"), toolUseDelta(2, "\"Paris\"}"), blockStop(2),
				messageStop(StopReason.TOOL_USE), usageMetadata());

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		List<ChatResponse> chunks = new MessageAggregator()
			.aggregate(newModel().stream(new Prompt("Weather in Paris?")), aggregatedRef::set)
			.collectList()
			.block();

		// 2 reasoning deltas, the signature on block stop, 2 text deltas and the final
		// chunk; the tool use input is buffered until the final chunk
		assertThat(chunks).hasSize(6);
		assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.getMetadata().getId()).isEqualTo("req-1"));
		assertThat(chunks.get(0).getResult().getOutput().getParts())
			.containsExactly(StreamingParts.partial(new ReasoningPart("Think ", null, null, BEDROCK_ATTRIBUTES), 0));
		assertThat(chunks.get(0).getResult().getOutput().getText()).isEmpty();
		assertThat(chunks.get(2).getResult().getOutput().getParts()).containsExactly(
				StreamingParts.partial(new ReasoningPart("", null, SIGNATURE_PAYLOAD, BEDROCK_ATTRIBUTES), 0));
		assertThat(chunks.get(3).getResult().getOutput().getParts())
			.containsExactly(StreamingParts.partial(TextPart.of("Let me "), 1));
		assertThat(chunks.get(3).getResult().getOutput().getText()).isEqualTo("Let me ");
		assertThat(chunks.subList(0, 5)).allSatisfy(chunk -> assertThat(chunk.hasToolCalls()).isFalse());

		AssistantMessage last = chunks.get(5).getResult().getOutput();
		assertThat(last.getToolCalls()).containsExactly(WEATHER_CALL);
		MessagePart toolCallPart = last.getParts().get(0);
		assertThat(StreamingParts.partIndex(toolCallPart)).isEqualTo(2);
		assertThat(StreamingParts.isPartial(toolCallPart)).isFalse();
		assertThat(last.getText()).isEmpty();
		assertThat(chunks.get(5).getResult().getMetadata().getFinishReason()).isEqualTo("tool_use");

		AssistantMessage aggregated = aggregatedRef.get().getResult().getOutput();
		assertThat(aggregated.getParts()).containsExactly(
				new ReasoningPart("Think hard.", null, SIGNATURE_PAYLOAD, BEDROCK_ATTRIBUTES),
				TextPart.of("Let me check."), ToolCallPart.of(WEATHER_CALL));

		// The aggregated turn replays with the signed reasoning before the tool use.
		List<ContentBlock> content = replayedAssistantContent(aggregated);
		assertThat(content).extracting(ContentBlock::type)
			.containsExactly(ContentBlock.Type.REASONING_CONTENT, ContentBlock.Type.TEXT, ContentBlock.Type.TOOL_USE);
		assertThat(content.get(0).reasoningContent().reasoningText().text()).isEqualTo("Think hard.");
		assertThat(content.get(0).reasoningContent().reasoningText().signature()).isEqualTo(SIGNATURE);
	}

	@Test
	void streamingUnsignedReasoningAggregatesToAPartMarkedAsProducedByBedrock() {
		givenStream(reasoningDelta(0, "The user wants "), reasoningDelta(0, "the weather in Paris."), blockStop(0),
				textDelta(1, "15°C"), blockStop(1), messageStop(StopReason.END_TURN), usageMetadata());

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		new MessageAggregator().aggregate(newModel().stream(new Prompt("Weather?")), aggregatedRef::set)
			.collectList()
			.block();

		assertThat(aggregatedRef.get().getResult().getOutput().getParts()).containsExactly(UNSIGNED_REASONING,
				TextPart.of("15°C"));
	}

	@Test
	void streamingSignatureSplitAcrossDeltasIsJoinedOnBlockStop() {
		givenStream(reasoningDelta(0, "Think hard."), signatureDelta(0, "first-"), signatureDelta(0, "second"),
				blockStop(0), textDelta(1, "15°C"), blockStop(1), messageStop(StopReason.END_TURN), usageMetadata());

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		List<ChatResponse> chunks = new MessageAggregator()
			.aggregate(newModel().stream(new Prompt("Weather?")), aggregatedRef::set)
			.collectList()
			.block();

		// The reasoning delta, the joined signature, the text delta and the final chunk
		assertThat(chunks).hasSize(4);
		AssistantMessage aggregated = aggregatedRef.get().getResult().getOutput();
		OpaquePayload joined = new OpaquePayload(ConverseApiUtils.BEDROCK_PROVIDER, ConverseApiUtils.PAYLOAD_SIGNATURE,
				"first-second");
		assertThat(aggregated.getParts())
			.containsExactly(new ReasoningPart("Think hard.", null, joined, BEDROCK_ATTRIBUTES), TextPart.of("15°C"));

		List<ContentBlock> content = replayedAssistantContent(aggregated);
		assertThat(content.get(0).reasoningContent().reasoningText().text()).isEqualTo("Think hard.");
		assertThat(content.get(0).reasoningContent().reasoningText().signature()).isEqualTo("first-second");
	}

	@Test
	void streamingSignatureWithoutReasoningTextAggregatesToAReplayablePart() {
		// A block with only a signature, as returned when the reasoning display is
		// omitted
		givenStream(signatureDelta(0, SIGNATURE), blockStop(0), textDelta(1, "15°C"), blockStop(1),
				messageStop(StopReason.END_TURN), usageMetadata());

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		new MessageAggregator().aggregate(newModel().stream(new Prompt("Weather?")), aggregatedRef::set)
			.collectList()
			.block();

		AssistantMessage aggregated = aggregatedRef.get().getResult().getOutput();
		assertThat(aggregated.getParts())
			.containsExactly(new ReasoningPart("", null, SIGNATURE_PAYLOAD, BEDROCK_ATTRIBUTES), TextPart.of("15°C"));

		List<ContentBlock> content = replayedAssistantContent(aggregated);
		assertThat(content.get(0).reasoningContent().reasoningText().text()).isEmpty();
		assertThat(content.get(0).reasoningContent().reasoningText().signature()).isEqualTo(SIGNATURE);
	}

	@Test
	void streamingSignatureWithoutABlockStopIsFlushedOnTheFinalChunk() {
		givenStream(reasoningDelta(0, "Think hard."), signatureDelta(0, SIGNATURE), messageStop(StopReason.END_TURN),
				usageMetadata());

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		List<ChatResponse> chunks = new MessageAggregator()
			.aggregate(newModel().stream(new Prompt("Weather?")), aggregatedRef::set)
			.collectList()
			.block();

		assertThat(chunks).hasSize(2);
		assertThat(aggregatedRef.get().getResult().getOutput().getParts())
			.containsExactly(new ReasoningPart("Think hard.", null, SIGNATURE_PAYLOAD, BEDROCK_ATTRIBUTES));
	}

	@Test
	void streamingRedactedReasoningIsBufferedUntilItsBlockStops() {
		byte[] first = "redacted-".getBytes(StandardCharsets.UTF_8);
		byte[] second = "reasoning-bytes".getBytes(StandardCharsets.UTF_8);
		givenStream(redactedDelta(0, first), redactedDelta(0, second), blockStop(0), textDelta(1, "15°C"), blockStop(1),
				messageStop(StopReason.END_TURN), usageMetadata());

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		List<ChatResponse> chunks = new MessageAggregator()
			.aggregate(newModel().stream(new Prompt("Weather?")), aggregatedRef::set)
			.collectList()
			.block();

		// The redacted block, the text delta and the final chunk
		assertThat(chunks).hasSize(3);
		// first + second is REDACTED, emitted as one complete part
		assertThat(chunks.get(0).getResult().getOutput().getParts())
			.containsExactly(StreamingParts.complete(REDACTED_REASONING, 0));
		assertThat(aggregatedRef.get().getResult().getOutput().getParts()).containsExactly(REDACTED_REASONING,
				TextPart.of("15°C"));
		assertThat(chunks.get(2).getResult().getOutput().getText()).isEmpty();
	}

	@Test
	void streamingRedactedReasoningWithoutABlockStopIsFlushedOnTheFinalChunk() {
		givenStream(redactedDelta(0, REDACTED), messageStop(StopReason.END_TURN), usageMetadata());

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		List<ChatResponse> chunks = new MessageAggregator()
			.aggregate(newModel().stream(new Prompt("Weather?")), aggregatedRef::set)
			.collectList()
			.block();

		assertThat(chunks).hasSize(1);
		assertThat(aggregatedRef.get().getResult().getOutput().getParts()).containsExactly(REDACTED_REASONING);
	}

	private List<ContentBlock> replayedAssistantContent(AssistantMessage assistant) {
		Prompt prompt = new Prompt(List.of(new UserMessage("Weather in Paris?"), assistant,
				ToolResponseMessage.builder()
					.responses(
							List.of(new ToolResponseMessage.ToolResponse("tooluse_123", "getCurrentWeather", "15°C")))
					.build()),
				BedrockChatOptions.builder().build());
		return newModel().createRequest(prompt)
			.messages()
			.stream()
			.filter(message -> message.role() == ConversationRole.ASSISTANT)
			.findFirst()
			.orElseThrow()
			.content();
	}

	private void givenStream(ConverseStreamOutput... events) {
		given(this.asyncClient.converseStream(any(ConverseStreamRequest.class),
				any(ConverseStreamResponseHandler.class)))
			.willAnswer(invocation -> {
				ConverseStreamResponseHandler handler = invocation.getArgument(1);
				// responseMetadata(...) returns the supertype builder, hence the cast
				handler.responseReceived((ConverseStreamResponse) ConverseStreamResponse.builder()
					.responseMetadata(BedrockRuntimeResponseMetadata
						.create(DefaultAwsResponseMetadata.create(Map.of("AWS_REQUEST_ID", "req-1"))))
					.build());
				handler.onEventStream(SdkPublisher.adapt(Flux.just(events)));
				handler.complete();
				return CompletableFuture.completedFuture(null);
			});
	}

	private static ConverseResponse converseResponse(StopReason stopReason, ContentBlock... content) {
		return ConverseResponse.builder()
			.output(ConverseOutput.builder()
				.message(Message.builder().role(ConversationRole.ASSISTANT).content(content).build())
				.build())
			.usage(TokenUsage.builder().inputTokens(20).outputTokens(30).totalTokens(50).build())
			.stopReason(stopReason)
			.build();
	}

	private static ContentBlock signedReasoningBlock() {
		return ContentBlock.fromReasoningContent(ReasoningContentBlock.builder()
			.reasoningText(ReasoningTextBlock.builder().text(REASONING_TEXT).signature(SIGNATURE).build())
			.build());
	}

	private static ContentBlock redactedReasoningBlock() {
		return ContentBlock.fromReasoningContent(
				ReasoningContentBlock.builder().redactedContent(SdkBytes.fromByteArray(REDACTED)).build());
	}

	private static ContentBlock toolUseBlock() {
		return ContentBlock.fromToolUse(ToolUseBlock.builder()
			.toolUseId("tooluse_123")
			.name("getCurrentWeather")
			.input(Document.mapBuilder().putString("location", "Paris").build())
			.build());
	}

	private static ConverseStreamOutput reasoningDelta(int index, String text) {
		return ConverseStreamOutput.contentBlockDeltaBuilder()
			.contentBlockIndex(index)
			.delta(ContentBlockDelta.builder()
				.reasoningContent(ReasoningContentBlockDelta.builder().text(text).build())
				.build())
			.build();
	}

	private static ConverseStreamOutput signatureDelta(int index, String signature) {
		return ConverseStreamOutput.contentBlockDeltaBuilder()
			.contentBlockIndex(index)
			.delta(ContentBlockDelta.builder()
				.reasoningContent(ReasoningContentBlockDelta.builder().signature(signature).build())
				.build())
			.build();
	}

	private static ConverseStreamOutput redactedDelta(int index, byte[] redactedContent) {
		return ConverseStreamOutput.contentBlockDeltaBuilder()
			.contentBlockIndex(index)
			.delta(ContentBlockDelta.builder()
				.reasoningContent(ReasoningContentBlockDelta.builder()
					.redactedContent(SdkBytes.fromByteArray(redactedContent))
					.build())
				.build())
			.build();
	}

	private static ConverseStreamOutput textDelta(int index, String text) {
		return ConverseStreamOutput.contentBlockDeltaBuilder()
			.contentBlockIndex(index)
			.delta(ContentBlockDelta.builder().text(text).build())
			.build();
	}

	private static ConverseStreamOutput toolUseDelta(int index, String input) {
		return ConverseStreamOutput.contentBlockDeltaBuilder()
			.contentBlockIndex(index)
			.delta(ContentBlockDelta.builder().toolUse(ToolUseBlockDelta.builder().input(input).build()).build())
			.build();
	}

	private static ConverseStreamOutput blockStop(int index) {
		return ConverseStreamOutput.contentBlockStopBuilder().contentBlockIndex(index).build();
	}

	private static ConverseStreamOutput messageStop(StopReason stopReason) {
		return ConverseStreamOutput.messageStopBuilder().stopReason(stopReason).build();
	}

	private static ConverseStreamOutput usageMetadata() {
		return ConverseStreamOutput.metadataBuilder()
			.usage(TokenUsage.builder().inputTokens(10).outputTokens(20).totalTokens(30).build())
			.build();
	}

	private static Media pngMedia() {
		return Media.builder()
			.mimeType(MimeType.valueOf("image/png"))
			.data(new byte[] { (byte) 0x89, 'P', 'N', 'G' })
			.build();
	}

	public record WeatherRequest(String location, String unit) {
	}

}
