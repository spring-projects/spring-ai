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

package org.springframework.ai.embedding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.knuddels.jtokkit.api.EncodingType;

import org.springframework.ai.document.ContentFormatter;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.util.Assert;

/**
 * Token count based strategy implementation for {@link BatchingStrategy}. Using openai
 * max input token as the default:
 * https://platform.openai.com/docs/guides/embeddings/embedding-models.
 *
 * This strategy incorporates a reserve percentage to provide a buffer for potential
 * overhead or unexpected increases in token count during processing. The actual max input
 * token count used is calculated as: actualMaxInputTokenCount =
 * originalMaxInputTokenCount * (1 - RESERVE_PERCENTAGE)
 *
 * For example, with the default reserve percentage of 10% (0.1) and the default max input
 * token count of 8191, the actual max input token count used will be 7371.
 *
 * The strategy batches documents based on their token counts, ensuring that each batch
 * does not exceed the calculated max input token count.
 *
 * @author Soby Chacko
 * @author Mark Pollack
 * @author Laura Trotta
 * @since 1.0.0
 */
public class TokenCountBatchingStrategy implements BatchingStrategy {

	/**
	 * Using openai upper limit of input token count as the default.
	 */
	private static final int MAX_INPUT_TOKEN_COUNT = 8191;

	/**
	 * The default percentage of tokens to reserve when calculating the actual max input
	 * token count.
	 */
	private static final double DEFAULT_TOKEN_COUNT_RESERVE_PERCENTAGE = 0.1;

	private final TokenCountEstimator tokenCountEstimator;

	private final int maxInputTokenCount;

	private final ContentFormatter contentFormater;

	private final MetadataMode metadataMode;

	public TokenCountBatchingStrategy() {
		this(EncodingType.CL100K_BASE, MAX_INPUT_TOKEN_COUNT, DEFAULT_TOKEN_COUNT_RESERVE_PERCENTAGE);
	}

	/**
	 * @param encodingType {@link EncodingType}
	 * @param thresholdFactor the threshold factor to use on top of the max input token
	 * count
	 * @param maxInputTokenCount upper limit for input tokens
	 */
	public TokenCountBatchingStrategy(EncodingType encodingType, int maxInputTokenCount, double thresholdFactor) {
		this(encodingType, maxInputTokenCount, thresholdFactor, Document.DEFAULT_CONTENT_FORMATTER, MetadataMode.NONE);
	}

	/**
	 * @param encodingType The {@link EncodingType} to be used for token counting.
	 * @param maxInputTokenCount The initial upper limit for input tokens.
	 * @param reservePercentage The percentage of tokens to reserve from the max input
	 * token count. This creates a buffer for potential token count increases during
	 * processing.
	 * @param contentFormatter the {@link ContentFormatter} to be used for formatting
	 * content.
	 * @param metadataMode The {@link MetadataMode} to be used for handling metadata.
	 */
	public TokenCountBatchingStrategy(EncodingType encodingType, int maxInputTokenCount, double reservePercentage,
			ContentFormatter contentFormatter, MetadataMode metadataMode) {
		Assert.notNull(encodingType, "EncodingType must not be null");
		Assert.notNull(contentFormatter, "ContentFormatter must not be null");
		Assert.notNull(metadataMode, "MetadataMode must not be null");
		this.tokenCountEstimator = new JTokkitTokenCountEstimator(encodingType);
		this.maxInputTokenCount = (int) Math.round(maxInputTokenCount * (1 - reservePercentage));
		this.contentFormater = contentFormatter;
		this.metadataMode = metadataMode;
	}

	/**
	 * Constructs a TokenCountBatchingStrategy with the specified parameters.
	 * @param tokenCountEstimator the TokenCountEstimator to be used for estimating token
	 * counts.
	 * @param maxInputTokenCount the initial upper limit for input tokens.
	 * @param reservePercentage the percentage of tokens to reserve from the max input
	 * token count to create a buffer.
	 * @param contentFormatter the ContentFormatter to be used for formatting content.
	 * @param metadataMode the MetadataMode to be used for handling metadata.
	 */
	public TokenCountBatchingStrategy(TokenCountEstimator tokenCountEstimator, int maxInputTokenCount,
			double reservePercentage, ContentFormatter contentFormatter, MetadataMode metadataMode) {
		Assert.notNull(tokenCountEstimator, "TokenCountEstimator must not be null");
		this.tokenCountEstimator = tokenCountEstimator;
		this.maxInputTokenCount = (int) Math.round(maxInputTokenCount * (1 - reservePercentage));
		this.contentFormater = contentFormatter;
		this.metadataMode = metadataMode;
	}

	@Override
	public List<List<Document>> batch(List<Document> documents) {
		List<List<Document>> batches = new ArrayList<>();
		int currentSize = 0;
		List<Document> currentBatch = new ArrayList<>();
		Map<Document, Integer> documentTokens = new HashMap<>();

		for (Document document : documents) {
			int tokenCount = this.tokenCountEstimator
				.estimate(document.getFormattedContent(this.contentFormater, this.metadataMode));
			if (tokenCount > this.maxInputTokenCount) {
				throw new IllegalArgumentException(
						"Tokens in a single document exceeds the maximum number of allowed input tokens");
			}
			documentTokens.put(document, tokenCount);
		}

		for (Document document : documentTokens.keySet()) {
			Integer tokenCount = documentTokens.get(document);
			if (currentSize + tokenCount > this.maxInputTokenCount) {
				batches.add(currentBatch);
				currentBatch = new ArrayList<>();
				currentSize = 0;
			}
			currentBatch.add(document);
			currentSize += tokenCount;
		}
		if (!currentBatch.isEmpty()) {
			batches.add(currentBatch);
		}
		return batches;
	}

}
