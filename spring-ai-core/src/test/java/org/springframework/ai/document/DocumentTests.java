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

import org.junit.jupiter.api.Test;

import org.springframework.ai.document.id.IdGenerator;
import org.springframework.ai.model.Media;
import org.springframework.util.MimeTypeUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

	@Test
	void testEmbeddingOperations() {
		float[] embedding = new float[] { 0.1f, 0.2f, 0.3f };

		Document document = Document.builder().text("test").embedding(embedding).build();

		assertThat(document.getEmbedding()).isEqualTo(embedding);
	}

	@Test
	void testNullEmbeddingThrowsException() {
		assertThrows(IllegalArgumentException.class, () -> Document.builder().text("test").embedding(null).build());
	}

	private static Media getMedia() {
		try {
			URL mediaUrl1 = new URL("http://type1");
			Media media1 = new Media(MimeTypeUtils.IMAGE_JPEG, mediaUrl1);
			return media1;
		}
		catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

}
