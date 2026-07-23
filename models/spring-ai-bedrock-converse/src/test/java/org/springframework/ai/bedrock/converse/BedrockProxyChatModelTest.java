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
import java.util.List;
import java.util.Map;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;

import org.springframework.ai.bedrock.converse.api.BedrockCacheOptions;
import org.springframework.ai.bedrock.converse.api.BedrockCacheStrategy;
import org.springframework.ai.bedrock.converse.api.BedrockCacheTtl;
import org.springframework.ai.bedrock.converse.api.MediaFetcher;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.util.MimeType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BedrockProxyChatModelTest {

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

	public record WeatherRequest(String location, String unit) {
	}

}
