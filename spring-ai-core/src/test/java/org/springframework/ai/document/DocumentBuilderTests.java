/*
 * Copyright 2024 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.document;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.Media;
import org.springframework.ai.document.id.IdGenerator;
import org.springframework.util.MimeTypeUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DocumentBuilderTests {

	private Document.Builder builder;

	@BeforeEach
	void setUp() {
		builder = Document.builder();
	}

	@Test
	void testWithIdGenerator() {
		IdGenerator mockGenerator = new IdGenerator() {
			@Override
			public String generateId(Object... contents) {
				return "mockedId";
			}
		};

		Document.Builder result = builder.withIdGenerator(mockGenerator);

		assertThat(result).isSameAs(builder);

		Document document = result.withContent("Test content").withMetadata("key", "value").build();

		assertThat(document.getId()).isEqualTo("mockedId");
	}

	@Test
	void testWithIdGeneratorNull() {
		assertThatThrownBy(() -> builder.withIdGenerator(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("idGenerator must not be null");
	}

	@Test
	void testWithId() {
		Document.Builder result = builder.withId("testId");

		assertThat(result).isSameAs(builder);
		assertThat(result.build().getId()).isEqualTo("testId");
	}

	@Test
	void testWithIdNullOrEmpty() {
		assertThatThrownBy(() -> builder.withId(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("id must not be null or empty");

		assertThatThrownBy(() -> builder.withId("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("id must not be null or empty");
	}

	@Test
	void testWithContent() {
		Document.Builder result = builder.withContent("Test content");

		assertThat(result).isSameAs(builder);
		assertThat(result.build().getContent()).isEqualTo("Test content");
	}

	@Test
	void testWithContentNull() {
		assertThatThrownBy(() -> builder.withContent(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("content must not be null");
	}

	@Test
	void testWithMediaList() {
		List<Media> mediaList = getMediaList();
		Document.Builder result = builder.withMedia(mediaList);

		assertThat(result).isSameAs(builder);
		assertThat(result.build().getMedia()).isEqualTo(mediaList);
	}

	@Test
	void testWithMediaListNull() {
		assertThatThrownBy(() -> builder.withMedia((List<Media>) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("media must not be null");
	}

	@Test
	void testWithMediaSingle() throws MalformedURLException {
		URL mediaUrl = new URL("http://test");
		Media media = new Media(MimeTypeUtils.IMAGE_JPEG, mediaUrl);

		Document.Builder result = builder.withMedia(media);

		assertThat(result).isSameAs(builder);
		assertThat(result.build().getMedia()).contains(media);
	}

	@Test
	void testWithMediaSingleNull() {
		assertThatThrownBy(() -> builder.withMedia((Media) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("media must not be null");
	}

	@Test
	void testWithMetadataMap() {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("key1", "value1");
		metadata.put("key2", 2);
		Document.Builder result = builder.withMetadata(metadata);

		assertThat(result).isSameAs(builder);
		assertThat(result.build().getMetadata()).isEqualTo(metadata);
	}

	@Test
	void testWithMetadataMapNull() {
		assertThatThrownBy(() -> builder.withMetadata((Map<String, Object>) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("metadata must not be null");
	}

	@Test
	void testWithMetadataKeyValue() {
		Document.Builder result = builder.withMetadata("key", "value");

		assertThat(result).isSameAs(builder);
		assertThat(result.build().getMetadata()).containsEntry("key", "value");
	}

	@Test
	void testWithMetadataKeyValueNull() {
		assertThatThrownBy(() -> builder.withMetadata(null, "value")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("key must not be null");

		assertThatThrownBy(() -> builder.withMetadata("key", null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("value must not be null");
	}

	@Test
	void testBuildWithoutId() {
		Document document = builder.withContent("Test content").build();

		assertThat(document.getId()).isNotNull().isNotEmpty();
		assertThat(document.getContent()).isEqualTo("Test content");
	}

	@Test
	void testBuildWithAllProperties() throws MalformedURLException {

		List<Media> mediaList = getMediaList();
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("key", "value");

		Document document = builder.withId("customId")
			.withContent("Test content")
			.withMedia(mediaList)
			.withMetadata(metadata)
			.build();

		assertThat(document.getId()).isEqualTo("customId");
		assertThat(document.getContent()).isEqualTo("Test content");
		assertThat(document.getMedia()).isEqualTo(mediaList);
		assertThat(document.getMetadata()).isEqualTo(metadata);
	}

	private static List<Media> getMediaList() {
		try {
			URL mediaUrl1 = new URL("http://type1");
			URL mediaUrl2 = new URL("http://type2");
			Media media1 = new Media(MimeTypeUtils.IMAGE_JPEG, mediaUrl1);
			Media media2 = new Media(MimeTypeUtils.IMAGE_JPEG, mediaUrl2);
			List<Media> mediaList = List.of(media1, media2);
			return mediaList;
		}
		catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}

	}

}
