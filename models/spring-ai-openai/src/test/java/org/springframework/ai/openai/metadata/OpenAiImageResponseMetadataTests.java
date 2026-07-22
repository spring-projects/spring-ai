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

package org.springframework.ai.openai.metadata;

import java.util.Objects;

import com.openai.models.images.ImagesResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiImageResponseMetadataTests {

	@Test
	void mapsAllUsageFields() {
		ImagesResponse.Usage usage = ImagesResponse.Usage.builder()
			.inputTokens(150L)
			.outputTokens(200L)
			.totalTokens(350L)
			.inputTokensDetails(inputTokensDetails(100L, 50L))
			.outputTokensDetails(outputTokensDetails(180L, 20L))
			.build();
		ImagesResponse imagesResponse = ImagesResponse.builder().created(123L).usage(usage).build();

		OpenAiImageResponseMetadata metadata = OpenAiImageResponseMetadata.from(imagesResponse);
		OpenAiImageUsage imageUsage = Objects.requireNonNull(metadata.getUsage());

		assertThat(metadata.getCreated()).isEqualTo(123L);
		assertThat(imageUsage.getInputTokens()).isEqualTo(150L);
		assertThat(imageUsage.getOutputTokens()).isEqualTo(200L);
		assertThat(imageUsage.getTotalTokens()).isEqualTo(350L);
		assertThat(imageUsage.getInputTextTokens()).isEqualTo(100L);
		assertThat(imageUsage.getInputImageTokens()).isEqualTo(50L);
		assertThat(imageUsage.getOutputTextTokens()).isEqualTo(180L);
		assertThat(imageUsage.getOutputImageTokens()).isEqualTo(20L);
	}

	@Test
	void handlesMissingUsage() {
		ImagesResponse imagesResponse = ImagesResponse.builder().created(123L).build();

		OpenAiImageResponseMetadata metadata = OpenAiImageResponseMetadata.from(imagesResponse);

		assertThat(metadata.getCreated()).isEqualTo(123L);
		assertThat(metadata.getUsage()).isNull();
	}

	@Test
	void handlesMissingOutputTokenDetails() {
		ImagesResponse.Usage usage = ImagesResponse.Usage.builder()
			.inputTokens(75L)
			.outputTokens(25L)
			.totalTokens(100L)
			.inputTokensDetails(inputTokensDetails(60L, 15L))
			.build();
		ImagesResponse imagesResponse = ImagesResponse.builder().created(456L).usage(usage).build();

		OpenAiImageUsage imageUsage = Objects
			.requireNonNull(OpenAiImageResponseMetadata.from(imagesResponse).getUsage());

		assertThat(imageUsage.getInputTokens()).isEqualTo(75L);
		assertThat(imageUsage.getOutputTokens()).isEqualTo(25L);
		assertThat(imageUsage.getTotalTokens()).isEqualTo(100L);
		assertThat(imageUsage.getInputTextTokens()).isEqualTo(60L);
		assertThat(imageUsage.getInputImageTokens()).isEqualTo(15L);
		assertThat(imageUsage.getOutputTextTokens()).isNull();
		assertThat(imageUsage.getOutputImageTokens()).isNull();
	}

	@Test
	void preservesLegacyConstructorBehavior() {
		OpenAiImageResponseMetadata metadata = new OpenAiImageResponseMetadata(789L);
		OpenAiImageResponseMetadata sameMetadata = new OpenAiImageResponseMetadata(789L);

		assertThat(metadata.getCreated()).isEqualTo(789L);
		assertThat(metadata.getUsage()).isNull();
		assertThat(metadata).isEqualTo(sameMetadata).hasSameHashCodeAs(sameMetadata);
		assertThat(metadata.toString()).isEqualTo("OpenAiImageResponseMetadata{created=789}");
	}

	@Test
	void includesUsageInValueSemantics() {
		ImagesResponse imagesResponse = ImagesResponse.builder()
			.created(123L)
			.usage(ImagesResponse.Usage.builder()
				.inputTokens(75L)
				.outputTokens(25L)
				.totalTokens(100L)
				.inputTokensDetails(inputTokensDetails(60L, 15L))
				.build())
			.build();

		OpenAiImageResponseMetadata metadata = OpenAiImageResponseMetadata.from(imagesResponse);
		OpenAiImageResponseMetadata sameMetadata = OpenAiImageResponseMetadata.from(imagesResponse);
		OpenAiImageResponseMetadata differentCreatedMetadata = OpenAiImageResponseMetadata
			.from(imagesResponse.toBuilder().created(124L).build());
		OpenAiImageResponseMetadata legacyMetadata = new OpenAiImageResponseMetadata(123L);

		assertThat(metadata).isEqualTo(metadata)
			.isEqualTo(sameMetadata)
			.hasSameHashCodeAs(sameMetadata)
			.isNotEqualTo(differentCreatedMetadata)
			.isNotEqualTo(legacyMetadata)
			.isNotEqualTo("metadata");
		assertThat(metadata.toString()).contains("created=123", "usage=OpenAiImageUsage{", "inputTokens=75");
	}

	@Test
	void imageUsageIncludesEveryFieldInValueSemantics() {
		OpenAiImageUsage usage = imageUsage(1L, 2L, 3L, 4L, 5L, 6L, 7L);
		OpenAiImageUsage sameUsage = imageUsage(1L, 2L, 3L, 4L, 5L, 6L, 7L);

		assertThat(usage).isEqualTo(usage)
			.isEqualTo(sameUsage)
			.hasSameHashCodeAs(sameUsage)
			.isNotEqualTo(imageUsage(8L, 2L, 3L, 4L, 5L, 6L, 7L))
			.isNotEqualTo(imageUsage(1L, 8L, 3L, 4L, 5L, 6L, 7L))
			.isNotEqualTo(imageUsage(1L, 2L, 8L, 4L, 5L, 6L, 7L))
			.isNotEqualTo(imageUsage(1L, 2L, 3L, 8L, 5L, 6L, 7L))
			.isNotEqualTo(imageUsage(1L, 2L, 3L, 4L, 8L, 6L, 7L))
			.isNotEqualTo(imageUsage(1L, 2L, 3L, 4L, 5L, 8L, 7L))
			.isNotEqualTo(imageUsage(1L, 2L, 3L, 4L, 5L, 6L, 8L))
			.isNotEqualTo("usage");
	}

	private static ImagesResponse.Usage.InputTokensDetails inputTokensDetails(long textTokens, long imageTokens) {
		return ImagesResponse.Usage.InputTokensDetails.builder()
			.textTokens(textTokens)
			.imageTokens(imageTokens)
			.build();
	}

	private static ImagesResponse.Usage.OutputTokensDetails outputTokensDetails(long textTokens, long imageTokens) {
		return ImagesResponse.Usage.OutputTokensDetails.builder()
			.textTokens(textTokens)
			.imageTokens(imageTokens)
			.build();
	}

	private static OpenAiImageUsage imageUsage(long inputTokens, long outputTokens, long totalTokens,
			long inputTextTokens, long inputImageTokens, Long outputTextTokens, Long outputImageTokens) {
		return new OpenAiImageUsage(inputTokens, outputTokens, totalTokens, inputTextTokens, inputImageTokens,
				outputTextTokens, outputImageTokens);
	}

}
