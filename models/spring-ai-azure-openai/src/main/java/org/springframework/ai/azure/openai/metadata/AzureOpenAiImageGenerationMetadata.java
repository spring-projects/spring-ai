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

import org.springframework.ai.image.ImageGenerationMetadata;

/**
 * Represents the metadata for image generation using Azure OpenAI.
 *
 * @author Benoit Moussaud
 * @since 1.0.0 M1
 */
public class AzureOpenAiImageGenerationMetadata implements ImageGenerationMetadata {

	private final String revisedPrompt;

	public AzureOpenAiImageGenerationMetadata(String revisedPrompt) {
		this.revisedPrompt = revisedPrompt;
	}

	public String getRevisedPrompt() {
		return this.revisedPrompt;
	}

	public String toString() {
		return "AzureOpenAiImageGenerationMetadata{" + "revisedPrompt='" + this.revisedPrompt + '\'' + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof AzureOpenAiImageGenerationMetadata that)) {
			return false;
		}
		return Objects.equals(this.revisedPrompt, that.revisedPrompt);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.revisedPrompt);
	}

}
