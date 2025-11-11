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

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.document.id.IdGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Christian Tzolov
 */
class ContentFormatterTests {

	Document document = new Document("The World is Big and Salvation Lurks Around the Corner",
			Map.of("embedKey1", "value1", "embedKey2", "value2", "embedKey3", "value3", "llmKey2", "value4"));

	@Test
	void noExplicitlySetFormatter() {
		TextBlockAssertion.assertThat(this.document.getText()).isEqualTo("""
				The World is Big and Salvation Lurks Around the Corner""");

		assertThat(this.document.getFormattedContent()).isEqualTo(this.document.getFormattedContent(MetadataMode.ALL));
		assertThat(this.document.getFormattedContent())
			.isEqualTo(this.document.getFormattedContent(Document.DEFAULT_CONTENT_FORMATTER, MetadataMode.ALL));

	}

	@Test
	void defaultConfigTextFormatter() {

		DefaultContentFormatter defaultConfigFormatter = DefaultContentFormatter.defaultConfig();

		TextBlockAssertion.assertThat(this.document.getFormattedContent(defaultConfigFormatter, MetadataMode.ALL))
			.isEqualTo("""
					llmKey2: value4
					embedKey1: value1
					embedKey2: value2
					embedKey3: value3

					The World is Big and Salvation Lurks Around the Corner""");

		assertThat(this.document.getFormattedContent(defaultConfigFormatter, MetadataMode.ALL))
			.isEqualTo(this.document.getFormattedContent());

		assertThat(this.document.getFormattedContent(defaultConfigFormatter, MetadataMode.ALL))
			.isEqualTo(defaultConfigFormatter.format(this.document, MetadataMode.ALL));
	}

	@Test
	void shouldThrowWhenIdIsNull() {
		assertThatThrownBy(() -> new Document(null, "text", new HashMap<>()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("id cannot be null or empty");
	}

	@Test
	void shouldThrowWhenIdIsEmpty() {
		assertThatThrownBy(() -> new Document("", "text", new HashMap<>())).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("id cannot be null or empty");
	}

	@Test
	void shouldThrowWhenMetadataIsNull() {
		assertThatThrownBy(() -> new Document("Sample text", null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("metadata cannot be null");
	}

	@Test
	void shouldThrowWhenMetadataHasNullKey() {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put(null, "value");

		assertThatThrownBy(() -> new Document("Sample text", metadata)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("metadata cannot have null keys");
	}

	@Test
	void shouldThrowWhenMetadataHasNullValue() {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("key", null);

		assertThatThrownBy(() -> new Document("Sample text", metadata)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("metadata cannot have null values");
	}

	@Test
	void shouldThrowWhenNeitherTextNorMediaAreSet() {
		assertThatThrownBy(() -> Document.builder().id("test-id").metadata("key", "value").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("exactly one of text or media must be specified");
	}

	@Test
	void builderWithCustomIdGenerator() {
		IdGenerator mockGenerator = mock(IdGenerator.class);
		when(mockGenerator.generateId("test text", Map.of("key", "value"))).thenReturn("generated-id");

		Document document = Document.builder()
			.idGenerator(mockGenerator)
			.text("test text")
			.metadata("key", "value")
			.build();

		assertThat(document.getId()).isEqualTo("generated-id");
	}

	@Test
	void builderShouldThrowWhenIdGeneratorIsNull() {
		assertThatThrownBy(() -> Document.builder().idGenerator(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("idGenerator cannot be null");
	}

	@Test
	void builderShouldThrowWhenMetadataKeyIsNull() {
		assertThatThrownBy(() -> Document.builder().metadata(null, "value"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("metadata key cannot be null");
	}

	@Test
	void builderShouldThrowWhenMetadataValueIsNull() {
		assertThatThrownBy(() -> Document.builder().metadata("key", null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("metadata value cannot be null");
	}

	@Test
	void setCustomContentFormatter() {
		Document document = new Document("Sample text", Map.of());
		ContentFormatter customFormatter = mock(ContentFormatter.class);
		when(customFormatter.format(document, MetadataMode.ALL)).thenReturn("Custom formatted content");

		document.setContentFormatter(customFormatter);

		assertThat(document.getContentFormatter()).isEqualTo(customFormatter);
		assertThat(document.getFormattedContent()).isEqualTo("Custom formatted content");
	}

	@Test
	void shouldThrowWhenFormatterIsNull() {
		Document document = new Document("Sample text", Map.of());

		assertThatThrownBy(() -> document.getFormattedContent(null, MetadataMode.ALL))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("formatter must not be null");
	}

	@Test
	void shouldThrowWhenMetadataModeIsNull() {
		Document document = new Document("Sample text", Map.of());

		assertThatThrownBy(() -> document.getFormattedContent(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Metadata mode must not be null");
	}

	@Test
	void mutateTextDocument() {
		Document original = new Document("id", "original text", Map.of("key", "value"));

		Document mutated = original.mutate().text("modified text").metadata("newKey", "newValue").score(0.9).build();

		assertThat(mutated.getId()).isEqualTo("id");
		assertThat(mutated.getText()).isEqualTo("modified text");
		assertThat(mutated.getMetadata()).containsEntry("newKey", "newValue");
		assertThat(mutated.getScore()).isEqualTo(0.9);

		// Original should be unchanged
		assertThat(original.getText()).isEqualTo("original text");
		assertThat(original.getScore()).isNull();
	}

	@Test
	void equalDocuments() {
		Map<String, Object> metadata = Map.of("key", "value");
		Document doc1 = new Document("id", "text", metadata);
		Document doc2 = new Document("id", "text", metadata);

		assertThat(doc1).isEqualTo(doc2);
		assertThat(doc1.hashCode()).isEqualTo(doc2.hashCode());
	}

	@Test
	void differentIds() {
		Map<String, Object> metadata = Map.of("key", "value");
		Document doc1 = new Document("id1", "text", metadata);
		Document doc2 = new Document("id2", "text", metadata);

		assertThat(doc1).isNotEqualTo(doc2);
	}

	@Test
	void differentText() {
		Map<String, Object> metadata = Map.of("key", "value");
		Document doc1 = new Document("id", "text1", metadata);
		Document doc2 = new Document("id", "text2", metadata);

		assertThat(doc1).isNotEqualTo(doc2);
	}

	@Test
	void isTextReturnsTrueForTextDocument() {
		Document document = new Document("Sample text", Map.of());
		assertThat(document.isText()).isTrue();
		assertThat(document.getText()).isNotNull();
		assertThat(document.getMedia()).isNull();
	}

	@Test
	void scoreHandling() {
		Document document = Document.builder().text("test").score(0.85).build();

		assertThat(document.getScore()).isEqualTo(0.85);

		Document documentWithoutScore = new Document("test");
		assertThat(documentWithoutScore.getScore()).isNull();
	}

	@Test
	void metadataImmutability() {
		Map<String, Object> originalMetadata = new HashMap<>();
		originalMetadata.put("key", "value");

		Document document = new Document("test", originalMetadata);

		// Modify original map
		originalMetadata.put("newKey", "newValue");

		// Document's metadata should not be affected
		assertThat(document.getMetadata()).hasSize(1);
		assertThat(document.getMetadata()).containsEntry("key", "value");
		assertThat(document.getMetadata()).doesNotContainKey("newKey");
	}

	@Test
	void builderWithMetadataMap() {
		Map<String, Object> metadata = Map.of("key1", "value1", "key2", 1);
		Document document = Document.builder().text("test").metadata(metadata).build();

		assertThat(document.getMetadata()).containsExactlyInAnyOrderEntriesOf(metadata);
	}

}
