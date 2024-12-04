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
import static org.junit.jupiter.api.Assertions.assertThrows;

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
	void testMutate() {
		Media media = getMedia();
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("key", "value");
		Double score = 0.95;

		Document original = Document.builder()
			.id("customId")
			.content("Test content")
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

		Document doc1 = Document.builder()
			.id("customId")
			.text("Test text")
			.metadata(metadata)
			.score(score)
			.build();

		Document doc2 = Document.builder()
			.id("customId")
			.text("Test text")
			.metadata(metadata)
			.score(score)
			.build();

		Document differentDoc = Document.builder()
			.id("differentId")
			.content("Different content")
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
			.content("Test content")
			.media(null)
			.metadata(metadata)
			.score(score)
			.build();

		String toString = document.toString();

		assertThat(toString).contains("id='customId'")
			.contains("content='Test content'")
			.contains("metadata=" + metadata)
			.contains("score=" + score);
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
