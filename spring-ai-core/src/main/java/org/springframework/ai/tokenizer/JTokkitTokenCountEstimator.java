/*
 * Copyright 2024-2024 the original author or authors.
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

import org.springframework.ai.chat.messages.Media;
import org.springframework.ai.chat.messages.Message;
import org.springframework.util.CollectionUtils;

/**
 * @author Christian Tzolov
 */
public class JTokkitTokenCountEstimator implements TokenCountEstimator {

	private final Encoding estimator;

	public JTokkitTokenCountEstimator() {
		this.estimator = Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
	}

	public JTokkitTokenCountEstimator(Encoding tokenEncoding) {
		this.estimator = tokenEncoding;
	}

	@Override
	public int estimate(String text) {
		if (text == null) {
			return 0;
		}
		return this.estimator.countTokens(text);
	}

	@Override
	public int estimate(Message message) {
		int tokenCount = 0;

		if (message.getContent() != null) {
			tokenCount += this.estimate(message.getContent());
		}

		if (!CollectionUtils.isEmpty(message.getMedia())) {

			for (Media media : message.getMedia()) {

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
	public int estimate(Iterable<Message> messages) {
		int totalSize = 0;
		for (Message message : messages) {
			totalSize += this.estimate(message);
		}
		return totalSize;
	}

}
