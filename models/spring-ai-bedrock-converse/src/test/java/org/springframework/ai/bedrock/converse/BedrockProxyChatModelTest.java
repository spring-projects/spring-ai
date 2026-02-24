/*
 * Copyright 2023-2026 the original author or authors.
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BedrockProxyChatModelTest {

	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	private DefaultAwsRegionProviderChain.Builder awsRegionProviderBuilder;

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

}
