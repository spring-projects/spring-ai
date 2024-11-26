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
import org.springframework.ai.model.Media;
import org.springframework.util.MimeTypeUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class DocumentTests {

	@Test
	void testScore() {
		Double score = 0.95;
		Document document = Document.builder().content("Test content").score(score).build();

		assertThat(document.getScore()).isEqualTo(score);
	}

	@Test
	void testNullScore() {
		Document document = Document.builder().content("Test content").score(null).build();

		assertThat(document.getScore()).isNull();
	}

	@Test
	void testMediaBuilderIsAdditive() {
		try {
			URL mediaUrl1 = new URL("http://type1");
			URL mediaUrl2 = new URL("http://type2");
			URL mediaUrl3 = new URL("http://type3");

			Media media1 = new Media(MimeTypeUtils.IMAGE_JPEG, mediaUrl1);
			Media media2 = new Media(MimeTypeUtils.IMAGE_JPEG, mediaUrl2);
			Media media3 = new Media(MimeTypeUtils.IMAGE_JPEG, mediaUrl3);

			Document document = Document.builder().media(media1).media(media2).media(List.of(media3)).build();

			assertThat(document.getMedia()).hasSize(3).containsExactly(media1, media2, media3);

		}
		catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	void testMutate() {
		List<Media> mediaList = getMediaList();
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("key", "value");
		Double score = 0.95;

		Document original = Document.builder()
			.id("customId")
			.content("Test content")
			.media(mediaList)
			.metadata(metadata)
			.score(score)
			.build();

		Document mutated = original.mutate().build();

		assertThat(mutated).isNotSameAs(original).usingRecursiveComparison().isEqualTo(original);
	}

	@Test
	void testEquals() {
		List<Media> mediaList = getMediaList();
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("key", "value");
		Double score = 0.95;

		Document doc1 = Document.builder()
			.id("customId")
			.content("Test content")
			.media(mediaList)
			.metadata(metadata)
			.score(score)
			.build();

		Document doc2 = Document.builder()
			.id("customId")
			.content("Test content")
			.media(mediaList)
			.metadata(metadata)
			.score(score)
			.build();

		Document differentDoc = Document.builder()
			.id("differentId")
			.content("Different content")
			.media(mediaList)
			.metadata(metadata)
			.score(score)
			.build();

		assertThat(doc1).isEqualTo(doc2).isNotEqualTo(differentDoc).isNotEqualTo(null).isNotEqualTo(new Object());

		assertThat(doc1.hashCode()).isEqualTo(doc2.hashCode());
	}

	@Test
	void testEmptyDocument() {
		Document emptyDoc = Document.builder().build();

		assertThat(emptyDoc.getContent()).isEqualTo(Document.EMPTY_TEXT).isEmpty();
		assertThat(emptyDoc.getMedia()).isEmpty();
		assertThat(emptyDoc.getMetadata()).isEmpty();
		assertThat(emptyDoc.getScore()).isNull();
	}

	@Test
	void testToString() {
		List<Media> mediaList = getMediaList();
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("key", "value");
		Double score = 0.95;

		Document document = Document.builder()
			.id("customId")
			.content("Test content")
			.media(mediaList)
			.metadata(metadata)
			.score(score)
			.build();

		String toString = document.toString();

		assertThat(toString).contains("id='customId'")
			.contains("content='Test content'")
			.contains("media=" + mediaList)
			.contains("metadata=" + metadata)
			.contains("score=" + score);
	}

	@Test
	void testToStringWithEmptyDocument() {
		Document emptyDoc = Document.builder().build();

		String toString = emptyDoc.toString();

		assertThat(toString).contains("content=''").contains("media=[]").contains("metadata={}").contains("score=null");
	}

	private static List<Media> getMediaList() {
		try {
			URL mediaUrl1 = new URL("http://type1");
			URL mediaUrl2 = new URL("http://type2");
			Media media1 = new Media(MimeTypeUtils.IMAGE_JPEG, mediaUrl1);
			Media media2 = new Media(MimeTypeUtils.IMAGE_JPEG, mediaUrl2);
			return List.of(media1, media2);
		}
		catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

}
