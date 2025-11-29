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
 */
public class TokenTextSplitter extends TextSplitter {

	private static final int DEFAULT_CHUNK_SIZE = 800;

	private static final int DEFAULT_CHUNK_OVERLAP = 50;

	private static final int MIN_CHUNK_SIZE_CHARS = 350;

	private static final int MIN_CHUNK_LENGTH_TO_EMBED = 5;

	private static final int MAX_NUM_CHUNKS = 10000;

	private static final boolean KEEP_SEPARATOR = true;

	private final EncodingRegistry registry = Encodings.newLazyEncodingRegistry();

	private final Encoding encoding = this.registry.getEncoding(EncodingType.CL100K_BASE);

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

	public TokenTextSplitter() {
		this(DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP, MIN_CHUNK_SIZE_CHARS, MIN_CHUNK_LENGTH_TO_EMBED, MAX_NUM_CHUNKS,
				KEEP_SEPARATOR);
	}

	public TokenTextSplitter(boolean keepSeparator) {
		this(DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP, MIN_CHUNK_SIZE_CHARS, MIN_CHUNK_LENGTH_TO_EMBED, MAX_NUM_CHUNKS,
				keepSeparator);
	}

	public TokenTextSplitter(int chunkSize, int chunkOverlap, int minChunkSizeChars, int minChunkLengthToEmbed,
			int maxNumChunks, boolean keepSeparator) {
		Assert.isTrue(chunkOverlap < chunkSize, "chunk overlap must be less than chunk size");
		this.chunkSize = chunkSize;
		this.chunkOverlap = chunkOverlap;
		this.minChunkSizeChars = minChunkSizeChars;
		this.minChunkLengthToEmbed = minChunkLengthToEmbed;
		this.maxNumChunks = maxNumChunks;
		this.keepSeparator = keepSeparator;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	protected List<String> splitText(String text) {
		return doSplit(text, this.chunkSize, this.chunkOverlap);
	}

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

			// Apply sentence boundary optimization
			String optimizedText = optimizeChunkBoundary(chunkText);
			int optimizedTokenCount = getEncodedTokens(optimizedText).size();

			// Use optimized chunk
			String finalChunkText = optimizedText;
			int finalChunkTokenCount = optimizedTokenCount;

			// Advance position with minimum advance guarantee
			// This prevents creating a series of mini chunks when boundary optimization
			// aggressively shrinks chunks
			int naturalAdvance = finalChunkTokenCount - chunkOverlap;
			int minAdvance = Math.max(1, (chunkSize - chunkOverlap) / 2);
			int advance = Math.max(naturalAdvance, minAdvance);
			position += advance;

			// Format according to keepSeparator setting
			String formattedChunk = this.keepSeparator ? finalChunkText.trim()
					: finalChunkText.replace(System.lineSeparator(), " ").trim();

			// Add chunk if it meets minimum length
			if (formattedChunk.length() > this.minChunkLengthToEmbed) {
				chunks.add(formattedChunk);
				num_chunks++;
			}
		}

		return chunks;
	}

	private String optimizeChunkBoundary(String chunkText) {
		if (chunkText.length() <= this.minChunkSizeChars) {
			return chunkText;
		}

		// Look for sentence endings: . ! ? \n
		int bestCutPoint = -1;

		// Check in reverse order to find the last sentence ending
		for (int i = chunkText.length() - 1; i >= this.minChunkSizeChars; i--) {
			char c = chunkText.charAt(i);
			if (c == '.' || c == '!' || c == '?' || c == '\n') {
				bestCutPoint = i + 1; // Include the punctuation
				break;
			}
		}

		// If we found a good cut point, use it
		if (bestCutPoint > 0) {
			return chunkText.substring(0, bestCutPoint);
		}

		// Otherwise return the original chunk
		return chunkText;
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

		private int chunkSize = DEFAULT_CHUNK_SIZE;

		private int chunkOverlap = DEFAULT_CHUNK_OVERLAP;

		private int minChunkSizeChars = MIN_CHUNK_SIZE_CHARS;

		private int minChunkLengthToEmbed = MIN_CHUNK_LENGTH_TO_EMBED;

		private int maxNumChunks = MAX_NUM_CHUNKS;

		private boolean keepSeparator = KEEP_SEPARATOR;

		private Builder() {
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

		public TokenTextSplitter build() {
			return new TokenTextSplitter(this.chunkSize, this.chunkOverlap, this.minChunkSizeChars,
					this.minChunkLengthToEmbed, this.maxNumChunks, this.keepSeparator);
		}

	}

}
