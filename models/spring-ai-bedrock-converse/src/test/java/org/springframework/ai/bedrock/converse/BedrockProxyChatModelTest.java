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

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

import org.springframework.ai.bedrock.converse.api.MediaFetcher;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
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
				ObservationRegistry.NOOP, ToolCallingManager.builder().build(),
				new DefaultToolExecutionEligibilityPredicate());
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
				new DefaultToolExecutionEligibilityPredicate(), new MediaFetcher(java.util.Set.of("trusted-cdn.com")));
		Media media = Media.builder().mimeType(MimeType.valueOf("image/png")).data("http://evil.com/image.png").build();

		assertThatThrownBy(() -> model.mapMediaToContentBlock(media)).isInstanceOf(RuntimeException.class)
			.cause()
			.isInstanceOf(SecurityException.class)
			.hasMessageContaining("evil.com");
	}

}
