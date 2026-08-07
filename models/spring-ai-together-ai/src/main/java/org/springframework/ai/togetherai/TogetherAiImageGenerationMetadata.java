/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.togetherai;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.image.ImageGenerationMetadata;
import org.springframework.ai.togetherai.api.TogetherAiApi;

/**
 * Together AI image generation metadata.
 *
 * @since 2.0.1
 * @author Maksym Uimanov
 */
public record TogetherAiImageGenerationMetadata(Integer index,
		@Nullable ImageType type) implements ImageGenerationMetadata {
	public enum ImageType {

		BASE64_JSON("b64_json"), URL("url");

		private final String value;

		@Nullable public static ImageType from(TogetherAiApi.GenerateImageResponse.@Nullable ImageType type) {
			if (type == null) {
				return null;
			}
			return switch (type) {
				case BASE64_JSON -> BASE64_JSON;
				case URL -> URL;
			};
		}

		ImageType(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

	}
}
