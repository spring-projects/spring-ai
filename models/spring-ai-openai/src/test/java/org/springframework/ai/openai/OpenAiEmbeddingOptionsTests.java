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

package org.springframework.ai.openai;

import java.util.List;

import com.openai.models.embeddings.EmbeddingCreateParams;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
			.encodingFormat("base64")
			.build();

		EmbeddingCreateParams createParams = options.toOpenAiCreateParams(List.of("test input"));

		assertThat(createParams.encodingFormat()).contains(EmbeddingCreateParams.EncodingFormat.BASE64);
	}

	@Test
	void encodingFormatIsCopiedAndMerged() {
		OpenAiEmbeddingOptions source = OpenAiEmbeddingOptions.builder()
			.model("test-model")
			.encodingFormat("base64")
			.build();

		OpenAiEmbeddingOptions copied = OpenAiEmbeddingOptions.builder().from(source).build();
		OpenAiEmbeddingOptions merged = OpenAiEmbeddingOptions.builder().model("other-model").merge(source).build();

		assertThat(copied.getEncodingFormat()).isEqualTo("base64");
		assertThat(merged.getEncodingFormat()).isEqualTo("base64");
	}

}
