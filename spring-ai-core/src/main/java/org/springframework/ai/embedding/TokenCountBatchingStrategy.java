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
import java.util.List;

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

	private final TokenCountEstimator tokenCountEstimator;

	private final int maxInputTokenCount;

	private final ContentFormatter contentFormater;

	private final MetadataMode metadataMode;

	public TokenCountBatchingStrategy() {
		this(EncodingType.CL100K_BASE, MAX_INPUT_TOKEN_COUNT);
	}

	/**
	 * @param encodingType {@link EncodingType}
	 * @param maxInputTokenCount upper limit for input tokens
	 */
	public TokenCountBatchingStrategy(EncodingType encodingType, int maxInputTokenCount) {
		this(encodingType, maxInputTokenCount, Document.DEFAULT_CONTENT_FORMATTER, MetadataMode.NONE);
	}

	/**
	 * @param encodingType {@link EncodingType}
	 * @param maxInputTokenCount upper limit for input tokens
	 * @param contentFormatter {@link ContentFormatter}
	 * @param metadataMode {@link MetadataMode}
	 */
	public TokenCountBatchingStrategy(EncodingType encodingType, int maxInputTokenCount,
			ContentFormatter contentFormatter, MetadataMode metadataMode) {
		this.tokenCountEstimator = new JTokkitTokenCountEstimator(encodingType);
		this.maxInputTokenCount = (int) Math.round(maxInputTokenCount - (maxInputTokenCount * .1));
		this.contentFormater = contentFormatter;
		this.metadataMode = metadataMode;
	}

	@Override
	public List<List<Document>> batch(List<Document> documents) {
		List<List<Document>> batches = new ArrayList<>();
		int currentSize = 0;
		List<Document> currentBatch = new ArrayList<>();

		for (Document document : documents) {
			int tokenCount = this.tokenCountEstimator
				.estimate(document.getFormattedContent(this.contentFormater, this.metadataMode));
			if (tokenCount > this.maxInputTokenCount) {
				throw new IllegalArgumentException(
						"Tokens in a single document exceeds the maximum number of allowed input tokens");
			}
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
