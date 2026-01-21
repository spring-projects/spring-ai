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

package org.springframework.ai.image;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.model.ModelResponse;
import org.springframework.util.CollectionUtils;

/**
 * The image completion (e.g. imageGeneration) response returned by an AI provider.
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @author Hyunjoon Choi
 */
public class ImageResponse implements ModelResponse<ImageGeneration> {

	private final ImageResponseMetadata imageResponseMetadata;

	/**
	 * List of generate images returned by the AI provider.
	 */
	private final List<ImageGeneration> imageGenerations;

	/**
	 * Construct a new {@link ImageResponse} instance without metadata.
	 * @param generations the {@link List} of {@link ImageGeneration} returned by the AI
	 * provider.
	 */
	public ImageResponse(List<ImageGeneration> generations) {
		this(generations, new ImageResponseMetadata());
	}

	/**
	 * Construct a new {@link ImageResponse} instance.
	 * @param generations the {@link List} of {@link ImageGeneration} returned by the AI
	 * provider.
	 * @param imageResponseMetadata {@link ImageResponseMetadata} containing information
	 * about the use of the AI provider's API.
	 */
	public ImageResponse(List<ImageGeneration> generations, ImageResponseMetadata imageResponseMetadata) {
		this.imageResponseMetadata = imageResponseMetadata;
		this.imageGenerations = List.copyOf(generations);
	}

	/**
	 * The {@link List} of {@link ImageGeneration generated outputs}.
	 * <p>
	 * It is a {@link List} of {@link List lists} because the Prompt could request
	 * multiple output {@link ImageGeneration generations}.
	 * @return the {@link List} of {@link ImageGeneration generated outputs}.
	 */
	@Override
	public List<ImageGeneration> getResults() {
		return this.imageGenerations;
	}

	/**
	 * @return Returns the first {@link ImageGeneration} in the generations list.
	 */
	@Override
	public @Nullable ImageGeneration getResult() {
		if (CollectionUtils.isEmpty(this.imageGenerations)) {
			return null;
		}
		return this.imageGenerations.get(0);
	}

	/**
	 * @return Returns {@link ImageResponseMetadata} containing information about the use
	 * of the AI provider's API.
	 */
	@Override
	public ImageResponseMetadata getMetadata() {
		return this.imageResponseMetadata;
	}

	@Override
	public String toString() {
		return "ImageResponse [" + "imageResponseMetadata=" + this.imageResponseMetadata + ", imageGenerations="
				+ this.imageGenerations + "]";
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ImageResponse that)) {
			return false;
		}
		return Objects.equals(this.imageResponseMetadata, that.imageResponseMetadata)
				&& Objects.equals(this.imageGenerations, that.imageGenerations);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.imageResponseMetadata, this.imageGenerations);
	}

}
