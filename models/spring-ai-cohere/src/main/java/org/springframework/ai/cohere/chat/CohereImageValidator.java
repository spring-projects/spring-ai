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

package org.springframework.ai.cohere.chat;

import java.util.List;
import java.util.Set;

import org.springframework.ai.content.Media;

/**
 * Validator for Cohere API image constraints.
 *
 * @author Ricken Bazolo
 */
public final class CohereImageValidator {

	private static final int MAX_IMAGES_PER_REQUEST = 20;

	private static final long MAX_TOTAL_IMAGE_SIZE_BYTES = 20 * 1024 * 1024;

	private static final Set<String> SUPPORTED_IMAGE_FORMATS = Set.of("image/jpeg", "image/png", "image/webp",
			"image/gif");

	private CohereImageValidator() {
	}

	public static void validateImages(List<Media> mediaList) {
		if (mediaList == null || mediaList.isEmpty()) {
			return;
		}

		validateImageCount(mediaList);
		validateImageFormats(mediaList);
		validateTotalImageSize(mediaList);
	}

	private static void validateImageCount(List<Media> mediaList) {
		if (mediaList.size() > MAX_IMAGES_PER_REQUEST) {
			throw new IllegalArgumentException(
					String.format("Cohere API supports maximum %d images per request, found: %d",
							MAX_IMAGES_PER_REQUEST, mediaList.size()));
		}
	}

	private static void validateImageFormats(List<Media> mediaList) {
		for (Media media : mediaList) {
			var mimeType = media.getMimeType().toString();
			if (!SUPPORTED_IMAGE_FORMATS.contains(mimeType)) {
				throw new IllegalArgumentException(String
					.format("Unsupported image format: %s. Supported formats: JPEG, PNG, WebP, GIF", mimeType));
			}
		}
	}

	private static void validateTotalImageSize(List<Media> mediaList) {
		long totalSize = 0;

		for (Media media : mediaList) {
			long mediaSize = calculateMediaSize(media);
			totalSize += mediaSize;
		}

		if (totalSize > MAX_TOTAL_IMAGE_SIZE_BYTES) {
			long totalSizeMB = totalSize / (1024 * 1024);
			throw new IllegalArgumentException(String.format("Total image size exceeds 20MB limit: %dMB", totalSizeMB));
		}
	}

	private static long calculateMediaSize(Media media) {
		var data = media.getData();

		if (data instanceof byte[] bytes) {
			return bytes.length;
		}

		if (data instanceof String text) {
			if (text.startsWith("data:")) {
				var base64Data = text.substring(text.indexOf(",") + 1);
				return (long) (base64Data.length() * 0.75);
			}
			return 0;
		}

		return 0;
	}

}
