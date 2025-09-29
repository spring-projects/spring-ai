/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.document;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.content.Media;
import org.springframework.ai.document.id.IdGenerator;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DocumentBuilderTests {

	private Document.Builder builder;

	private static Media getMedia() {
		return Media.builder().data(URI.create("http://type1")).mimeType(MimeTypeUtils.IMAGE_JPEG).build();
	}

	@BeforeEach
	void setUp() {
		this.builder = Document.builder();
	}

	@Test
	void testWithIdGenerator() {
		IdGenerator mockGenerator = contents -> "mockedId";

		Document.Builder result = this.builder.idGenerator(mockGenerator);

		assertThat(result).isSameAs(this.builder);

		Document document = result.text("Test content").metadata("key", "value").build();

		assertThat(document.getId()).isEqualTo("mockedId");
	}

	@Test
	void testWithIdGeneratorNull() {
		assertThatThrownBy(() -> this.builder.idGenerator(null).build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("idGenerator cannot be null");
	}

	@Test
	void testWithId() {
		Document.Builder result = this.builder.text("text").id("testId");

		assertThat(result).isSameAs(this.builder);
		assertThat(result.build().getId()).isEqualTo("testId");
	}

	@Test
	void testWithIdNullOrEmpty() {
		assertThatThrownBy(() -> this.builder.text("text").id(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("id cannot be null or empty");

		assertThatThrownBy(() -> this.builder.text("text").id("").build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("id cannot be null or empty");
	}

	@Test
	void testWithContent() {
		Document.Builder result = this.builder.text("Test content");

		assertThat(result).isSameAs(this.builder);
		assertThat(result.build().getText()).isEqualTo("Test content");
	}

	@Test
	void testWithMediaSingle() {
		Media media = Media.builder().mimeType(MimeTypeUtils.IMAGE_JPEG).data(URI.create("http://test")).build();

		Document.Builder result = this.builder.media(media);

		assertThat(result).isSameAs(this.builder);
		assertThat(result.build().getMedia()).isEqualTo(media);
	}

	@Test
	void testWithMetadataMap() {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("key1", "value1");
		metadata.put("key2", 2);
		Document.Builder result = this.builder.text("text").metadata(metadata);

		assertThat(result).isSameAs(this.builder);
		assertThat(result.build().getMetadata()).isEqualTo(metadata);
	}

	@Test
	void testWithMetadataMapNull() {
		assertThatThrownBy(() -> this.builder.text("text").metadata(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("metadata cannot be null");
	}

	@Test
	void testWithMetadataKeyValue() {
		Document.Builder result = this.builder.metadata("key", "value");

		assertThat(result).isSameAs(this.builder);
		assertThat(result.text("text").build().getMetadata()).containsEntry("key", "value");
	}

	@Test
	void testWithMetadataKeyNull() {
		assertThatThrownBy(() -> this.builder.text("text").metadata(null, "value").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("metadata key cannot be null");
	}

	@Test
	void testWithMetadataValueNull() {
		assertThatThrownBy(() -> this.builder.text("text").metadata("key", null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("metadata value cannot be null");
	}

	@Test
	void testBuildWithoutId() {
		Document document = this.builder.text("text").text("Test content").build();

		assertThat(document.getId()).isNotNull().isNotEmpty();
		assertThat(document.getText()).isEqualTo("Test content");
	}

	@Test
	void testBuildWithAllProperties() {

		Media media = getMedia();
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("key", "value");

		Document document = this.builder.id("customId").text("Test content").metadata(metadata).build();

		assertThat(document.getId()).isEqualTo("customId");
		assertThat(document.getText()).isEqualTo("Test content");
		assertThat(document.getMetadata()).isEqualTo(metadata);
	}

	@Test
	void testWithWhitespaceOnlyId() {
		assertThatThrownBy(() -> this.builder.text("text").id("   ").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("id cannot be null or empty");
	}

	@Test
	void testWithEmptyText() {
		Document document = this.builder.text("").build();
		assertThat(document.getText()).isEqualTo("");
	}

	@Test
	void testOverwritingText() {
		Document document = this.builder.text("initial text").text("final text").build();
		assertThat(document.getText()).isEqualTo("final text");
	}

	@Test
	void testMultipleMetadataKeyValueCalls() {
		Document document = this.builder.text("text")
			.metadata("key1", "value1")
			.metadata("key2", "value2")
			.metadata("key3", 123)
			.build();

		assertThat(document.getMetadata()).hasSize(3)
			.containsEntry("key1", "value1")
			.containsEntry("key2", "value2")
			.containsEntry("key3", 123);
	}

	@Test
	void testMetadataMapOverridesKeyValue() {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("newKey", "newValue");

		Document document = this.builder.text("text").metadata("oldKey", "oldValue").metadata(metadata).build();

		assertThat(document.getMetadata()).hasSize(1).containsEntry("newKey", "newValue").doesNotContainKey("oldKey");
	}

	@Test
	void testKeyValueMetadataAfterMap() {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("mapKey", "mapValue");

		Document document = this.builder.text("text")
			.metadata(metadata)
			.metadata("additionalKey", "additionalValue")
			.build();

		assertThat(document.getMetadata()).hasSize(2)
			.containsEntry("mapKey", "mapValue")
			.containsEntry("additionalKey", "additionalValue");
	}

	@Test
	void testWithEmptyMetadataMap() {
		Map<String, Object> emptyMetadata = new HashMap<>();

		Document document = this.builder.text("text").metadata(emptyMetadata).build();

		assertThat(document.getMetadata()).isEmpty();
	}

	@Test
	void testOverwritingMetadataWithSameKey() {
		Document document = this.builder.text("text")
			.metadata("key", "firstValue")
			.metadata("key", "secondValue")
			.build();

		assertThat(document.getMetadata()).hasSize(1).containsEntry("key", "secondValue");
	}

	@Test
	void testWithNullMedia() {
		Document document = this.builder.text("text").media(null).build();
		assertThat(document.getMedia()).isNull();
	}

	@Test
	void testIdOverridesIdGenerator() {
		IdGenerator generator = contents -> "generated-id";

		Document document = this.builder.text("text").idGenerator(generator).id("explicit-id").build();

		assertThat(document.getId()).isEqualTo("explicit-id");
	}

	@Test
	void testComplexMetadataTypes() {
		Map<String, Object> nestedMap = new HashMap<>();
		nestedMap.put("nested", "value");

		Document document = this.builder.text("text")
			.metadata("string", "text")
			.metadata("integer", 42)
			.metadata("double", 3.14)
			.metadata("boolean", true)
			.metadata("map", nestedMap)
			.build();

		assertThat(document.getMetadata()).hasSize(5)
			.containsEntry("string", "text")
			.containsEntry("integer", 42)
			.containsEntry("double", 3.14)
			.containsEntry("boolean", true)
			.containsEntry("map", nestedMap);
	}

	@Test
	void testBuilderReuse() {
		// First document
		Document doc1 = this.builder.text("first").id("id1").metadata("key", "value1").build();

		// Reuse builder for second document
		Document doc2 = this.builder.text("second").id("id2").metadata("key", "value2").build();

		assertThat(doc1.getId()).isEqualTo("id1");
		assertThat(doc1.getText()).isEqualTo("first");
		assertThat(doc1.getMetadata()).containsEntry("key", "value1");

		assertThat(doc2.getId()).isEqualTo("id2");
		assertThat(doc2.getText()).isEqualTo("second");
		assertThat(doc2.getMetadata()).containsEntry("key", "value2");
	}

	@Test
	void testMediaDocumentWithoutText() {
		Media media = getMedia();
		Document document = this.builder.media(media).build();

		assertThat(document.getMedia()).isEqualTo(media);
		assertThat(document.getText()).isNull();
	}

	@Test
	void testTextDocumentWithoutMedia() {
		Document document = this.builder.text("test content").build();

		assertThat(document.getText()).isEqualTo("test content");
		assertThat(document.getMedia()).isNull();
	}

	@Test
	void testOverwritingMediaWithNull() {
		Media media = getMedia();
		Document document = this.builder.media(media).media(null).text("fallback").build();

		assertThat(document.getMedia()).isNull();
	}

	@Test
	void testMetadataWithSpecialCharacterKeys() {
		Document document = this.builder.text("test")
			.metadata("key-with-dashes", "value1")
			.metadata("key.with.dots", "value2")
			.metadata("key_with_underscores", "value3")
			.metadata("key with spaces", "value4")
			.build();

		assertThat(document.getMetadata()).containsEntry("key-with-dashes", "value1")
			.containsEntry("key.with.dots", "value2")
			.containsEntry("key_with_underscores", "value3")
			.containsEntry("key with spaces", "value4");
	}

	@Test
	void testBuilderStateIsolation() {
		// Configure first builder state
		this.builder.text("first").metadata("shared", "first");

		// Create first document
		Document doc1 = this.builder.build();

		// Modify builder for second document
		this.builder.text("second").metadata("shared", "second");

		// Create second document
		Document doc2 = this.builder.build();

		// Verify first document wasn't affected by subsequent changes
		assertThat(doc1.getText()).isEqualTo("first");
		assertThat(doc1.getMetadata()).containsEntry("shared", "first");

		assertThat(doc2.getText()).isEqualTo("second");
		assertThat(doc2.getMetadata()).containsEntry("shared", "second");
	}

	@Test
	void testBuilderMethodChaining() {
		Document document = this.builder.text("chained")
			.id("chain-id")
			.metadata("key1", "value1")
			.metadata("key2", "value2")
			.score(0.75)
			.build();

		assertThat(document.getText()).isEqualTo("chained");
		assertThat(document.getId()).isEqualTo("chain-id");
		assertThat(document.getMetadata()).hasSize(2);
		assertThat(document.getScore()).isEqualTo(0.75);
	}

	@Test
	void testTextWithNewlinesAndTabs() {
		String textWithFormatting = "Line 1\nLine 2\n\tTabbed line\r\nWindows line ending";
		Document document = this.builder.text(textWithFormatting).build();

		assertThat(document.getText()).isEqualTo(textWithFormatting);
	}

	@Test
	void testMetadataOverwritingWithMapAfterKeyValue() {
		Map<String, Object> newMetadata = new HashMap<>();
		newMetadata.put("map-key", "map-value");

		Document document = this.builder.text("test")
			.metadata("old-key", "old-value")
			.metadata("another-key", "another-value")
			.metadata(newMetadata) // This should replace all previous metadata
			.build();

		assertThat(document.getMetadata()).hasSize(1);
		assertThat(document.getMetadata()).containsEntry("map-key", "map-value");
		assertThat(document.getMetadata()).doesNotContainKey("old-key");
		assertThat(document.getMetadata()).doesNotContainKey("another-key");
	}

	@Test
	void testMetadataKeyValuePairsAccumulation() {
		Document document = this.builder.text("test")
			.metadata("a", "1")
			.metadata("b", "2")
			.metadata("c", "3")
			.metadata("d", "4")
			.metadata("e", "5")
			.build();

		assertThat(document.getMetadata()).hasSize(5);
		assertThat(document.getMetadata().keySet()).containsExactlyInAnyOrder("a", "b", "c", "d", "e");
	}

}
