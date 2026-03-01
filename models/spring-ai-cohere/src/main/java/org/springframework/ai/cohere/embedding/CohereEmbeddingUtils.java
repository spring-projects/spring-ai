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

package org.springframework.ai.cohere.embedding;

import java.util.Base64;
import java.util.List;

import org.springframework.ai.content.Media;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

/**
 * Utility class for Cohere embedding operations.
 *
 * @author Ricken Bazolo
 */
public final class CohereEmbeddingUtils {

	private static final List<MimeType> SUPPORTED_IMAGE_TYPES = List.of(MimeTypeUtils.IMAGE_JPEG,
			MimeTypeUtils.IMAGE_PNG, MimeTypeUtils.parseMimeType("image/webp"), MimeTypeUtils.IMAGE_GIF);

	private static final long MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024;

	private CohereEmbeddingUtils() {
	}

	public static String mediaToDataUri(Media media) {
		Assert.notNull(media, "Media cannot be null");
		validateImageMedia(media);

		byte[] imageData = getImageBytes(media);
		validateImageSize(imageData);

		String base64Data = Base64.getEncoder().encodeToString(imageData);
		String mimeType = media.getMimeType().toString();

		return String.format("data:%s;base64,%s", mimeType, base64Data);
	}

	private static void validateImageMedia(Media media) {
		MimeType mimeType = media.getMimeType();
		boolean isSupported = SUPPORTED_IMAGE_TYPES.stream()
			.anyMatch(supported -> mimeType.isCompatibleWith(supported));

		if (!isSupported) {
			throw new IllegalArgumentException("Unsupported image MIME type: " + mimeType
					+ ". Supported types: image/jpeg, image/png, image/webp, image/gif");
		}
	}

	private static byte[] getImageBytes(Media media) {
		Object data = media.getData();

		if (data instanceof byte[] bytes) {
			return bytes;
		}
		else if (data instanceof String base64String) {
			return Base64.getDecoder().decode(base64String);
		}
		else {
			throw new IllegalArgumentException("Media data must be byte[] or base64 String");
		}
	}

	private static void validateImageSize(byte[] imageData) {
		if (imageData.length > MAX_IMAGE_SIZE_BYTES) {
			throw new IllegalArgumentException(
					String.format("Image size (%d bytes) exceeds maximum allowed size (%d bytes)", imageData.length,
							MAX_IMAGE_SIZE_BYTES));
		}
	}

}
