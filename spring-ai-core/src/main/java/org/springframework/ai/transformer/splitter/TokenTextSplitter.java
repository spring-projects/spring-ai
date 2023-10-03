package org.springframework.ai.transformer.splitter;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Raphael Yu
 */
public class TokenTextSplitter extends TextSplitter {

	private final int defaultChunkSize = 800; // The target size of each text
												// chunk in tokens

	private int minChunkSizeChars = 350; // The minimum size of each text
											// chunk in characters

	private int minChunkLengthToEmbed = 5; // Discard chunks shorter than this

	private int maxNumChunks = 10000; // The maximum number of chunks to generate from a
										// text

	private boolean keepSeparator = true;

	public TokenTextSplitter() {
	}

	public TokenTextSplitter(boolean keepSeparator) {
		this.keepSeparator = keepSeparator;
	}

	private final EncodingRegistry registry = Encodings.newLazyEncodingRegistry();

	private final Encoding encoding = registry.getEncoding(EncodingType.CL100K_BASE);

	@Override
	protected List<String> splitText(String text) {
		return split(text, defaultChunkSize);
	}

	public List<String> split(String text, int chunkSize) {
		if (text == null || text.trim().isEmpty()) {
			return new ArrayList<>();
		}

		List<Integer> tokens = getEncodedTokens(text);
		List<String> chunks = new ArrayList<>();
		int num_chunks = 0;
		while (!tokens.isEmpty() && num_chunks < maxNumChunks) {
			List<Integer> chunk = tokens.subList(0, Math.min(chunkSize, tokens.size()));
			String chunkText = decodeTokens(chunk);

			// Skip the chunk if it is empty or whitespace
			if (chunkText.trim().isEmpty()) {
				tokens = tokens.subList(chunk.size(), tokens.size());
				continue;
			}

			// Find the last period or punctuation mark in the chunk
			int lastPunctuation = Math.max(chunkText.lastIndexOf('.'), Math.max(chunkText.lastIndexOf('?'),
					Math.max(chunkText.lastIndexOf('!'), chunkText.lastIndexOf('\n'))));

			if (lastPunctuation != -1 && lastPunctuation > minChunkSizeChars) {
				// Truncate the chunk text at the punctuation mark
				chunkText = chunkText.substring(0, lastPunctuation + 1);
			}

			String chunk_text_to_append = (this.keepSeparator) ? chunkText.trim() : chunkText.replace("\n", " ").trim();
			if (chunk_text_to_append.length() > minChunkLengthToEmbed) {
				chunks.add(chunk_text_to_append);
			}

			// Remove the tokens corresponding to the chunk text from the remaining tokens
			tokens = tokens.subList(getEncodedTokens(chunkText).size(), tokens.size());

			num_chunks++;
		}

		// Handle the remaining tokens
		if (!tokens.isEmpty()) {
			String remaining_text = decodeTokens(tokens).replace("\n", " ").trim();
			if (remaining_text.length() > minChunkLengthToEmbed) {
				chunks.add(remaining_text);
			}
		}

		return chunks;
	}

	private List<Integer> getEncodedTokens(String text) {
		return encoding.encode(text);
	}

	private String decodeTokens(List<Integer> tokens) {
		return encoding.decode(tokens);
	}

}
