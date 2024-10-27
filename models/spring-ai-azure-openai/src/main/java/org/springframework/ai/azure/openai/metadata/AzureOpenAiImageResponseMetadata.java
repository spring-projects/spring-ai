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

package org.springframework.ai.azure.openai.metadata;

import java.util.Objects;

import com.azure.ai.openai.models.ImageGenerations;

import org.springframework.ai.image.ImageResponseMetadata;
import org.springframework.util.Assert;

/**
 * Represents metadata associated with an image response from the Azure OpenAI image
 * model. It provides additional information about the generative response from the Azure
 * OpenAI image model, including the creation timestamp of the generated image.
 *
 * @author Benoit Moussaud
 * @since 1.0.0 M1
 */
public class AzureOpenAiImageResponseMetadata extends ImageResponseMetadata {

	private final Long created;

	protected AzureOpenAiImageResponseMetadata(Long created) {
		this.created = created;
	}

	public static AzureOpenAiImageResponseMetadata from(ImageGenerations openAiImageResponse) {
		Assert.notNull(openAiImageResponse, "OpenAiImageResponse must not be null");
		return new AzureOpenAiImageResponseMetadata(openAiImageResponse.getCreatedAt().toEpochSecond());
	}

	@Override
	public Long getCreated() {
		return this.created;
	}

	@Override
	public String toString() {
		return "AzureOpenAiImageResponseMetadata{" + "created=" + this.created + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof AzureOpenAiImageResponseMetadata that)) {
			return false;
		}
		return Objects.equals(this.created, that.created);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.created);
	}

}
