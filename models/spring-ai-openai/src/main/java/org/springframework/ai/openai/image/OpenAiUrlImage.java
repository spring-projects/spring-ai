/*
 * Copyright 2023 - 2024 the original author or authors.
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
package org.springframework.ai.openai.image;

import org.springframework.ai.image.AbstractImage;

/**
 * Represents an image sourced from a URL, specifically designed for use within the OpenAI
 * image processing context. This class extends {@link AbstractImage} to provide a
 * specialized representation for images that are accessible via web URLs.
 * <p>
 * Each instance of {@code OpenAiUrlImage} is associated with the
 * {@link OpenAiImageType#URL} image type, indicating the source of the image data is from
 * an external URL. This is particularly useful for scenarios where images need to be
 * referenced rather than stored directly within the application.
 * </p>
 *
 * @author youngmon
 * @version 0.8.1
 */
public class OpenAiUrlImage extends AbstractImage<String> {

	/**
	 * Constructs a new {@code OpenAiUrlImage} with the specified image URL.
	 * @param url The URL of the image. This should be a valid, fully qualified URL that
	 * points directly to an image file accessible over the internet.
	 */
	public OpenAiUrlImage(final String url) {
		super(url, OpenAiImageType.URL);
	}

}
