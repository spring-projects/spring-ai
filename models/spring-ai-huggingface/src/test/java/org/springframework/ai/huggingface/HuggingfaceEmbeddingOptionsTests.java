/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.huggingface;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HuggingfaceEmbeddingOptions}.
 *
 * @author Myeongdeok Kang
 */
class HuggingfaceEmbeddingOptionsTests {

	@Test
	void testBuilderWithAllFields() {
		HuggingfaceEmbeddingOptions options = HuggingfaceEmbeddingOptions.builder()
			.model("sentence-transformers/all-MiniLM-L6-v2")
			.normalize(true)
			.promptName("query")
			.truncate(true)
			.truncationDirection("Right")
			.build();

		assertThat(options).extracting("model", "normalize", "promptName", "truncate", "truncationDirection")
			.containsExactly("sentence-transformers/all-MiniLM-L6-v2", true, "query", true, "Right");
	}

	@Test
	void testCopy() {
		HuggingfaceEmbeddingOptions originalOptions = HuggingfaceEmbeddingOptions.builder()
			.model("test-model")
			.normalize(false)
			.promptName("passage")
			.truncate(false)
			.truncationDirection("Left")
			.build();

		HuggingfaceEmbeddingOptions copiedOptions = originalOptions.copy();

		assertThat(copiedOptions).isNotSameAs(originalOptions).isEqualTo(originalOptions);
	}

	@Test
	void testSetters() {
		HuggingfaceEmbeddingOptions options = new HuggingfaceEmbeddingOptions();

		options.setModel("test-model");
		options.setNormalize(true);
		options.setPromptName("query");
		options.setTruncate(true);
		options.setTruncationDirection("Right");

		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getNormalize()).isTrue();
		assertThat(options.getPromptName()).isEqualTo("query");
		assertThat(options.getTruncate()).isTrue();
		assertThat(options.getTruncationDirection()).isEqualTo("Right");
	}

	@Test
	void testDefaultValues() {
		HuggingfaceEmbeddingOptions options = new HuggingfaceEmbeddingOptions();

		assertThat(options.getModel()).isNull();
		assertThat(options.getNormalize()).isNull();
		assertThat(options.getPromptName()).isNull();
		assertThat(options.getTruncate()).isNull();
		assertThat(options.getTruncationDirection()).isNull();
	}

	@Test
	void testFromOptions() {
		HuggingfaceEmbeddingOptions originalOptions = HuggingfaceEmbeddingOptions.builder()
			.model("original-model")
			.normalize(true)
			.promptName("document")
			.truncate(true)
			.truncationDirection("Left")
			.build();

		HuggingfaceEmbeddingOptions copiedOptions = HuggingfaceEmbeddingOptions.fromOptions(originalOptions);

		assertThat(copiedOptions).isNotSameAs(originalOptions).isEqualTo(originalOptions);
	}

	@Test
	void testToMap() {
		HuggingfaceEmbeddingOptions options = HuggingfaceEmbeddingOptions.builder()
			.model("test-model")
			.normalize(true)
			.promptName("query")
			.truncate(false)
			.truncationDirection("Right")
			.build();

		Map<String, Object> map = options.toMap();

		assertThat(map).containsEntry("model", "test-model")
			.containsEntry("normalize", true)
			.containsEntry("prompt_name", "query")
			.containsEntry("truncate", false)
			.containsEntry("truncation_direction", "Right");
	}

	@Test
	void testEqualsAndHashCode() {
		HuggingfaceEmbeddingOptions options1 = HuggingfaceEmbeddingOptions.builder()
			.model("test-model")
			.normalize(false)
			.promptName("passage")
			.truncate(true)
			.truncationDirection("Left")
			.build();

		HuggingfaceEmbeddingOptions options2 = HuggingfaceEmbeddingOptions.builder()
			.model("test-model")
			.normalize(false)
			.promptName("passage")
			.truncate(true)
			.truncationDirection("Left")
			.build();

		assertThat(options1).isEqualTo(options2);
		assertThat(options1.hashCode()).isEqualTo(options2.hashCode());
	}

	@Test
	void testToString() {
		HuggingfaceEmbeddingOptions options = HuggingfaceEmbeddingOptions.builder()
			.model("test-model")
			.normalize(true)
			.promptName("query")
			.truncate(false)
			.truncationDirection("Right")
			.build();

		String result = options.toString();

		assertThat(result).contains("test-model", "true", "query", "false", "Right");
	}

	@Test
	void testBuilderWithNullValues() {
		HuggingfaceEmbeddingOptions options = HuggingfaceEmbeddingOptions.builder()
			.model(null)
			.normalize(null)
			.promptName(null)
			.truncate(null)
			.truncationDirection(null)
			.build();

		assertThat(options.getModel()).isNull();
		assertThat(options.getNormalize()).isNull();
		assertThat(options.getPromptName()).isNull();
		assertThat(options.getTruncate()).isNull();
		assertThat(options.getTruncationDirection()).isNull();
	}

	@Test
	void testCopyChangeIndependence() {
		HuggingfaceEmbeddingOptions originalOptions = HuggingfaceEmbeddingOptions.builder()
			.model("original-model")
			.normalize(true)
			.promptName("query")
			.build();

		HuggingfaceEmbeddingOptions copiedOptions = originalOptions.copy();

		// Modify original
		originalOptions.setModel("modified-model");
		originalOptions.setNormalize(false);

		// Copy should retain original values
		assertThat(copiedOptions.getModel()).isEqualTo("original-model");
		assertThat(copiedOptions.getNormalize()).isTrue();
		assertThat(originalOptions.getModel()).isEqualTo("modified-model");
		assertThat(originalOptions.getNormalize()).isFalse();
	}

	@Test
	void testBuilderChaining() {
		HuggingfaceEmbeddingOptions.Builder builder = HuggingfaceEmbeddingOptions.builder();

		HuggingfaceEmbeddingOptions.Builder result = builder.model("test-model")
			.normalize(true)
			.promptName("query")
			.truncate(true)
			.truncationDirection("Right");

		assertThat(result).isSameAs(builder);
	}

}
