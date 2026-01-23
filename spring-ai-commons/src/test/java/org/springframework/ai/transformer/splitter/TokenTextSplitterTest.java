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

import org.springframework.ai.document.DefaultContentFormatter;
import org.springframework.ai.document.Document;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ricken Bazolo
 */
public class TokenTextSplitterTest {

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
				+ "being forced to choose. It isn't the lack of an exit, but the abundance of exits that is so disorienting.",
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
		assertThat(chunks.get(4).getText()).isEqualTo("It isn't the lack of an exit, but");
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
	public void testSmallTextWithPunctuationShouldNotSplit() {
		TokenTextSplitter splitter = TokenTextSplitter.builder()
			.withKeepSeparator(true)
			.withChunkSize(10000)
			.withMinChunkSizeChars(10)
			.build();

		Document testDoc = new Document(
				"Hi. This is a small text without one of the ending chars. It is splitted into multiple chunks but shouldn't");
		List<Document> splitted = splitter.split(testDoc);

		// Should be a single chunk since the text is well below the chunk size
		assertThat(splitted.size()).isEqualTo(1);
		assertThat(splitted.get(0).getText()).isEqualTo(
				"Hi. This is a small text without one of the ending chars. It is splitted into multiple chunks but shouldn't");
	}

	@Test
	public void testLargeTextStillSplitsAtPunctuation() {
		// Verify that punctuation-based splitting still works when text exceeds chunk
		// size
		TokenTextSplitter splitter = TokenTextSplitter.builder()
			.withKeepSeparator(true)
			.withChunkSize(15)
			.withMinChunkSizeChars(10)
			.build();

		// This text has multiple sentences and will exceed 15 tokens
		Document testDoc = new Document(
				"This is the first sentence with enough words. This is the second sentence. And this is the third sentence.");
		List<Document> splitted = splitter.split(testDoc);

		// Should split into multiple chunks at punctuation marks
		assertThat(splitted.size()).isGreaterThan(1);

		// Verify first chunk ends with punctuation
		assertThat(splitted.get(0).getText()).endsWith(".");
	}

	@Test
	public void testTokenTextSplitterWithCustomPunctuationMarks() {
		var contentFormatter1 = DefaultContentFormatter.defaultConfig();
		var contentFormatter2 = DefaultContentFormatter.defaultConfig();

		assertThat(contentFormatter1).isNotSameAs(contentFormatter2);

		var doc1 = new Document("Here, we set custom punctuation marks。？！. We just want to test it works or not？");
		doc1.setContentFormatter(contentFormatter1);

		var doc2 = new Document("And more, we add protected method getLastPunctuationIndex in TokenTextSplitter class！"
				+ "The subclasses can override this method to achieve their own business logic。We just want to test it works or not？");
		doc2.setContentFormatter(contentFormatter2);

		var tokenTextSplitter = TokenTextSplitter.builder()
			.withChunkSize(10)
			.withMinChunkSizeChars(5)
			.withMinChunkLengthToEmbed(3)
			.withMaxNumChunks(50)
			.withKeepSeparator(true)
			.withPunctuationMarks(List.of('。', '？', '！'))
			.build();

		var chunks = tokenTextSplitter.apply(List.of(doc1, doc2));

		assertThat(chunks.size()).isEqualTo(7);

		// Doc 1
		assertThat(chunks.get(0).getText()).isEqualTo("Here, we set custom punctuation marks。？！");
		assertThat(chunks.get(1).getText()).isEqualTo(". We just want to test it works or not");

		// Doc 2
		assertThat(chunks.get(2).getText()).isEqualTo("And more, we add protected method getLastPunctuation");
		assertThat(chunks.get(3).getText()).isEqualTo("Index in TokenTextSplitter class！");
		assertThat(chunks.get(4).getText()).isEqualTo("The subclasses can override this method to achieve their own");
		assertThat(chunks.get(5).getText()).isEqualTo("business logic。");
		assertThat(chunks.get(6).getText()).isEqualTo("We just want to test it works or not？");

	}

}
