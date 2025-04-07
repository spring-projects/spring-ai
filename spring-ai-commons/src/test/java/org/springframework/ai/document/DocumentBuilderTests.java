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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.document.id.IdGenerator;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DocumentBuilderTests {

	private Document.Builder builder;

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
	void testWithMediaSingle() throws MalformedURLException {
		URL mediaUrl = new URL("http://test");
		Media media = Media.builder().mimeType(MimeTypeUtils.IMAGE_JPEG).data(mediaUrl).build();

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

}
