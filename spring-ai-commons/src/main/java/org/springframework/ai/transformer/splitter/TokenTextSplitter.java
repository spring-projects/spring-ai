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

import java.util.ArrayList;
import java.util.List;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;

import org.springframework.util.Assert;

/**
 * A {@link TextSplitter} that splits text into chunks of a target size in tokens.
 *
 * @author Raphael Yu
 * @author Christian Tzolov
 * @author Ricken Bazolo
 * @author Seunghwan Jung
 * @author Jemin Huh
 */
public class TokenTextSplitter extends TextSplitter {

	private static final int DEFAULT_CHUNK_SIZE = 800;

	private static final int DEFAULT_CHUNK_OVERLAP = 0;

	private static final int MIN_CHUNK_SIZE_CHARS = 350;

	private static final int MIN_CHUNK_LENGTH_TO_EMBED = 5;

	private static final int MAX_NUM_CHUNKS = 10000;

	private static final boolean KEEP_SEPARATOR = true;

	private static final List<Character> DEFAULT_PUNCTUATION_MARKS = List.of('.', '?', '!', '\n');

	private static final EncodingType DEFAULT_ENCODING_TYPE = EncodingType.CL100K_BASE;

	private final EncodingRegistry registry = Encodings.newLazyEncodingRegistry();

	private final Encoding encoding;

	// The target size of each text chunk in tokens
	private final int chunkSize;

	// The overlap size of each text chunk in tokens
	private final int chunkOverlap;

	// The minimum size of each text chunk in characters
	private final int minChunkSizeChars;

	// Discard chunks shorter than this
	private final int minChunkLengthToEmbed;

	// The maximum number of chunks to generate from a text
	private final int maxNumChunks;

	private final boolean keepSeparator;

	private final List<Character> punctuationMarks;

	/**
	 * @deprecated since 2.0.0-M3, use {@link #builder()} instead.
	 */
	@Deprecated(since = "2.0.0-M3", forRemoval = true)
	@SuppressWarnings("deprecation")
	public TokenTextSplitter() {
		this(DEFAULT_ENCODING_TYPE, DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP, MIN_CHUNK_SIZE_CHARS,
				MIN_CHUNK_LENGTH_TO_EMBED, MAX_NUM_CHUNKS, KEEP_SEPARATOR, DEFAULT_PUNCTUATION_MARKS);
	}

	/**
	 * @deprecated since 2.0.0-M3, use {@link #builder()} instead.
	 */
	@Deprecated(since = "2.0.0-M3", forRemoval = true)
	public TokenTextSplitter(boolean keepSeparator) {
		this(DEFAULT_ENCODING_TYPE, DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP, MIN_CHUNK_SIZE_CHARS,
				MIN_CHUNK_LENGTH_TO_EMBED, MAX_NUM_CHUNKS, keepSeparator, DEFAULT_PUNCTUATION_MARKS);
	}

	/**
	 * @deprecated since 2.0.0-M3, use {@link #builder()} instead.
	 */
	@Deprecated(since = "2.0.0-M3", forRemoval = true)
	public TokenTextSplitter(EncodingType encodingType) {
		this(encodingType, DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP, MIN_CHUNK_SIZE_CHARS, MIN_CHUNK_LENGTH_TO_EMBED,
				MAX_NUM_CHUNKS, KEEP_SEPARATOR, DEFAULT_PUNCTUATION_MARKS);
	}

	/**
	 * @deprecated since 2.0.0-M3, use {@link #builder()} instead.
	 */
	@Deprecated(since = "2.0.0-M3", forRemoval = true)
	public TokenTextSplitter(EncodingType encodingType, boolean keepSeparator) {
		this(encodingType, DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP, MIN_CHUNK_SIZE_CHARS, MIN_CHUNK_LENGTH_TO_EMBED,
				MAX_NUM_CHUNKS, keepSeparator, DEFAULT_PUNCTUATION_MARKS);
	}

	/**
	 * @deprecated since 2.0.0-M3, use {@link #builder()} instead.
	 */
	@Deprecated(since = "2.0.0-M3", forRemoval = true)
	public TokenTextSplitter(int chunkSize, int minChunkSizeChars, int minChunkLengthToEmbed, int maxNumChunks,
			boolean keepSeparator, List<Character> punctuationMarks) {
		this(DEFAULT_ENCODING_TYPE, chunkSize, DEFAULT_CHUNK_OVERLAP, minChunkSizeChars, minChunkLengthToEmbed,
				maxNumChunks, keepSeparator, punctuationMarks);
	}

	private TokenTextSplitter(EncodingType encodingType, int chunkSize, int chunkOverlap, int minChunkSizeChars,
			int minChunkLengthToEmbed, int maxNumChunks, boolean keepSeparator, List<Character> punctuationMarks) {
		Assert.notNull(encodingType, "encodingType must not be null");
		Assert.notEmpty(punctuationMarks, "punctuationMarks must not be empty");
		Assert.isTrue(chunkSize > 0, "chunkSize must be greater than zero");
		Assert.isTrue(chunkOverlap >= 0, "chunk overlap must not be negative");
		Assert.isTrue(chunkOverlap < chunkSize, "chunk overlap must be less than chunk size");
		Assert.isTrue(maxNumChunks > 0, "maxNumChunks must be greater than zero");
		Assert.isTrue(minChunkSizeChars >= 0, "minChunkSizeChars must not be negative");
		Assert.isTrue(minChunkLengthToEmbed >= 0, "minChunkLengthToEmbed must not be negative");
		this.encoding = this.registry.getEncoding(encodingType);
		this.chunkSize = chunkSize;
		this.chunkOverlap = chunkOverlap;
		this.minChunkSizeChars = minChunkSizeChars;
		this.minChunkLengthToEmbed = minChunkLengthToEmbed;
		this.maxNumChunks = maxNumChunks;
		this.keepSeparator = keepSeparator;
		this.punctuationMarks = punctuationMarks;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	protected List<String> splitText(String text) {
		return doSplit(text, this.chunkSize);
	}

	/**
	 * Splits text into chunks based on token count, using the overlap configured on this
	 * splitter.
	 * <p>
	 * Subclasses that override this method replace the whole algorithm, so overlap does
	 * not apply to them unless they call {@link #doSplit(String, int, int)}.
	 * @param text the text to split
	 * @param chunkSize the target chunk size in tokens
	 * @return list of text chunks
	 */
	protected List<String> doSplit(String text, int chunkSize) {
		return doSplit(text, chunkSize, this.chunkOverlap);
	}

	/**
	 * Splits text into chunks based on token count.
	 * <p>
	 * Punctuation-based splitting only applies when the token count exceeds the chunk
	 * size ({@code tokens.size() > chunkSize}). Text that exactly matches or is smaller
	 * than the chunk size is returned as a single chunk without punctuation-based
	 * truncation.
	 * @param text the text to split
	 * @param chunkSize the target chunk size in tokens
	 * @param chunkOverlap the number of tokens each chunk shares with the one before it
	 * @return list of text chunks
	 * @since 2.1.0
	 */
	protected List<String> doSplit(String text, int chunkSize, int chunkOverlap) {
		if (text == null || text.trim().isEmpty()) {
			return new ArrayList<>();
		}

		List<Integer> tokens = getEncodedTokens(text);

		// If text is smaller than chunk size, return as a single chunk
		if (tokens.size() <= chunkSize) {
			String processedText = this.keepSeparator ? text.trim() : text.replace(System.lineSeparator(), " ").trim();

			if (processedText.length() > this.minChunkLengthToEmbed) {
				return List.of(processedText);
			}
			return new ArrayList<>();
		}
		List<String> chunks = new ArrayList<>();
		int position = 0;
		int num_chunks = 0;

		while (position < tokens.size() && num_chunks < this.maxNumChunks) {
			int chunkEnd = Math.min(position + chunkSize, tokens.size());

			// Extract tokens for this chunk
			List<Integer> chunkTokens = tokens.subList(position, chunkEnd);
			String chunkText = decodeTokens(chunkTokens);

			// Skip the chunk if it is empty or whitespace
			if (chunkText.trim().isEmpty()) {
				position = chunkEnd;
				continue;
			}

			// Apply sentence boundary optimization, but only while more text remains than
			// fits in this chunk, so the last chunk is never cut at punctuation
			String optimizedText = (chunkEnd < tokens.size()) ? optimizeChunkBoundary(chunkText) : chunkText;
			int optimizedTokenCount = getEncodedTokens(optimizedText).size();

			// Use optimized chunk
			String finalChunkText = optimizedText;
			int finalChunkTokenCount = optimizedTokenCount;

			// Advance past this chunk, stepping back chunkOverlap tokens so the next
			// chunk repeats them, but always move forward by at least one token
			int advance = Math.max(1, finalChunkTokenCount - chunkOverlap);

			// Once a chunk reaches the end of the text, or only whitespace follows it,
			// stop instead of stepping back, so no trailing chunk repeats text that was
			// already emitted. Only a short remainder is checked for whitespace.
			int chunkEndPosition = position + finalChunkTokenCount;
			int remainingTokens = tokens.size() - chunkEndPosition;
			boolean reachedEnd = remainingTokens <= 0 || (remainingTokens <= chunkSize
					&& decodeTokens(tokens.subList(chunkEndPosition, tokens.size())).isBlank());
			position = reachedEnd ? tokens.size() : position + advance;

			// Format according to keepSeparator setting
			String formattedChunk = this.keepSeparator ? finalChunkText.trim()
					: finalChunkText.replace(System.lineSeparator(), " ").trim();

			// Add chunk if it meets minimum length
			if (formattedChunk.length() > this.minChunkLengthToEmbed) {
				chunks.add(formattedChunk);
			}

			// Count every chunk toward maxNumChunks, including ones dropped as too short
			num_chunks++;
		}

		// Handle the remaining tokens
		if (position < tokens.size()) {
			String remaining_text = decodeTokens(tokens.subList(position, tokens.size()))
				.replace(System.lineSeparator(), " ")
				.trim();
			if (remaining_text.length() > this.minChunkLengthToEmbed) {
				chunks.add(remaining_text);
			}
		}

		return chunks;
	}

	private String optimizeChunkBoundary(String chunkText) {
		if (chunkText.length() <= this.minChunkSizeChars) {
			return chunkText;
		}

		// Find the last configured punctuation mark in the chunk
		int lastPunctuation = getLastPunctuationIndex(chunkText);

		// Cut after it, keeping the punctuation, unless that would leave the chunk too
		// short
		if (lastPunctuation != -1 && lastPunctuation > this.minChunkSizeChars) {
			return chunkText.substring(0, lastPunctuation + 1);
		}

		// Otherwise return the original chunk
		return chunkText;
	}

	protected int getLastPunctuationIndex(String chunkText) {
		// find the max index of any punctuation mark
		int maxLastPunctuation = -1;
		for (Character punctuationMark : this.punctuationMarks) {
			int lastPunctuation = chunkText.lastIndexOf(punctuationMark);
			maxLastPunctuation = Math.max(maxLastPunctuation, lastPunctuation);
		}
		return maxLastPunctuation;
	}

	private List<Integer> getEncodedTokens(String text) {
		Assert.notNull(text, "Text must not be null");
		return this.encoding.encode(text).boxed();
	}

	private String decodeTokens(List<Integer> tokens) {
		Assert.notNull(tokens, "Tokens must not be null");
		var tokensIntArray = new IntArrayList(tokens.size());
		tokens.forEach(tokensIntArray::add);
		return this.encoding.decode(tokensIntArray);
	}

	public static final class Builder {

		private EncodingType encodingType = DEFAULT_ENCODING_TYPE;

		private int chunkSize = DEFAULT_CHUNK_SIZE;

		private int chunkOverlap = DEFAULT_CHUNK_OVERLAP;

		private int minChunkSizeChars = MIN_CHUNK_SIZE_CHARS;

		private int minChunkLengthToEmbed = MIN_CHUNK_LENGTH_TO_EMBED;

		private int maxNumChunks = MAX_NUM_CHUNKS;

		private boolean keepSeparator = KEEP_SEPARATOR;

		private List<Character> punctuationMarks = DEFAULT_PUNCTUATION_MARKS;

		private Builder() {
		}

		public Builder withEncodingType(EncodingType encodingType) {
			this.encodingType = encodingType;
			return this;
		}

		public Builder withChunkSize(int chunkSize) {
			this.chunkSize = chunkSize;
			return this;
		}

		public Builder withChunkOverlap(int chunkOverlap) {
			this.chunkOverlap = chunkOverlap;
			return this;
		}

		public Builder withMinChunkSizeChars(int minChunkSizeChars) {
			this.minChunkSizeChars = minChunkSizeChars;
			return this;
		}

		public Builder withMinChunkLengthToEmbed(int minChunkLengthToEmbed) {
			this.minChunkLengthToEmbed = minChunkLengthToEmbed;
			return this;
		}

		public Builder withMaxNumChunks(int maxNumChunks) {
			this.maxNumChunks = maxNumChunks;
			return this;
		}

		public Builder withKeepSeparator(boolean keepSeparator) {
			this.keepSeparator = keepSeparator;
			return this;
		}

		public Builder withPunctuationMarks(List<Character> punctuationMarks) {
			this.punctuationMarks = punctuationMarks;
			return this;
		}

		public TokenTextSplitter build() {
			return new TokenTextSplitter(this.encodingType, this.chunkSize, this.chunkOverlap, this.minChunkSizeChars,
					this.minChunkLengthToEmbed, this.maxNumChunks, this.keepSeparator, this.punctuationMarks);
		}

	}

}
