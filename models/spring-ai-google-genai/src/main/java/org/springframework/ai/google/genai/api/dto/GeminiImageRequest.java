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
package org.springframework.ai.google.genai.api.dto;

import org.springframework.ai.google.genai.GoogleGenAiImageOptions;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * @author Danil Temnikov
 */
public class GeminiImageRequest {

	public final List<GeminiContent> contents;

	public final GeminiGenerationConfig generationConfig;

	public GeminiImageRequest(List<GeminiContent> contents, GeminiGenerationConfig generationConfig) {

		this.contents = contents != null ? contents : Collections.emptyList();
		this.generationConfig = generationConfig;
	}

	public List<GeminiContent> getContents() {
		return contents;
	}

	public GeminiGenerationConfig getGenerationConfig() {
		return generationConfig;
	}

	public static final class GeminiGenerationConfig {

		public final List<String> responseModalities;

		public final GeminiImageConfig imageConfig;

		public GeminiGenerationConfig(List<String> responseModalities, GeminiImageConfig imageConfig) {

			this.responseModalities = responseModalities != null ? responseModalities : Collections.emptyList();
			this.imageConfig = imageConfig;
		}

		public List<String> getResponseModalities() {
			return responseModalities;
		}

		public GeminiImageConfig getImageConfig() {
			return imageConfig;
		}

	}

	public static final class GeminiImageConfig {

		@Nullable
		public final String aspectRatio;

		@Nullable
		public final String imageSize;

		public GeminiImageConfig(GoogleGenAiImageOptions.ImageConfig imageConfig) {
			this.aspectRatio = imageConfig.getAspectRatio();
			this.imageSize = imageConfig.getImageSize();
		}

		@Nullable
		public String getAspectRatio() {
			return aspectRatio;
		}

		@Nullable
		public String getImageSize() {
			return imageSize;
		}

	}

}
