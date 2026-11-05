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

package org.springframework.ai.openai.metadata;

import java.util.Objects;

import com.openai.models.images.ImagesResponse;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.image.ImageResponseMetadata;
import org.springframework.util.Assert;

/**
 * Represents the metadata for image response using the OpenAI Java SDK.
 *
 * @author Julien Dubois
 */
public class OpenAiImageResponseMetadata extends ImageResponseMetadata {

	private final Long created;

	private final @Nullable OpenAiImageUsage usage;

	/**
	 * Creates a new OpenAiImageResponseMetadata.
	 * @param created the creation timestamp
	 */
	protected OpenAiImageResponseMetadata(Long created) {
		this(created, null);
	}

	/**
	 * Creates a new OpenAiImageResponseMetadata.
	 * @param created the creation timestamp
	 * @param usage the image token usage, or {@code null} if not available
	 * @since 2.0.1
	 */
	protected OpenAiImageResponseMetadata(Long created, @Nullable OpenAiImageUsage usage) {
		this.created = created;
		this.usage = usage;
	}

	/**
	 * Creates metadata from an ImagesResponse.
	 * @param imagesResponse the OpenAI images response
	 * @return the metadata instance
	 */
	public static OpenAiImageResponseMetadata from(ImagesResponse imagesResponse) {
		Assert.notNull(imagesResponse, "imagesResponse must not be null");
		@Nullable OpenAiImageUsage usage = imagesResponse.usage().map(OpenAiImageResponseMetadata::from).orElse(null);
		return new OpenAiImageResponseMetadata(imagesResponse.created(), usage);
	}

	private static OpenAiImageUsage from(ImagesResponse.Usage usage) {
		ImagesResponse.Usage.InputTokensDetails inputDetails = usage.inputTokensDetails();
		@Nullable Long outputTextTokens = usage.outputTokensDetails()
			.map(ImagesResponse.Usage.OutputTokensDetails::textTokens)
			.orElse(null);
		@Nullable Long outputImageTokens = usage.outputTokensDetails()
			.map(ImagesResponse.Usage.OutputTokensDetails::imageTokens)
			.orElse(null);
		return new OpenAiImageUsage(usage.inputTokens(), usage.outputTokens(), usage.totalTokens(),
				inputDetails.textTokens(), inputDetails.imageTokens(), outputTextTokens, outputImageTokens);
	}

	@Override
	public Long getCreated() {
		return this.created;
	}

	/**
	 * Returns the token usage reported by the OpenAI Images API.
	 * @return the image token usage, or {@code null} if not available
	 * @since 2.0.1
	 */
	public @Nullable OpenAiImageUsage getUsage() {
		return this.usage;
	}

	@Override
	public String toString() {
		if (this.usage == null) {
			return "OpenAiImageResponseMetadata{" + "created=" + this.created + '}';
		}
		return "OpenAiImageResponseMetadata{" + "created=" + this.created + ", usage=" + this.usage + '}';
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof OpenAiImageResponseMetadata that)) {
			return false;
		}
		return Objects.equals(this.created, that.created) && Objects.equals(this.usage, that.usage);
	}

	@Override
	public int hashCode() {
		return this.usage == null ? Objects.hash(this.created) : Objects.hash(this.created, this.usage);
	}

}
