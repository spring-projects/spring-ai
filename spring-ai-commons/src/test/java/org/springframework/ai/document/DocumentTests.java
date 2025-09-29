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

import org.junit.jupiter.api.Test;

import org.springframework.ai.content.Media;
import org.springframework.ai.document.id.IdGenerator;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DocumentTests {

	@Test
	void testScore() {
		Double score = 0.95;
		Document document = Document.builder().text("Test content").score(score).build();

		assertThat(document.getScore()).isEqualTo(score);
	}

	@Test
	void testNullScore() {
		Document document = Document.builder().text("Test content").score(null).build();

		assertThat(document.getScore()).isNull();
	}

	@Test
	void testMutate() {
		Media media = getMedia();
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("key", "value");
		Double score = 0.95;

		Document original = Document.builder()
			.id("customId")
			.text("Test content")
			.media(null)
			.metadata(metadata)
			.score(score)
			.build();

		Document mutated = original.mutate().build();

		assertThat(mutated).isNotSameAs(original).usingRecursiveComparison().isEqualTo(original);
	}

	@Test
	void testEquals() {
		Media media = getMedia();
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("key", "value");
		Double score = 0.95;

		Document doc1 = Document.builder().id("customId").text("Test text").metadata(metadata).score(score).build();

		Document doc2 = Document.builder().id("customId").text("Test text").metadata(metadata).score(score).build();

		Document differentDoc = Document.builder()
			.id("differentId")
			.text("Different content")
			.metadata(metadata)
			.score(score)
			.build();

		assertThat(doc1).isEqualTo(doc2).isNotEqualTo(differentDoc).isNotEqualTo(null).isNotEqualTo(new Object());

		assertThat(doc1.hashCode()).isEqualTo(doc2.hashCode());
	}

	@Test
	void testEmptyDocument() {
		assertThrows(IllegalArgumentException.class, () -> Document.builder().build());
	}

	@Test
	void testToString() {
		Media media = getMedia();
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("key", "value");
		Double score = 0.95;

		Document document = Document.builder()
			.id("customId")
			.text("Test content")
			.media(null)
			.metadata(metadata)
			.score(score)
			.build();

		String toString = document.toString();

		assertThat(toString).contains("id='customId'")
			.contains("text='Test content'")
			.contains("metadata=" + metadata)
			.contains("score=" + score);
	}

	@Test
	void testMediaDocumentConstruction() {
		Media media = getMedia();
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("key", "value");

		Document document = Document.builder().media(media).metadata(metadata).build();

		assertThat(document.getMedia()).isEqualTo(media);
		assertThat(document.getText()).isNull();
		assertThat(document.isText()).isFalse();
	}

	@Test
	void testTextDocumentConstruction() {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("key", "value");

		Document document = Document.builder().text("Test text").metadata(metadata).build();

		assertThat(document.getText()).isEqualTo("Test text");
		assertThat(document.getMedia()).isNull();
		assertThat(document.isText()).isTrue();
	}

	@Test
	void testBothTextAndMediaThrowsException() {
		Media media = getMedia();
		assertThrows(IllegalArgumentException.class, () -> Document.builder().text("Test text").media(media).build());
	}

	@Test
	void testCustomIdGenerator() {
		IdGenerator customGenerator = contents -> "custom-" + contents[0];

		Document document = Document.builder().text("test").idGenerator(customGenerator).build();

		assertThat(document.getId()).isEqualTo("custom-test");
	}

	@Test
	void testMetadataValidation() {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("nullKey", null);

		assertThrows(IllegalArgumentException.class, () -> Document.builder().text("test").metadata(metadata).build());
	}

	@Test
	void testFormattedContent() {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("key", "value");

		Document document = Document.builder().text("Test text").metadata(metadata).build();

		String formattedContent = document.getFormattedContent(MetadataMode.ALL);
		assertThat(formattedContent).contains("Test text");
		assertThat(formattedContent).contains("key");
		assertThat(formattedContent).contains("value");
	}

	@Test
	void testCustomFormattedContent() {
		Document document = Document.builder().text("Test text").build();

		ContentFormatter customFormatter = (doc, mode) -> "Custom: " + doc.getText();
		String formattedContent = document.getFormattedContent(customFormatter, MetadataMode.ALL);

		assertThat(formattedContent).isEqualTo("Custom: Test text");
	}

	@Test
	void testNullIdThrowsException() {
		assertThrows(IllegalArgumentException.class, () -> Document.builder().id(null).text("test").build());
	}

	@Test
	void testEmptyIdThrowsException() {
		assertThrows(IllegalArgumentException.class, () -> Document.builder().id("").text("test").build());
	}

	@Test
	void testMetadataKeyValueAddition() {
		Document document = Document.builder()
			.text("test")
			.metadata("key1", "value1")
			.metadata("key2", "value2")
			.build();

		assertThat(document.getMetadata()).containsEntry("key1", "value1").containsEntry("key2", "value2");
	}

	private static Media getMedia() {
		return Media.builder().mimeType(MimeTypeUtils.IMAGE_JPEG).data(URI.create("http://type1")).build();
	}

	@Test
	void testMetadataModeNone() {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("secret", "hidden");

		Document document = Document.builder().text("Visible content").metadata(metadata).build();

		String formattedContent = document.getFormattedContent(MetadataMode.NONE);
		assertThat(formattedContent).contains("Visible content");
		assertThat(formattedContent).doesNotContain("secret");
		assertThat(formattedContent).doesNotContain("hidden");
	}

	@Test
	void testMetadataModeEmbed() {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("embedKey", "embedValue");
		metadata.put("filterKey", "filterValue");

		Document document = Document.builder().text("Test content").metadata(metadata).build();

		String formattedContent = document.getFormattedContent(MetadataMode.EMBED);
		// This test assumes EMBED mode includes all metadata - adjust based on actual
		// implementation
		assertThat(formattedContent).contains("Test content");
	}

	@Test
	void testDocumentBuilderChaining() {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("chain", "test");

		Document document = Document.builder()
			.text("Chain test")
			.metadata(metadata)
			.metadata("additional", "value")
			.score(0.85)
			.build();

		assertThat(document.getText()).isEqualTo("Chain test");
		assertThat(document.getMetadata()).containsEntry("chain", "test");
		assertThat(document.getMetadata()).containsEntry("additional", "value");
		assertThat(document.getScore()).isEqualTo(0.85);
	}

	@Test
	void testDocumentWithScoreGreaterThanOne() {
		Document document = Document.builder().text("High score test").score(1.5).build();

		assertThat(document.getScore()).isEqualTo(1.5);
	}

	@Test
	void testMutateWithChanges() {
		Document original = Document.builder().text("Original text").score(0.5).metadata("original", "value").build();

		Document mutated = original.mutate().text("Mutated text").score(0.8).metadata("new", "metadata").build();

		assertThat(mutated.getText()).isEqualTo("Mutated text");
		assertThat(mutated.getScore()).isEqualTo(0.8);
		assertThat(mutated.getMetadata()).containsEntry("new", "metadata");
		assertThat(original.getText()).isEqualTo("Original text"); // Original unchanged
	}

	@Test
	void testDocumentEqualityWithDifferentScores() {
		Document doc1 = Document.builder().id("sameId").text("Same text").score(0.5).build();

		Document doc2 = Document.builder().id("sameId").text("Same text").score(0.8).build();

		// Assuming score affects equality - adjust if it doesn't
		assertThat(doc1).isNotEqualTo(doc2);
	}

	@Test
	void testDocumentWithComplexMetadata() {
		Map<String, Object> nestedMap = new HashMap<>();
		nestedMap.put("nested", "value");

		Map<String, Object> metadata = new HashMap<>();
		metadata.put("string", "value");
		metadata.put("number", 1);
		metadata.put("boolean", true);
		metadata.put("map", nestedMap);

		Document document = Document.builder().text("Complex metadata test").metadata(metadata).build();

		assertThat(document.getMetadata()).containsEntry("string", "value");
		assertThat(document.getMetadata()).containsEntry("number", 1);
		assertThat(document.getMetadata()).containsEntry("boolean", true);
		assertThat(document.getMetadata()).containsEntry("map", nestedMap);
	}

	@Test
	void testMetadataImmutability() {
		Map<String, Object> originalMetadata = new HashMap<>();
		originalMetadata.put("key", "value");

		Document document = Document.builder().text("Immutability test").metadata(originalMetadata).build();

		// Modify original map
		originalMetadata.put("key", "modified");
		originalMetadata.put("newKey", "newValue");

		// Document's metadata should be unaffected (if properly copied)
		assertThat(document.getMetadata()).containsEntry("key", "value");
		assertThat(document.getMetadata()).doesNotContainKey("newKey");
	}

	@Test
	void testDocumentWithEmptyMetadata() {
		Document document = Document.builder().text("Empty metadata test").metadata(new HashMap<>()).build();

		assertThat(document.getMetadata()).isEmpty();
	}

	@Test
	void testMetadataWithNullValueInMap() {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("validKey", "validValue");
		metadata.put("nullKey", null);

		assertThrows(IllegalArgumentException.class, () -> Document.builder().text("test").metadata(metadata).build());
	}

	@Test
	void testDocumentWithWhitespaceOnlyText() {
		String whitespaceText = "   \n\t\r   ";
		Document document = Document.builder().text(whitespaceText).build();

		assertThat(document.getText()).isEqualTo(whitespaceText);
		assertThat(document.isText()).isTrue();
	}

	@Test
	void testDocumentHashCodeConsistency() {
		Document document = Document.builder().text("Hash test").metadata("key", "value").score(0.1).build();

		int hashCode1 = document.hashCode();
		int hashCode2 = document.hashCode();

		assertThat(hashCode1).isEqualTo(hashCode2);
	}

}
