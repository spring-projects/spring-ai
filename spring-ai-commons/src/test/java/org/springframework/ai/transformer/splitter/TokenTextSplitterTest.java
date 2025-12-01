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

package org.springframework.ai.transformer.splitter;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.springframework.ai.document.DefaultContentFormatter;
import org.springframework.ai.document.Document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Ricken Bazolo
 */
public class TokenTextSplitterTest {

	private final String SAMPLE_TEXT = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. "
			+ "Vestibulum volutpat augue et turpis facilisis, id porta ligula interdum. "
			+ "Proin condimentum justo sed lectus fermentum, a pretium orci iaculis. "
			+ "Mauris nec pharetra libero. Nulla facilisi. Sed consequat velit id eros volutpat dignissim.";

	@Test
	public void testTokenTextSplitterBuilderWithDefaultValues() {

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

		var tokenTextSplitter = new TokenTextSplitter();

		var chunks = tokenTextSplitter.apply(List.of(doc1, doc2));

		assertThat(chunks.size()).isEqualTo(2);

		// Doc 1
		assertThat(chunks.get(0).getText())
			.isEqualTo("In the end, writing arises when man realizes that memory is not enough.");
		// Doc 2
		assertThat(chunks.get(1).getText()).isEqualTo(
				"The most oppressive thing about the labyrinth is that you are constantly being forced to choose. It isn’t the lack of an exit, but the abundance of exits that is so disorienting.");

		assertThat(chunks.get(0).getMetadata()).containsKeys("key1", "key2").doesNotContainKeys("key3");
		assertThat(chunks.get(1).getMetadata()).containsKeys("key2", "key3").doesNotContainKeys("key1");
	}

	@Test
	public void testTokenTextSplitterBuilderWithAllFields() {

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

		var tokenTextSplitter = TokenTextSplitter.builder()
			.withChunkSize(10)
			.withMinChunkSizeChars(5)
			.withMinChunkLengthToEmbed(3)
			.withMaxNumChunks(50)
			.withKeepSeparator(true)
			.build();

		var chunks = tokenTextSplitter.apply(List.of(doc1, doc2));

		assertThat(chunks.size()).isEqualTo(6);

		// Doc 1
		assertThat(chunks.get(0).getText()).isEqualTo("In the end, writing arises when man realizes that");
		assertThat(chunks.get(1).getText()).isEqualTo("memory is not enough.");

		// Doc 2
		assertThat(chunks.get(2).getText()).isEqualTo("The most oppressive thing about the labyrinth is that you");
		assertThat(chunks.get(3).getText()).isEqualTo("are constantly being forced to choose.");
		assertThat(chunks.get(4).getText()).isEqualTo("It isn’t the lack of an exit, but");
		assertThat(chunks.get(5).getText()).isEqualTo("the abundance of exits that is so disorienting");

		// Verify that the original metadata is copied to all chunks (including
		// chunk-specific fields)
		assertThat(chunks.get(0).getMetadata()).containsKeys("key1", "key2", "parent_document_id", "chunk_index",
				"total_chunks");
		assertThat(chunks.get(1).getMetadata()).containsKeys("key1", "key2", "parent_document_id", "chunk_index",
				"total_chunks");
		assertThat(chunks.get(2).getMetadata()).containsKeys("key2", "key3", "parent_document_id", "chunk_index",
				"total_chunks");
		assertThat(chunks.get(3).getMetadata()).containsKeys("key2", "key3", "parent_document_id", "chunk_index",
				"total_chunks");

		// Verify chunk indices are correct
		assertThat(chunks.get(0).getMetadata().get("chunk_index")).isEqualTo(0);
		assertThat(chunks.get(1).getMetadata().get("chunk_index")).isEqualTo(1);
		assertThat(chunks.get(2).getMetadata().get("chunk_index")).isEqualTo(0);
		assertThat(chunks.get(3).getMetadata().get("chunk_index")).isEqualTo(1);

		assertThat(chunks.get(0).getMetadata()).containsKeys("key1", "key2").doesNotContainKeys("key3");
		assertThat(chunks.get(2).getMetadata()).containsKeys("key2", "key3").doesNotContainKeys("key1");
	}

	@Test
	void testSplitWithOverlap() {
		TokenTextSplitter splitter = TokenTextSplitter.builder()
			.withChunkSize(40)
			.withOverlapSize(10)
			.withMinChunkLengthToEmbed(5)
			.build();

		List<String> chunks = splitter.splitText(SAMPLE_TEXT);

		assertNotNull(chunks);
		assertTrue(chunks.size() > 1, "Text should be split into multiple chunks");

		// Compare overlapping tokens between consecutive chunks
		List<Integer> allTokens = splitter.getEncodedTokens(SAMPLE_TEXT);

		for (int i = 1; i < chunks.size(); i++) {
			List<Integer> prevTokens = splitter.getEncodedTokens(chunks.get(i - 1));
			List<Integer> currTokens = splitter.getEncodedTokens(chunks.get(i));

			int overlap = getOverlapSize(prevTokens, currTokens);

			// Allow some deviation due to punctuation or sentence trimming
			assertTrue(overlap >= 5 && overlap <= 15,
					"Expected ~10 overlapping tokens between chunks, but got " + overlap);
		}
	}

	@Test
	void testSplitWithoutOverlap() {
		TokenTextSplitter splitter = TokenTextSplitter.builder().withChunkSize(40).withOverlapSize(0).build();

		List<String> chunks = splitter.splitText(SAMPLE_TEXT);

		assertNotNull(chunks);
		assertTrue(chunks.size() > 1);

		for (int i = 1; i < chunks.size(); i++) {
			List<Integer> prev = splitter.getEncodedTokens(chunks.get(i - 1));
			List<Integer> curr = splitter.getEncodedTokens(chunks.get(i));

			assertTrue(noOverlap(prev, curr), "There should be no overlap between chunks");
		}
	}

	@Test
	void testEmptyText() {
		TokenTextSplitter splitter = TokenTextSplitter.builder().withChunkSize(50).withOverlapSize(10).build();

		List<String> chunks = splitter.splitText("   ");
		assertTrue(chunks.isEmpty(), "Empty or whitespace-only input should return no chunks");
	}

	/**
	 * Calculate the number of overlapping tokens between the end of the previous chunk
	 * and the start of the current chunk.
	 */
	private int getOverlapSize(List<Integer> prev, List<Integer> curr) {
		int maxOverlap = Math.min(prev.size(), curr.size());
		for (int i = maxOverlap; i > 0; i--) {
			if (prev.subList(prev.size() - i, prev.size()).equals(curr.subList(0, i))) {
				return i;
			}
		}
		return 0;
	}

	/**
	 * Check whether there is no overlap between the two token lists.
	 */
	private boolean noOverlap(List<Integer> prev, List<Integer> curr) {
		for (int len = Math.min(prev.size(), curr.size()); len > 0; len--) {
			if (prev.subList(prev.size() - len, prev.size()).equals(curr.subList(0, len))) {
				return false;
			}
		}
		return true;
	}

}
