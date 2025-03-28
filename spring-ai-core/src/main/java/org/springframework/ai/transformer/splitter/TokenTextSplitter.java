/*
 * Copyright 2023-2025 the original author or authors.
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
import java.util.function.Function;

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
 * @author Nan Chiu
 */
public class TokenTextSplitter extends TextSplitter {

	public static final Function<String, Integer> DEFAULT_FIND_LAST_PUNCTUATION = chunkText ->
			Math.max(chunkText.lastIndexOf('.'),
					Math.max(chunkText.lastIndexOf('?'),
							Math.max(chunkText.lastIndexOf('!'), chunkText.lastIndexOf('\n'))));

	private static final int DEFAULT_CHUNK_SIZE = 800;

	private static final int MIN_CHUNK_SIZE_CHARS = 350;

	private static final int MIN_CHUNK_LENGTH_TO_EMBED = 5;

	private static final int MAX_NUM_CHUNKS = 10000;

	private static final boolean KEEP_SEPARATOR = true;

	private final EncodingRegistry registry = Encodings.newLazyEncodingRegistry();

	private final Encoding encoding = this.registry.getEncoding(EncodingType.CL100K_BASE);

	// The target size of each text chunk in tokens
	private final int chunkSize;

	// The minimum size of each text chunk in characters
	private final int minChunkSizeChars;

	// Discard chunks shorter than this
	private final int minChunkLengthToEmbed;

	// The maximum number of chunks to generate from a text
	private final int maxNumChunks;

	private final boolean keepSeparator;

	// Finds the last period or punctuation mark in the given chunk
	private final Function<String, Integer> findLastPunctuation;

	public TokenTextSplitter() {
		this(DEFAULT_CHUNK_SIZE, MIN_CHUNK_SIZE_CHARS, MIN_CHUNK_LENGTH_TO_EMBED, MAX_NUM_CHUNKS,
				KEEP_SEPARATOR, DEFAULT_FIND_LAST_PUNCTUATION);
	}

	public TokenTextSplitter(boolean keepSeparator) {
		this(DEFAULT_CHUNK_SIZE, MIN_CHUNK_SIZE_CHARS, MIN_CHUNK_LENGTH_TO_EMBED, MAX_NUM_CHUNKS,
				keepSeparator, DEFAULT_FIND_LAST_PUNCTUATION);
	}

	public TokenTextSplitter(int chunkSize, int minChunkSizeChars, int minChunkLengthToEmbed, int maxNumChunks,
			boolean keepSeparator, Function<String, Integer> findLastPunctuation) {
		Assert.isTrue(chunkSize > 0, "ChunkSize must be greater than 0");
		Assert.isTrue(minChunkSizeChars >= 0, "MinChunkSizeChars must be a positive value");
		Assert.isTrue(maxNumChunks > 0, "MaxNumChunks must be greater than 0");
		Assert.notNull(findLastPunctuation, "FindLastPunctuation must not be null");
		this.chunkSize = chunkSize;
		this.minChunkSizeChars = minChunkSizeChars;
		this.minChunkLengthToEmbed = minChunkLengthToEmbed;
		this.maxNumChunks = maxNumChunks;
		this.keepSeparator = keepSeparator;
		this.findLastPunctuation = findLastPunctuation;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	protected List<String> splitText(String text) {
		return doSplit(text, this.chunkSize);
	}

	protected List<String> doSplit(String text, int chunkSize) {
		if (text == null || text.trim().isEmpty()) {
			return new ArrayList<>();
		}

		List<Integer> tokens = getEncodedTokens(text);
		List<String> chunks = new ArrayList<>();
		while (!tokens.isEmpty() && chunks.size() < this.maxNumChunks) {
			List<Integer> chunk = tokens.subList(0, Math.min(chunkSize, tokens.size()));
			String chunkText = decodeTokens(chunk);

			// Skip the chunk if it is empty or whitespace
			if (chunkText.trim().isEmpty()) {
				tokens = tokens.subList(chunk.size(), tokens.size());
				continue;
			}

			// Find the last period or punctuation mark in the chunk
			int lastPunctuation = findLastPunctuation.apply(chunkText);

			if (lastPunctuation > this.minChunkSizeChars && lastPunctuation < chunkText.length() - 1) {
				// Truncate the chunk text at the punctuation mark
				chunkText = chunkText.substring(0, lastPunctuation + 1);
			}

			String chunkTextToAppend = (this.keepSeparator) ? chunkText.trim()
					: chunkText.replace(System.lineSeparator(), " ").trim();
			if (chunkTextToAppend.length() > this.minChunkLengthToEmbed) {
				// Reserve capacity for remaining tokens
				if (chunks.size() == this.maxNumChunks - 1) {
					break;
				}
				chunks.add(chunkTextToAppend);
			}

			// Remove the tokens corresponding to the chunk text from the remaining tokens
			tokens = tokens.subList(getEncodedTokens(chunkText).size(), tokens.size());
		}

		// Handle the remaining tokens
		if (!tokens.isEmpty()) {
			String remaining_text = decodeTokens(tokens).replace(System.lineSeparator(), " ").trim();
			if (remaining_text.length() > this.minChunkLengthToEmbed) {
				chunks.add(remaining_text);
			}
		}

		return chunks;
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

		private int minChunkSizeChars = MIN_CHUNK_SIZE_CHARS;

		private int minChunkLengthToEmbed = MIN_CHUNK_LENGTH_TO_EMBED;

		private int maxNumChunks = MAX_NUM_CHUNKS;

		private boolean keepSeparator = KEEP_SEPARATOR;

		private Function<String, Integer> findLastPunctuation = DEFAULT_FIND_LAST_PUNCTUATION;

		private Builder() {
		}

		public Builder withChunkSize(int chunkSize) {
			this.chunkSize = chunkSize;
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

		public Builder withFindLastPunctuation(Function<String, Integer> findLastPunctuation) {
			this.findLastPunctuation = findLastPunctuation;
			return this;
		}

		public TokenTextSplitter build() {
			return new TokenTextSplitter(this.chunkSize, this.minChunkSizeChars, this.minChunkLengthToEmbed,
					this.maxNumChunks, this.keepSeparator, this.findLastPunctuation);
		}

	}

}
