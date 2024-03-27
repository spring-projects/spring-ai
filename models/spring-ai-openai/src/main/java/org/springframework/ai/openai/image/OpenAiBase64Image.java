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
 * Represents an image encoded in b64_json format within the OpenAI image processing
 * context. This class extends {@link AbstractImage} to cater specifically to images that
 * are provided as b64_json encoded strings. This format is useful for directly embedding
 * image data within JSON or other text-based data structures without relying on external
 * references.
 * <p>
 * An instance of this class is associated with the {@link OpenAiImageType#BASE64} image
 * type, signifying that the image data is encoded in b64_json format.
 * </p>
 *
 * @author youngmon
 * @version 0.8.1
 */
public class OpenAiBase64Image extends AbstractImage<String> {

	/**
	 * Constructs a new {@code OpenAiBase64Image} with the specified b64_json data.
	 * @param b64Json The Base64 encoded string that encapsulates the image data. The
	 * string should be a valid b64_json representation of an image file.
	 */
	public OpenAiBase64Image(final String b64Json) {
		super(b64Json, OpenAiImageType.BASE64);
	}

}
