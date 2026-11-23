/*
 * Copyright 2023-present the original author or authors.
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

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.EncodingType;
import org.junit.jupiter.api.Test;

import org.springframework.ai.document.DefaultContentFormatter;
import org.springframework.ai.document.Document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * @author Ricken Bazolo
 * @author Seunghwan Jung
 * @author Jemin Huh
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

		var tokenTextSplitter = TokenTextSplitter.builder().build();

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
	public void testChunkOverlapFunctionality() {
		// Each word is one token, and a large minChunkSizeChars turns off the
		// punctuation trim, so chunks are cut purely by token count
		var doc = new Document("one two three four five six seven eight nine ten eleven twelve");

		var splitterNoOverlap = TokenTextSplitter.builder()
			.withChunkSize(5)
			.withMinChunkSizeChars(1000)
			.withMinChunkLengthToEmbed(0)
			.build();

		var splitterWithOverlap = TokenTextSplitter.builder()
			.withChunkSize(5)
			.withChunkOverlap(2)
			.withMinChunkSizeChars(1000)
			.withMinChunkLengthToEmbed(0)
			.build();

		// Expect: chunks follow each other with no shared words
		assertThat(splitterNoOverlap.apply(List.of(doc))).extracting(Document::getText)
			.containsExactly("one two three four five", "six seven eight nine ten", "eleven twelve");

		// Expect: each chunk starts with the last two words of the chunk before it
		// @formatter:off
		// chunk 1: one two three [four five]
		// chunk 2:               [four five] six [seven eight]
		// chunk 3:                               [seven eight] nine [ten eleven]
		// chunk 4:                                                  [ten eleven] twelve
		// @formatter:on
		assertThat(splitterWithOverlap.apply(List.of(doc))).extracting(Document::getText)
			.containsExactly("one two three four five", "four five six seven eight", "seven eight nine ten eleven",
					"ten eleven twelve");
	}

	@Test
	public void testChunkOverlapKeepsBoundaryTextWhole() {
		var doc = new Document("The consumer kept failing in production. "
				+ "The fix is to set the timeout to 30 seconds on the consumer. Then restart the service.");
		String fact = "set the timeout to 30 seconds";

		var splitterNoOverlap = TokenTextSplitter.builder()
			.withChunkSize(12)
			.withMinChunkSizeChars(1000)
			.withMinChunkLengthToEmbed(0)
			.build();

		var splitterWithOverlap = TokenTextSplitter.builder()
			.withChunkSize(12)
			.withChunkOverlap(8)
			.withMinChunkSizeChars(1000)
			.withMinChunkLengthToEmbed(0)
			.build();

		// Expect: without overlap the chunk boundary falls inside the fact, so no chunk
		// holds it whole ("... The fix is to set" | "the timeout to 30 seconds ...")
		assertThat(splitterNoOverlap.apply(List.of(doc))).extracting(Document::getText)
			.noneMatch(text -> text.contains(fact));

		// Expect: with overlap at least one chunk holds the whole fact
		assertThat(splitterWithOverlap.apply(List.of(doc))).extracting(Document::getText)
			.anyMatch(text -> text.contains(fact));
	}

	@Test
	public void testChunkOverlapKeepsRemainingTextWhenMaxNumChunksReached() {
		var doc = new Document("one two three four five six seven eight nine ten eleven twelve");

		var tokenTextSplitter = TokenTextSplitter.builder()
			.withChunkSize(5)
			.withChunkOverlap(2)
			.withMaxNumChunks(2)
			.withMinChunkSizeChars(1000)
			.withMinChunkLengthToEmbed(0)
			.build();

		// Expect: after two chunks the limit is reached, and the rest of the text,
		// starting with the overlap, is kept as one final chunk instead of dropped
		assertThat(tokenTextSplitter.apply(List.of(doc))).extracting(Document::getText)
			.containsExactly("one two three four five", "four five six seven eight",
					"seven eight nine ten eleven twelve");
	}

	@Test
	public void testDroppedChunksCountTowardMaxNumChunks() {
		var tokenTextSplitter = TokenTextSplitter.builder()
			.withChunkSize(2)
			.withMinChunkSizeChars(1000)
			.withMinChunkLengthToEmbed(15)
			.withMaxNumChunks(3)
			.build();

		var chunks = tokenTextSplitter.apply(List.of(new Document("one two three four five six seven eight nine ten")));

		// Expect: the first three two-word chunks are dropped as too short but still
		// count toward the limit, so the rest of the text is kept as the final chunk
		assertThat(chunks).extracting(Document::getText).containsExactly("seven eight nine ten");
	}

	@Test
	@SuppressWarnings("removal")
	public void testSubclassOverrideOfDoSplitIsStillCalled() {
		var tokenTextSplitter = new TokenTextSplitter() {

			@Override
			protected List<String> doSplit(String text, int chunkSize) {
				return List.of("custom split");
			}

		};

		// Expect: the two-argument doSplit is still the method splitText calls, so
		// existing overrides keep working
		assertThat(tokenTextSplitter.apply(List.of(new Document("some text")))).extracting(Document::getText)
			.containsExactly("custom split");
	}

	@Test
	public void testChunkOverlapValidation() {
		// chunkOverlap must be zero or more and strictly less than chunkSize
		assertThatIllegalArgumentException()
			.isThrownBy(() -> TokenTextSplitter.builder().withChunkSize(10).withChunkOverlap(15).build())
			.withMessage("chunk overlap must be less than chunk size");

		assertThatIllegalArgumentException()
			.isThrownBy(() -> TokenTextSplitter.builder().withChunkSize(10).withChunkOverlap(10).build())
			.withMessage("chunk overlap must be less than chunk size");

		assertThatIllegalArgumentException().isThrownBy(() -> TokenTextSplitter.builder().withChunkOverlap(-1).build())
			.withMessage("chunk overlap must not be negative");

		// The largest valid overlap builds fine
		assertThat(TokenTextSplitter.builder().withChunkSize(10).withChunkOverlap(9).build()).isNotNull();
	}

	@Test
	public void testBoundaryOptimizationWithOverlap() {
		String text = "First sentence here. Second sentence follows immediately. "
				+ "Third sentence is next. Fourth sentence continues the text. "
				+ "Fifth sentence completes this test.";

		var doc = new Document(text);

		var tokenTextSplitter = TokenTextSplitter.builder()
			.withChunkSize(22)
			.withChunkOverlap(3)
			.withMinChunkSizeChars(20)
			.withMinChunkLengthToEmbed(5)
			.withKeepSeparator(true)
			.build();

		var chunks = tokenTextSplitter.apply(List.of(doc));

		// Expect: the first 22 tokens end with "the text. Fifth sentence", so the first
		// chunk is trimmed back to "the text.", and the next chunk starts with that
		// chunk's last three tokens (" the text.")
		assertThat(chunks).extracting(Document::getText)
			.containsExactly(
					"First sentence here. Second sentence follows immediately. Third sentence is next. Fourth sentence continues the text.",
					"the text. Fifth sentence completes this test.");
	}

	@Test
	public void testKeepSeparatorVariations() {
		// The splitter replaces System.lineSeparator(), so use it to keep the test
		// platform independent
		String newline = System.lineSeparator();
		String textWithNewlines = "Line one content here." + newline + "Line two content here." + newline
				+ "Line three content here.";
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

		// Expect: the text fits in one chunk; line breaks are kept or replaced by spaces
		assertThat(chunksWithSeparator).extracting(Document::getText).containsExactly(textWithNewlines);
		assertThat(chunksWithoutSeparator).extracting(Document::getText)
			.containsExactly("Line one content here. Line two content here. Line three content here.");
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

		// Expect: no trailing chunk only repeats text from the chunk before it, even
		// though the text ends with whitespace after the last sentence
		for (int i = 1; i < chunks.size(); i++) {
			assertThat(chunks.get(i - 1).getText()).as("chunk %d only repeats chunk %d", i, i - 1)
				.doesNotContain(chunks.get(i).getText());
		}

		// Expect: the last chunk ends with the last sentence, so nothing is lost
		assertThat(chunks.get(chunks.size() - 1).getText()).endsWith(
				"This is sentence number 99 and it contains some meaningful content to test the chunking behavior.");
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

		// Expect: no chunk, including the last one, is larger than the chunk size,
		// even though each chunk also repeats tokens from the one before it
		var encoding = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
		for (int i = 0; i < chunks.size(); i++) {
			assertThat(encoding.encode(chunks.get(i).getText()).size()).as("chunk %d token count", i)
				.isLessThanOrEqualTo(80);
		}
	}

	@Test
	public void testNoTextLostWithSparsePunctuation() {
		// One early sentence end, then a long run with no punctuation, so the sentence
		// trim makes the first chunk much shorter than the chunk size
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < 70; i++) {
			text.append("word").append(i).append(' ');
		}
		text.append(". ");
		for (int i = 0; i < 1500; i++) {
			text.append("tok").append(i).append(' ');
		}

		for (int overlap : new int[] { 0, 50, 200 }) {
			var splitter = TokenTextSplitter.builder().withChunkOverlap(overlap).build();
			var chunks = splitter.apply(List.of(new Document(text.toString())));

			// Expect: every part of the text is in at least one chunk
			assertNoTextLost(text.toString(), chunks);
		}
	}

	private static void assertNoTextLost(String text, List<Document> chunks) {
		int coveredUpTo = 0;
		int searchFrom = 0;
		for (Document chunk : chunks) {
			int start = text.indexOf(chunk.getText(), searchFrom);
			assertThat(start).as("chunk not found in the original text: %s", chunk.getText()).isNotNegative();
			assertThat(text.substring(coveredUpTo, Math.max(coveredUpTo, start))).as("text lost before chunk")
				.isBlank();
			coveredUpTo = Math.max(coveredUpTo, start + chunk.getText().length());
			searchFrom = start + 1;
		}
		assertThat(text.substring(coveredUpTo)).as("text lost after the last chunk").isBlank();
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
	public void testLastChunkIsNotSplitAtPunctuation() {
		TokenTextSplitter splitter = TokenTextSplitter.builder()
			.withChunkSize(10)
			.withMinChunkSizeChars(5)
			.withMinChunkLengthToEmbed(0)
			.build();

		var chunks = splitter
			.apply(List.of(new Document("one two three four five six seven eight nine ten eleven. twelve thirteen")));

		// Expect: the remaining text fits in one chunk, so it is kept whole instead of
		// being cut after "eleven."
		assertThat(chunks).extracting(Document::getText)
			.containsExactly("one two three four five six seven eight nine ten", "eleven. twelve thirteen");
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

	@Test
	public void testChunkOverlapWithCustomPunctuationMarks() {
		var doc = new Document("red green blue; one two. black white gray; three four. pink brown gold; five six.");

		var tokenTextSplitter = TokenTextSplitter.builder()
			.withChunkSize(10)
			.withChunkOverlap(2)
			.withMinChunkSizeChars(5)
			.withMinChunkLengthToEmbed(0)
			.withPunctuationMarks(List.of(';'))
			.build();

		// Expect: chunks are cut at the configured ';', never at '.', and each chunk
		// starts with the last two tokens of the chunk before it (e.g. " blue;")
		assertThat(tokenTextSplitter.apply(List.of(doc))).extracting(Document::getText)
			.containsExactly("red green blue;", "blue; one two. black white gray;",
					"gray; three four. pink brown gold;", "gold; five six.");
	}

	@Test
	public void testTokenTextSplitterWithNullEncodingTypeThrows() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> TokenTextSplitter.builder().withEncodingType(null).build())
			.withMessage("encodingType must not be null");
	}

	@Test
	public void testTokenTextSplitterWithZeroChunkSizeThrows() {
		// A chunkSize of 0 previously caused doSplit to loop forever; it must now fail
		// fast.
		assertThatIllegalArgumentException().isThrownBy(() -> TokenTextSplitter.builder().withChunkSize(0).build())
			.withMessage("chunkSize must be greater than zero");
	}

	@Test
	public void testTokenTextSplitterWithNegativeChunkSizeThrows() {
		assertThatIllegalArgumentException().isThrownBy(() -> TokenTextSplitter.builder().withChunkSize(-1).build())
			.withMessage("chunkSize must be greater than zero");
	}

	@Test
	public void testTokenTextSplitterWithNonPositiveMaxNumChunksThrows() {
		assertThatIllegalArgumentException().isThrownBy(() -> TokenTextSplitter.builder().withMaxNumChunks(0).build())
			.withMessage("maxNumChunks must be greater than zero");
	}

	@Test
	public void testTokenTextSplitterWithNegativeMinChunkSizeCharsThrows() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> TokenTextSplitter.builder().withMinChunkSizeChars(-1).build())
			.withMessage("minChunkSizeChars must not be negative");
	}

	@Test
	public void testTokenTextSplitterWithNegativeMinChunkLengthToEmbedThrows() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> TokenTextSplitter.builder().withMinChunkLengthToEmbed(-1).build())
			.withMessage("minChunkLengthToEmbed must not be negative");
	}

	@Test
	public void testTokenTextSplitterWithChunkSizeOneTerminates() {
		// chunkSize == 1 is the smallest valid value: it must terminate and produce
		// chunks.
		var splitter = TokenTextSplitter.builder().withChunkSize(1).withMinChunkLengthToEmbed(0).build();

		var chunks = splitter.apply(List.of(new Document("Hello world from Spring AI")));

		assertThat(chunks).isNotEmpty();
	}

	@Test
	public void testTokenTextSplitterWithDifferentEncodingTypes() {
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

		var cl100kSplitter = TokenTextSplitter.builder()
			.withEncodingType(EncodingType.CL100K_BASE)
			.withChunkSize(10)
			.withMinChunkSizeChars(5)
			.withMinChunkLengthToEmbed(3)
			.withMaxNumChunks(50)
			.withKeepSeparator(true)
			.build();

		var cl100kChunks = cl100kSplitter.apply(List.of(doc1, doc2));

		assertThat(cl100kChunks.size()).isEqualTo(6);

		// Doc 1
		assertThat(cl100kChunks.get(0).getText()).isEqualTo("In the end, writing arises when man realizes that");
		assertThat(cl100kChunks.get(1).getText()).isEqualTo("memory is not enough.");

		// Doc 2
		assertThat(cl100kChunks.get(2).getText())
			.isEqualTo("The most oppressive thing about the labyrinth is that you");
		assertThat(cl100kChunks.get(3).getText()).isEqualTo("are constantly being forced to choose.");
		assertThat(cl100kChunks.get(4).getText()).isEqualTo("It isn't the lack of an exit, but");
		assertThat(cl100kChunks.get(5).getText()).isEqualTo("the abundance of exits that is so disorienting");

		// P50K_BASE behaves the same as CL100K_BASE for this English input
		var p50kSplitter = TokenTextSplitter.builder()
			.withEncodingType(EncodingType.P50K_BASE)
			.withChunkSize(10)
			.withMinChunkSizeChars(5)
			.withMinChunkLengthToEmbed(3)
			.withMaxNumChunks(50)
			.withKeepSeparator(true)
			.build();

		var p50kChunks = p50kSplitter.apply(List.of(doc1, doc2));

		assertThat(p50kChunks.size()).isEqualTo(6);

		// Doc 1
		assertThat(p50kChunks.get(0).getText()).isEqualTo("In the end, writing arises when man realizes that");
		assertThat(p50kChunks.get(1).getText()).isEqualTo("memory is not enough.");

		// Doc 2
		assertThat(p50kChunks.get(2).getText()).isEqualTo("The most oppressive thing about the labyrinth is that you");
		assertThat(p50kChunks.get(3).getText()).isEqualTo("are constantly being forced to choose.");
		assertThat(p50kChunks.get(4).getText()).isEqualTo("It isn't the lack of an exit, but");
		assertThat(p50kChunks.get(5).getText()).isEqualTo("the abundance of exits that is so disorienting");

		var o200kSplitter = TokenTextSplitter.builder()
			.withEncodingType(EncodingType.O200K_BASE)
			.withChunkSize(10)
			.withMinChunkSizeChars(5)
			.withMinChunkLengthToEmbed(3)
			.withMaxNumChunks(50)
			.withKeepSeparator(true)
			.build();

		// O200K_BASE has slightly different token boundaries
		var o200kChunks = o200kSplitter.apply(List.of(doc1, doc2));

		assertThat(o200kChunks.size()).isEqualTo(6);

		// Doc 1
		assertThat(o200kChunks.get(0).getText()).isEqualTo("In the end, writing arises when man realizes that");
		assertThat(o200kChunks.get(1).getText()).isEqualTo("memory is not enough.");

		// Doc 2
		assertThat(o200kChunks.get(2).getText()).isEqualTo("The most oppressive thing about the labyrinth is that you");
		assertThat(o200kChunks.get(3).getText()).isEqualTo("are constantly being forced to choose.");
		assertThat(o200kChunks.get(4).getText()).isEqualTo("It isn't the lack of an exit, but the");
		assertThat(o200kChunks.get(5).getText()).isEqualTo("abundance of exits that is so disorienting.");
	}

}
