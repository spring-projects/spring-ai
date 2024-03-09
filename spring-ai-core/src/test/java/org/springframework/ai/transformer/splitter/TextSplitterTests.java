/*
 * Copyright 2023 - 2024 the original author or authors.
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
package org.springframework.ai.transformer.splitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import org.springframework.ai.document.DefaultContentFormatter;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;

/**
 * @author Christian Tzolov
 */
public class TextSplitterTests {

	static TextSplitter testTextSplitter = new TextSplitter() {

		@Override
		protected List<String> splitText(String text) {
			int chuckSize = text.length() / 2;

			List<String> chunks = new ArrayList<>();

			chunks.add(text.substring(0, chuckSize));
			chunks.add(text.substring(chuckSize, text.length()));

			return chunks;
		}
	};

	@Test
	public void testSplitText() {

		var contentFormatter1 = DefaultContentFormatter.defaultConfig();
		var contentFormatter2 = DefaultContentFormatter.defaultConfig();

		assertThat(contentFormatter1).isNotSameAs(contentFormatter2);

		var doc1 = new Document("In the end, writing arises when man realizes that memory is not enough.",
				Map.of("key1", "value1", "key2", "value2"));
		doc1.setContentFormatter(contentFormatter1);

		var doc2 = new Document("The most oppressive thing about the labyrinth is that you are constantly "
				+ "being forced to choose. It isn’t the lack of an exit, but the abundance of exits that is so disorienting.",
				Map.of("key2", "value22", "key3", "value3"));
		doc2.setContentFormatter(contentFormatter2);

		List<Document> chunks = testTextSplitter.apply(List.of(doc1, doc2));

		assertThat(testTextSplitter.isCopyContentFormatter()).isTrue();

		assertThat(chunks).hasSize(4);

		// Doc1 chunks:
		assertThat(chunks.get(0).getContent()).isEqualTo("In the end, writing arises when man");
		assertThat(chunks.get(1).getContent()).isEqualTo(" realizes that memory is not enough.");

		// Doc2 chunks:
		assertThat(chunks.get(2).getContent())
			.isEqualTo("The most oppressive thing about the labyrinth is that you are constantly being forced to ");
		assertThat(chunks.get(3).getContent())
			.isEqualTo("choose. It isn’t the lack of an exit, but the abundance of exits that is so disorienting.");

		// Verify that the same, merged metadata is copied to all chunks.
		assertThat(chunks.get(0).getMetadata()).isEqualTo(chunks.get(1).getMetadata());
		assertThat(chunks.get(0).getMetadata()).isEqualTo(chunks.get(2).getMetadata());
		assertThat(chunks.get(0).getMetadata()).isEqualTo(chunks.get(3).getMetadata());
		assertThat(chunks.get(0).getMetadata()).containsKeys("key1", "key2", "key3");

		// Verify that the content formatters are copied from the parents to the chunks.
		// doc1 -> chunk0, chunk1 and doc2 -> chunk2, chunk3
		assertThat(chunks.get(0).getContentFormatter()).isSameAs(contentFormatter1);
		assertThat(chunks.get(1).getContentFormatter()).isSameAs(contentFormatter1);

		assertThat(chunks.get(2).getContentFormatter()).isSameAs(contentFormatter2);
		assertThat(chunks.get(3).getContentFormatter()).isSameAs(contentFormatter2);

		// Disable copy content formatters
		testTextSplitter.setCopyContentFormatter(false);
		chunks = testTextSplitter.apply(List.of(doc1, doc2));

		assertThat(chunks.get(0).getContentFormatter()).isNotSameAs(contentFormatter1);
		assertThat(chunks.get(1).getContentFormatter()).isNotSameAs(contentFormatter1);

		assertThat(chunks.get(2).getContentFormatter()).isNotSameAs(contentFormatter2);
		assertThat(chunks.get(3).getContentFormatter()).isNotSameAs(contentFormatter2);

	}

}
