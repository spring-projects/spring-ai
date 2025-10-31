/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.mistralai.ocr;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MistralAiOcrOptions}.
 *
 * @author Alexandros Pappas
 * @since 1.1.0
 */
class MistralAiOcrOptionsTests {

	@Test
	void testBuilderWithAllFields() {
		MistralAiOcrOptions options = MistralAiOcrOptions.builder()
			.model("custom-model")
			.id("test-id")
			.pages(List.of(0, 1, 2))
			.includeImageBase64(true)
			.imageLimit(5)
			.imageMinSize(100)
			.build();

		assertThat(options).extracting("model", "id", "pages", "includeImageBase64", "imageLimit", "imageMinSize")
			.containsExactly("custom-model", "test-id", List.of(0, 1, 2), true, 5, 100);
	}

	@Test
	void testEqualsAndHashCode() {
		MistralAiOcrOptions options1 = MistralAiOcrOptions.builder()
			.model("custom-model")
			.id("test-id")
			.pages(List.of(0, 1, 2))
			.includeImageBase64(true)
			.imageLimit(5)
			.imageMinSize(100)
			.build();

		MistralAiOcrOptions options2 = MistralAiOcrOptions.builder()
			.model("custom-model")
			.id("test-id")
			.pages(List.of(0, 1, 2))
			.includeImageBase64(true)
			.imageLimit(5)
			.imageMinSize(100)
			.build();

		assertThat(options1).isEqualTo(options2);
		assertThat(options1.hashCode()).isEqualTo(options2.hashCode());
	}

	@Test
	void testDefaultValues() {
		MistralAiOcrOptions options = new MistralAiOcrOptions();
		assertThat(options.getModel()).isEqualTo("mistral-ocr-latest");
		assertThat(options.getId()).isNull();
		assertThat(options.getPages()).isNull();
		assertThat(options.getIncludeImageBase64()).isNull();
		assertThat(options.getImageLimit()).isNull();
		assertThat(options.getImageMinSize()).isNull();
	}

	@Test
	void testGetters() {
		MistralAiOcrOptions options = MistralAiOcrOptions.builder()
			.model("my-model")
			.id("id-123")
			.pages(List.of(3, 4))
			.includeImageBase64(false)
			.imageLimit(2)
			.imageMinSize(50)
			.build();

		assertThat(options.getModel()).isEqualTo("my-model");
		assertThat(options.getId()).isEqualTo("id-123");
		assertThat(options.getPages()).isEqualTo(List.of(3, 4));
		assertThat(options.getIncludeImageBase64()).isFalse();
		assertThat(options.getImageLimit()).isEqualTo(2);
		assertThat(options.getImageMinSize()).isEqualTo(50);
	}

}
