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

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;

import org.springframework.ai.model.Media;
import org.springframework.ai.model.MediaContent;
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

	private final Encoding estimator;

	public JTokkitTokenCountEstimator() {
		this(EncodingType.CL100K_BASE);
	}

	public JTokkitTokenCountEstimator(EncodingType tokenEncodingType) {
		this.estimator = Encodings.newLazyEncodingRegistry().getEncoding(tokenEncodingType);
	}

	@Override
	public int estimate(String text) {
		if (text == null) {
			return 0;
		}
		return this.estimator.countTokens(text);
	}

	@Override
	public int estimate(MediaContent content) {
		int tokenCount = 0;

		if (content.getContent() != null) {
			tokenCount += this.estimate(content.getContent());
		}

		if (!CollectionUtils.isEmpty(content.getMedia())) {

			for (Media media : content.getMedia()) {

				tokenCount += this.estimate(media.getMimeType().toString());

				if (media.getData() instanceof String textData) {
					tokenCount += this.estimate(textData);
				}
				else if (media.getData() instanceof byte[] binaryData) {
					tokenCount += binaryData.length; // This is likely incorrect.
				}
			}
		}

		return tokenCount;
	}

	@Override
	public int estimate(Iterable<MediaContent> contents) {
		int totalSize = 0;
		for (MediaContent mediaContent : contents) {
			totalSize += this.estimate(mediaContent);
		}
		return totalSize;
	}

}
