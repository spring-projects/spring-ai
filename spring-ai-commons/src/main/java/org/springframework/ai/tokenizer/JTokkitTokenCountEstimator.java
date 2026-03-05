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

package org.springframework.ai.tokenizer;

import java.util.Base64;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.content.Media;
import org.springframework.ai.content.MediaContent;
import org.springframework.util.CollectionUtils;

/**
 * Estimates the number of tokens in a given text or message using the JTokkit encoding
 * library.
 *
 * @author Christian Tzolov
 * @author Soby Chacko
 * @since 1.0.0
 */
public class JTokkitTokenCountEstimator implements TokenCountEstimator {

	/**
	 * The JTokkit encoding instance used for token counting.
	 */
	private final Encoding estimator;

	/**
	 * Creates a new JTokkitTokenCountEstimator with default CL100K_BASE encoding.
	 */
	public JTokkitTokenCountEstimator() {
		this(EncodingType.CL100K_BASE);
	}

	/**
	 * Creates a new JTokkitTokenCountEstimator with the specified encoding type.
	 * @param tokenEncodingType the encoding type to use for token counting
	 */
	public JTokkitTokenCountEstimator(final EncodingType tokenEncodingType) {
		this.estimator = Encodings.newLazyEncodingRegistry().getEncoding(tokenEncodingType);
	}

	@Override
	public int estimate(final @Nullable String text) {
		if (text == null) {
			return 0;
		}
		return this.estimator.countTokens(text);
	}

	@Override
	public int estimate(final MediaContent content) {
		int tokenCount = 0;

		if (content.getText() != null) {
			tokenCount += this.estimate(content.getText());
		}

		if (!CollectionUtils.isEmpty(content.getMedia())) {
			for (Media media : content.getMedia()) {
				tokenCount += this.estimate(media.getMimeType().toString());

				if (media.getData() instanceof String textData) {
					tokenCount += this.estimate(textData);
				}
				else if (media.getData() instanceof byte[] binaryData) {
					String base64 = Base64.getEncoder().encodeToString(binaryData);
					tokenCount += this.estimate(base64);
				}
			}
		}

		return tokenCount;
	}

	@Override
	public int estimate(final Iterable<MediaContent> contents) {
		int totalSize = 0;
		for (MediaContent mediaContent : contents) {
			totalSize += this.estimate(mediaContent);
		}
		return totalSize;
	}

}
