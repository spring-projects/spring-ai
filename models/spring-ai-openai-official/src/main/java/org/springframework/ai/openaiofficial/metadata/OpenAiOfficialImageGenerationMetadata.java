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

package org.springframework.ai.openaiofficial.metadata;

import org.springframework.ai.image.ImageGenerationMetadata;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents the metadata for image generation using the OpenAI Java SDK.
 *
 * @author Julien Dubois
 */
public class OpenAiOfficialImageGenerationMetadata implements ImageGenerationMetadata {

	private final String revisedPrompt;

	public OpenAiOfficialImageGenerationMetadata(Optional<String> revisedPrompt) {
		if (revisedPrompt.isPresent()) {
			this.revisedPrompt = revisedPrompt.get();
		}
		else {
			this.revisedPrompt = null;
		}
	}

	public String getRevisedPrompt() {
		return this.revisedPrompt;
	}

	@Override
	public String toString() {
		return "OpenAiOfficialImageGenerationMetadata{" + "revisedPrompt='" + revisedPrompt + '\'' + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof OpenAiOfficialImageGenerationMetadata that)) {
			return false;
		}
		return Objects.equals(this.revisedPrompt, that.revisedPrompt);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.revisedPrompt);
	}

}
