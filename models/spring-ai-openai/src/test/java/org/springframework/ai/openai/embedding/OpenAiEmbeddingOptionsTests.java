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

package org.springframework.ai.openai.embedding;

import java.util.List;
import java.util.Map;

import com.openai.models.embeddings.EmbeddingCreateParams;
import org.junit.jupiter.api.Test;

import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OpenAiEmbeddingOptions}.
 *
 * @author guan xu
 */
class OpenAiEmbeddingOptionsTests {

	@Test
	void defaultEncodingFormatIsNull() {
		OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder().model("test-model").build();

		EmbeddingCreateParams createParams = options.toOpenAiCreateParams(List.of("test input"));

		assertThat(options.getEncodingFormat()).isNull();
		assertThat(createParams.encodingFormat()).contains(EmbeddingCreateParams.EncodingFormat.BASE64);
	}

	@Test
	void encodingFormatCanBeConfigured() {
		OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
			.model("test-model")
			.encodingFormat(OpenAiEmbeddingOptions.EncodingFormat.FLOAT)
			.build();

		EmbeddingCreateParams createParams = options.toOpenAiCreateParams(List.of("test input"));

		assertThat(createParams.encodingFormat()).contains(EmbeddingCreateParams.EncodingFormat.FLOAT);
	}

	@Test
	void encodingFormatIsCopiedAndMerged() {
		OpenAiEmbeddingOptions source = OpenAiEmbeddingOptions.builder()
			.model("test-model")
			.encodingFormat(OpenAiEmbeddingOptions.EncodingFormat.FLOAT)
			.build();

		OpenAiEmbeddingOptions copied = OpenAiEmbeddingOptions.builder().from(source).build();
		OpenAiEmbeddingOptions merged = OpenAiEmbeddingOptions.builder().model("other-model").merge(source).build();

		assertThat(copied.getEncodingFormat()).isEqualTo(OpenAiEmbeddingOptions.EncodingFormat.FLOAT);
		assertThat(merged.getEncodingFormat()).isEqualTo(OpenAiEmbeddingOptions.EncodingFormat.FLOAT);
	}

	@Test
	void genericEmbeddingOptionsAreMerged() {
		EmbeddingOptions source = EmbeddingOptions.builder().model("generic-model").dimensions(1024).build();

		OpenAiEmbeddingOptions merged = OpenAiEmbeddingOptions.builder().merge(source).build();

		assertThat(merged.getModel()).isEqualTo("generic-model");
		assertThat(merged.getDimensions()).isEqualTo(1024);
	}

	@Test
	void testOptionsBuilderMergeCustomHeaders() {
		OpenAiEmbeddingOptions defaultOptions = OpenAiEmbeddingOptions.builder()
			.customHeaders(Map.of("default-header", "default-value"))
			.build();

		OpenAiEmbeddingOptions requestOptions = OpenAiEmbeddingOptions.builder()
			.customHeaders(Map.of("merged-header1", "merged-value1", "merged-header2", "merged-value2"))
			.build();

		OpenAiEmbeddingOptions mergedOptions = OpenAiEmbeddingOptions.builder()
			.from(defaultOptions)
			.merge(requestOptions)
			.build();
		assertThat(mergedOptions.getCustomHeaders()).containsEntry("default-header", "default-value")
			.containsEntry("merged-header1", "merged-value1")
			.containsEntry("merged-header2", "merged-value2");
	}

	@Test
	void extraBodyIsPassedAsAdditionalBodyProperties() {
		OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
			.model("test-model")
			.extraBody(Map.of("top_k", 40, "repetition_penalty", 1.2))
			.build();

		EmbeddingCreateParams createParams = options.toOpenAiCreateParams(List.of("test input"));

		assertThat(options.getExtraBody()).containsEntry("top_k", 40).containsEntry("repetition_penalty", 1.2);
		assertThat(createParams._additionalBodyProperties()).containsKeys("top_k", "repetition_penalty");
		assertThat(createParams._additionalBodyProperties().get("top_k").asNumber().get()).isEqualTo(40);
		assertThat(createParams._additionalBodyProperties().get("repetition_penalty").asNumber().get()).isEqualTo(1.2);
	}

	@Test
	void extraBodyIsCopiedAndMerged() {
		OpenAiEmbeddingOptions source = OpenAiEmbeddingOptions.builder()
			.model("test-model")
			.extraBody(Map.of("top_k", 40))
			.build();

		OpenAiEmbeddingOptions copied = OpenAiEmbeddingOptions.builder().from(source).build();
		OpenAiEmbeddingOptions merged = OpenAiEmbeddingOptions.builder()
			.extraBody(Map.of("repetition_penalty", 1.2))
			.merge(source)
			.build();

		assertThat(copied.getExtraBody()).containsEntry("top_k", 40);
		assertThat(merged.getExtraBody()).containsEntry("top_k", 40).containsEntry("repetition_penalty", 1.2);
	}

	@Test
	void emptyExtraBodyIsNotAddedToCreateParams() {
		OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder().model("test-model").build();

		EmbeddingCreateParams createParams = options.toOpenAiCreateParams(List.of("test input"));

		assertThat(options.getExtraBody()).isNull();
		assertThat(createParams._additionalBodyProperties()).isEmpty();
	}

}
