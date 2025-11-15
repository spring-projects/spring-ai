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
			.dimensions(384)
			.normalize(true)
			.build();

		assertThat(options).extracting("model", "dimensions", "normalize")
			.containsExactly("sentence-transformers/all-MiniLM-L6-v2", 384, true);
	}

	@Test
	void testCopy() {
		HuggingfaceEmbeddingOptions originalOptions = HuggingfaceEmbeddingOptions.builder()
			.model("test-model")
			.dimensions(512)
			.normalize(false)
			.build();

		HuggingfaceEmbeddingOptions copiedOptions = originalOptions.copy();

		assertThat(copiedOptions).isNotSameAs(originalOptions).isEqualTo(originalOptions);
	}

	@Test
	void testSetters() {
		HuggingfaceEmbeddingOptions options = new HuggingfaceEmbeddingOptions();

		options.setModel("test-model");
		options.setDimensions(256);
		options.setNormalize(true);

		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getDimensions()).isEqualTo(256);
		assertThat(options.getNormalize()).isTrue();
	}

	@Test
	void testDefaultValues() {
		HuggingfaceEmbeddingOptions options = new HuggingfaceEmbeddingOptions();

		assertThat(options.getModel()).isNull();
		assertThat(options.getDimensions()).isNull();
		assertThat(options.getNormalize()).isNull();
	}

	@Test
	void testFromOptions() {
		HuggingfaceEmbeddingOptions originalOptions = HuggingfaceEmbeddingOptions.builder()
			.model("original-model")
			.dimensions(768)
			.normalize(true)
			.build();

		HuggingfaceEmbeddingOptions copiedOptions = HuggingfaceEmbeddingOptions.fromOptions(originalOptions);

		assertThat(copiedOptions).isNotSameAs(originalOptions).isEqualTo(originalOptions);
	}

	@Test
	void testToMap() {
		HuggingfaceEmbeddingOptions options = HuggingfaceEmbeddingOptions.builder()
			.model("test-model")
			.dimensions(512)
			.normalize(true)
			.build();

		Map<String, Object> map = options.toMap();

		assertThat(map).containsEntry("model", "test-model")
			.containsEntry("dimensions", 512)
			.containsEntry("normalize", true);
	}

	@Test
	void testEqualsAndHashCode() {
		HuggingfaceEmbeddingOptions options1 = HuggingfaceEmbeddingOptions.builder()
			.model("test-model")
			.dimensions(384)
			.normalize(false)
			.build();

		HuggingfaceEmbeddingOptions options2 = HuggingfaceEmbeddingOptions.builder()
			.model("test-model")
			.dimensions(384)
			.normalize(false)
			.build();

		assertThat(options1).isEqualTo(options2);
		assertThat(options1.hashCode()).isEqualTo(options2.hashCode());
	}

	@Test
	void testToString() {
		HuggingfaceEmbeddingOptions options = HuggingfaceEmbeddingOptions.builder()
			.model("test-model")
			.dimensions(512)
			.normalize(true)
			.build();

		String result = options.toString();

		assertThat(result).contains("test-model", "512", "true");
	}

	@Test
	void testBuilderWithNullValues() {
		HuggingfaceEmbeddingOptions options = HuggingfaceEmbeddingOptions.builder()
			.model(null)
			.dimensions(null)
			.normalize(null)
			.build();

		assertThat(options.getModel()).isNull();
		assertThat(options.getDimensions()).isNull();
		assertThat(options.getNormalize()).isNull();
	}

	@Test
	void testCopyChangeIndependence() {
		HuggingfaceEmbeddingOptions originalOptions = HuggingfaceEmbeddingOptions.builder()
			.model("original-model")
			.dimensions(384)
			.normalize(true)
			.build();

		HuggingfaceEmbeddingOptions copiedOptions = originalOptions.copy();

		// Modify original
		originalOptions.setModel("modified-model");
		originalOptions.setDimensions(768);

		// Copy should retain original values
		assertThat(copiedOptions.getModel()).isEqualTo("original-model");
		assertThat(copiedOptions.getDimensions()).isEqualTo(384);
		assertThat(originalOptions.getModel()).isEqualTo("modified-model");
		assertThat(originalOptions.getDimensions()).isEqualTo(768);
	}

	@Test
	void testBuilderChaining() {
		HuggingfaceEmbeddingOptions.Builder builder = HuggingfaceEmbeddingOptions.builder();

		HuggingfaceEmbeddingOptions.Builder result = builder.model("test-model").dimensions(512).normalize(true);

		assertThat(result).isSameAs(builder);
	}

}
