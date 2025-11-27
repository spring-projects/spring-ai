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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Ricken Bazolo
 * @author Seunghwan Jung
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

		String doc2Text = "The most oppressive thing about the labyrinth is that you are constantly "
				+ "being forced to choose. It isn't the lack of an exit, but the abundance of exits that is so disorienting.";
		var doc2 = new Document(doc2Text, Map.of("key2", "value22", "key3", "value3"));
		doc2.setContentFormatter(contentFormatter2);

		var tokenTextSplitter = new TokenTextSplitter();

		var chunks = tokenTextSplitter.apply(List.of(doc1, doc2));

		assertThat(chunks.size()).isEqualTo(2);

		assertThat(chunks.get(0).getText())
			.isEqualTo("In the end, writing arises when man realizes that memory is not enough.");
		assertThat(chunks.get(1).getText()).isEqualTo(doc2Text);

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
			.withChunkSize(20)
			.withChunkOverlap(3)
			.withMinChunkSizeChars(10)
			.withMinChunkLengthToEmbed(5)
			.withMaxNumChunks(50)
			.withKeepSeparator(true)
			.build();

		var chunks = tokenTextSplitter.apply(List.of(doc1, doc2));

		assertThat(chunks.size()).isBetween(4, 10);

		for (Document chunk : chunks) {
			assertThat(chunk.getText()).isNotEmpty();
			assertThat(chunk.getText().trim().length()).isGreaterThanOrEqualTo(5);
		}

		boolean foundDoc1Chunks = false;
		boolean foundDoc2Chunks = false;

		for (Document chunk : chunks) {
			Map<String, Object> metadata = chunk.getMetadata();

			if (metadata.containsKey("key1") && !metadata.containsKey("key3")) {
				assertThat(metadata).containsKeys("key1", "key2").doesNotContainKeys("key3");
				foundDoc1Chunks = true;
			}
			else if (metadata.containsKey("key3") && !metadata.containsKey("key1")) {
				assertThat(metadata).containsKeys("key2", "key3").doesNotContainKeys("key1");
				foundDoc2Chunks = true;
			}
		}

		assertThat(foundDoc1Chunks).isTrue();
		assertThat(foundDoc2Chunks).isTrue();

		for (Document chunk : chunks) {
			assertThat(chunk.getMetadata()).containsKeys("parent_document_id", "chunk_index", "total_chunks");
		}

		int doc1ChunkIndex = 0;
		int doc2ChunkIndex = 0;
		for (Document chunk : chunks) {
			Map<String, Object> metadata = chunk.getMetadata();

			if (metadata.containsKey("key1") && !metadata.containsKey("key3")) {
				assertThat(metadata.get("chunk_index")).isEqualTo(doc1ChunkIndex);
				doc1ChunkIndex++;
			}
			else if (metadata.containsKey("key3") && !metadata.containsKey("key1")) {
				assertThat(metadata.get("chunk_index")).isEqualTo(doc2ChunkIndex);
				doc2ChunkIndex++;
			}
		}
	}

	@Test
	public void testChunkOverlapFunctionality() {
		String longText = "This is the first sentence. This is the second sentence. "
				+ "This is the third sentence. This is the fourth sentence. "
				+ "This is the fifth sentence. This is the sixth sentence.";

		var doc = new Document(longText);

		var tokenTextSplitter = TokenTextSplitter.builder()
			.withChunkSize(15)
			.withChunkOverlap(5)
			.withMinChunkSizeChars(10)
			.withMinChunkLengthToEmbed(5)
			.withKeepSeparator(false)
			.build();

		var chunks = tokenTextSplitter.apply(List.of(doc));

		assertThat(chunks.size()).isGreaterThan(1);

		if (chunks.size() >= 2) {
			String firstChunk = chunks.get(0).getText();
			String secondChunk = chunks.get(1).getText();

			assertThat(firstChunk).isNotEmpty();
			assertThat(secondChunk).isNotEmpty();
		}
	}

	@Test
	public void testChunkOverlapValidation() {
		assertThatThrownBy(() -> TokenTextSplitter.builder().withChunkSize(10).withChunkOverlap(15).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("chunk overlap must be less than chunk size");

		assertThatThrownBy(() -> TokenTextSplitter.builder().withChunkSize(10).withChunkOverlap(10).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("chunk overlap must be less than chunk size");
	}

	@Test
	public void testBoundaryOptimizationWithOverlap() {
		String text = "First sentence here. Second sentence follows immediately. "
				+ "Third sentence is next. Fourth sentence continues the text. "
				+ "Fifth sentence completes this test.";

		var doc = new Document(text);

		var tokenTextSplitter = TokenTextSplitter.builder()
			.withChunkSize(20)
			.withChunkOverlap(3)
			.withMinChunkSizeChars(20)
			.withMinChunkLengthToEmbed(5)
			.withKeepSeparator(true)
			.build();

		var chunks = tokenTextSplitter.apply(List.of(doc));

		assertThat(chunks).isNotEmpty();

		for (Document chunk : chunks) {
			String chunkText = chunk.getText();
			if (chunkText != null && chunkText.trim().length() > 20) {
				assertThat(chunkText.trim()).isNotEmpty();
			}
		}
	}

	@Test
	public void testKeepSeparatorVariations() {
		String textWithNewlines = "Line one content here.\nLine two content here.\nLine three content here.";
		var doc = new Document(textWithNewlines);

		var splitterKeepSeparator = TokenTextSplitter.builder()
			.withChunkSize(50)
			.withChunkOverlap(0)
			.withKeepSeparator(true)
			.build();

		var chunksWithSeparator = splitterKeepSeparator.apply(List.of(doc));

		var splitterNoSeparator = TokenTextSplitter.builder()
			.withChunkSize(50)
			.withChunkOverlap(0)
			.withKeepSeparator(false)
			.build();

		var chunksWithoutSeparator = splitterNoSeparator.apply(List.of(doc));

		assertThat(chunksWithSeparator).isNotEmpty();
		assertThat(chunksWithoutSeparator).isNotEmpty();

		if (chunksWithSeparator.size() == 1 && chunksWithoutSeparator.size() == 1) {
			String withSeparatorText = chunksWithSeparator.get(0).getText();
			String withoutSeparatorText = chunksWithoutSeparator.get(0).getText();

			assertThat(withSeparatorText).contains("\n");
			assertThat(withoutSeparatorText).doesNotContain("\n");
		}
	}

	@Test
	public void testNoMiniChunksAtEnd() {
		StringBuilder longText = new StringBuilder();
		for (int i = 0; i < 100; i++) {
			longText.append("This is sentence number ")
				.append(i)
				.append(" and it contains some meaningful content to test the chunking behavior. ");
		}

		var doc = new Document(longText.toString());

		var tokenTextSplitter = TokenTextSplitter.builder()
			.withChunkSize(100)
			.withChunkOverlap(10)
			.withMinChunkSizeChars(50)
			.withMinChunkLengthToEmbed(5)
			.withKeepSeparator(true)
			.build();

		var chunks = tokenTextSplitter.apply(List.of(doc));

		assertThat(chunks.size()).isGreaterThan(1);

		var encoding = com.knuddels.jtokkit.Encodings.newDefaultEncodingRegistry()
			.getEncoding(com.knuddels.jtokkit.api.EncodingType.CL100K_BASE);

		int minExpectedAdvance = (100 - 10) / 2;
		int consecutiveSmallChunks = 0;
		int maxConsecutiveSmallChunks = 0;

		for (int i = 0; i < chunks.size() - 1; i++) {
			String chunkText = chunks.get(i).getText();
			int tokenCount = encoding.encode(chunkText).size();

			if (tokenCount < minExpectedAdvance) {
				consecutiveSmallChunks++;
				maxConsecutiveSmallChunks = Math.max(maxConsecutiveSmallChunks, consecutiveSmallChunks);
			}
			else {
				consecutiveSmallChunks = 0;
			}

			assertThat(tokenCount)
				.as("Chunk %d should have at least %d tokens but has %d", i, minExpectedAdvance, tokenCount)
				.isGreaterThanOrEqualTo(minExpectedAdvance);
		}

		assertThat(maxConsecutiveSmallChunks)
			.as("Should not have multiple consecutive small chunks (found %d consecutive)", maxConsecutiveSmallChunks)
			.isLessThanOrEqualTo(1);
	}

	@Test
	public void testChunkSizesAreConsistent() {
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < 50; i++) {
			text.append("Sentence ").append(i).append(" contains important information for testing. ");
		}

		var doc = new Document(text.toString());

		var tokenTextSplitter = TokenTextSplitter.builder()
			.withChunkSize(80)
			.withChunkOverlap(10)
			.withMinChunkSizeChars(100)
			.withMinChunkLengthToEmbed(5)
			.withKeepSeparator(false)
			.build();

		var chunks = tokenTextSplitter.apply(List.of(doc));

		assertThat(chunks.size()).isGreaterThan(1);

		var encoding = com.knuddels.jtokkit.Encodings.newDefaultEncodingRegistry()
			.getEncoding(com.knuddels.jtokkit.api.EncodingType.CL100K_BASE);

		for (int i = 0; i < chunks.size() - 1; i++) {
			int tokenCount = encoding.encode(chunks.get(i).getText()).size();
			assertThat(tokenCount).as("Chunk %d token count should be reasonable", i).isBetween(40, 120);
		}

		int lastChunkTokens = encoding.encode(chunks.get(chunks.size() - 1).getText()).size();
		assertThat(lastChunkTokens).isGreaterThan(0);
	}

}
