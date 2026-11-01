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

package org.springframework.ai.google.genai.image;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.image.ImageGenerationMetadata;

/**
 * Image generation metadata returned by the Google GenAI image API.
 *
 * @author Olivier Le Quellec
 * @since 2.0.1
 */
public class GoogleGenAiImageGenerationMetadata implements ImageGenerationMetadata {

	private final @Nullable String enhancedPrompt;

	private final @Nullable String raiFilteredReason;

	private final @Nullable String mimeType;

	private final @Nullable String gcsUri;

	public GoogleGenAiImageGenerationMetadata(@Nullable String enhancedPrompt, @Nullable String raiFilteredReason,
			@Nullable String mimeType, @Nullable String gcsUri) {
		this.enhancedPrompt = enhancedPrompt;
		this.raiFilteredReason = raiFilteredReason;
		this.mimeType = mimeType;
		this.gcsUri = gcsUri;
	}

	public @Nullable String getEnhancedPrompt() {
		return this.enhancedPrompt;
	}

	public @Nullable String getRaiFilteredReason() {
		return this.raiFilteredReason;
	}

	public @Nullable String getMimeType() {
		return this.mimeType;
	}

	public @Nullable String getGcsUri() {
		return this.gcsUri;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof GoogleGenAiImageGenerationMetadata that)) {
			return false;
		}
		return Objects.equals(this.enhancedPrompt, that.enhancedPrompt)
				&& Objects.equals(this.raiFilteredReason, that.raiFilteredReason)
				&& Objects.equals(this.mimeType, that.mimeType) && Objects.equals(this.gcsUri, that.gcsUri);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.enhancedPrompt, this.raiFilteredReason, this.mimeType, this.gcsUri);
	}

	@Override
	public String toString() {
		return "GoogleGenAiImageGenerationMetadata{" + "enhancedPrompt='" + this.enhancedPrompt + '\''
				+ ", raiFilteredReason='" + this.raiFilteredReason + '\'' + ", mimeType='" + this.mimeType + '\''
				+ ", gcsUri='" + this.gcsUri + '\'' + '}';
	}

}
