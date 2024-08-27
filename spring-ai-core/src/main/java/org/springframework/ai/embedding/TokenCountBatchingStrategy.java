/*
 * Copyright 2024 the original author or authors.
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
package org.springframework.ai.embedding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.ContentFormatter;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;

import com.knuddels.jtokkit.api.EncodingType;

/**
 * Token count based strategy implementation for {@link BatchingStrategy}. Using openai
 * max input token as the default:
 * https://platform.openai.com/docs/guides/embeddings/embedding-models.
 *
 * @author Soby Chacko
 * @since 1.0.0
 */
public class TokenCountBatchingStrategy implements BatchingStrategy {

	/**
	 * Using openai upper limit of input token count as the default.
	 */
	private static final int MAX_INPUT_TOKEN_COUNT = 8191;

	/**
	 * The actual max input token count used will be the original max input minus the
	 * threshold value multiplied by the original input.
	 */
	private static final double DEFAULT_TOKEN_COUNT_THRESHOLD_FACTOR = 0.1;

	private final TokenCountEstimator tokenCountEstimator;

	private final int maxInputTokenCount;

	private final ContentFormatter contentFormater;

	private final MetadataMode metadataMode;

	public TokenCountBatchingStrategy() {
		this(EncodingType.CL100K_BASE, MAX_INPUT_TOKEN_COUNT, DEFAULT_TOKEN_COUNT_THRESHOLD_FACTOR);
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
	 * @param encodingType {@link EncodingType}
	 * @param maxInputTokenCount upper limit for input tokens
	 * @param thresholdFactor the threshold factor to use on top of the max input token
	 * count
	 * @param contentFormatter {@link ContentFormatter}
	 * @param metadataMode {@link MetadataMode}
	 */
	public TokenCountBatchingStrategy(EncodingType encodingType, int maxInputTokenCount, double thresholdFactor,
			ContentFormatter contentFormatter, MetadataMode metadataMode) {
		this.tokenCountEstimator = new JTokkitTokenCountEstimator(encodingType);
		this.maxInputTokenCount = (int) Math.round(maxInputTokenCount - (maxInputTokenCount * thresholdFactor));
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
			if (currentSize + tokenCount > maxInputTokenCount) {
				batches.add(currentBatch);
				currentBatch.clear();
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
