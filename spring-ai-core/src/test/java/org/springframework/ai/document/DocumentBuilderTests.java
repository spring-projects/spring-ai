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
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.document.id.IdGenerator;
import org.springframework.ai.model.Media;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DocumentBuilderTests {

	private Document.Builder builder;

	private static List<Media> getMediaList() {
		try {
			URL mediaUrl1 = new URL("http://type1");
			URL mediaUrl2 = new URL("http://type2");
			Media media1 = Media.builder().mimeType(MimeTypeUtils.IMAGE_JPEG).data(mediaUrl1).build();
			Media media2 = Media.builder().mimeType(MimeTypeUtils.IMAGE_JPEG).data(mediaUrl2).build();
			return List.of(media1, media2);
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

		Document document = result.content("Test content").metadata("key", "value").build();

		assertThat(document.getId()).isEqualTo("mockedId");
	}

	@Test
	void testWithIdGeneratorNull() {
		assertThatThrownBy(() -> this.builder.idGenerator(null).build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("idGenerator cannot be null");
	}

	@Test
	void testWithId() {
		Document.Builder result = this.builder.id("testId");

		assertThat(result).isSameAs(this.builder);
		assertThat(result.build().getId()).isEqualTo("testId");
	}

	@Test
	void testWithIdNullOrEmpty() {
		assertThatThrownBy(() -> this.builder.id(null).build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("id cannot be null or empty");

		assertThatThrownBy(() -> this.builder.id("").build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("id cannot be null or empty");
	}

	@Test
	void testWithContent() {
		Document.Builder result = this.builder.content("Test content");

		assertThat(result).isSameAs(this.builder);
		assertThat(result.build().getContent()).isEqualTo("Test content");
	}

	@Test
	void testWithContentNull() {
		assertThatThrownBy(() -> this.builder.content(null).build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("content cannot be null");
	}

	@Test
	void testWithMediaList() {
		List<Media> mediaList = getMediaList();
		Document.Builder result = this.builder.media(mediaList);

		assertThat(result).isSameAs(this.builder);
		assertThat(result.build().getMedia()).isEqualTo(mediaList);
	}

	@Test
	void testWithMediaListNull() {
		assertThatThrownBy(() -> this.builder.media((List<Media>) null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("media cannot be null");
	}

	@Test
	void testWithMediaSingle() throws MalformedURLException {
		URL mediaUrl = new URL("http://test");
		Media media = Media.builder().mimeType(MimeTypeUtils.IMAGE_JPEG).data(mediaUrl).build();

		Document.Builder result = this.builder.media(media);

		assertThat(result).isSameAs(this.builder);
		assertThat(result.build().getMedia()).contains(media);
	}

	@Test
	void testWithMediaSingleNull() {
		assertThatThrownBy(() -> this.builder.media((Media) null).build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("media cannot contain null elements");
	}

	@Test
	void testWithMetadataMap() {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("key1", "value1");
		metadata.put("key2", 2);
		Document.Builder result = this.builder.metadata(metadata);

		assertThat(result).isSameAs(this.builder);
		assertThat(result.build().getMetadata()).isEqualTo(metadata);
	}

	@Test
	void testWithMetadataMapNull() {
		assertThatThrownBy(() -> this.builder.metadata(null).build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("metadata cannot be null");
	}

	@Test
	void testWithMetadataKeyValue() {
		Document.Builder result = this.builder.metadata("key", "value");

		assertThat(result).isSameAs(this.builder);
		assertThat(result.build().getMetadata()).containsEntry("key", "value");
	}

	@Test
	void testWithMetadataKeyNull() {
		assertThatThrownBy(() -> this.builder.metadata(null, "value").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("metadata cannot have null keys");
	}

	@Test
	void testWithMetadataValueNull() {
		assertThatThrownBy(() -> this.builder.metadata("key", null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("metadata cannot have null values");
	}

	@Test
	void testBuildWithoutId() {
		Document document = this.builder.content("Test content").build();

		assertThat(document.getId()).isNotNull().isNotEmpty();
		assertThat(document.getContent()).isEqualTo("Test content");
	}

	@Test
	void testBuildWithAllProperties() {

		List<Media> mediaList = getMediaList();
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("key", "value");

		Document document = this.builder.id("customId")
			.content("Test content")
			.media(mediaList)
			.metadata(metadata)
			.build();

		assertThat(document.getId()).isEqualTo("customId");
		assertThat(document.getContent()).isEqualTo("Test content");
		assertThat(document.getMedia()).isEqualTo(mediaList);
		assertThat(document.getMetadata()).isEqualTo(metadata);
	}

}
