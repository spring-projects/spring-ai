/*
 * Copyright 2025-2026 the original author or authors.
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

package org.springframework.ai.vertexai.imagen.metadata;

import java.util.Objects;

import org.springframework.ai.image.ImageGenerationMetadata;

/**
 * VertexAiImagenImageGenerationMetadata is a class that defines the metadata for Imagen
 * on Vertex AI.
 *
 * @author Sami Marzouki
 */
public class VertexAiImagenImageGenerationMetadata implements ImageGenerationMetadata {

	private final String prompt;

	private final String model;

	private final String mimeType;

	public VertexAiImagenImageGenerationMetadata(String revisedPrompt, String model, String mimeType) {
		this.prompt = revisedPrompt;
		this.model = model;
		this.mimeType = mimeType;
	}

	public String getPrompt() {
		return prompt;
	}

	public String getModel() {
		return model;
	}

	public String getMimeType() {
		return mimeType;
	}

	@Override
	public String toString() {
		return "VertexAiImagenImageGenerationMetadata{" + "prompt='" + prompt + '\'' + ", model='" + model + '\''
				+ ", mimeType='" + mimeType + '\'' + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		VertexAiImagenImageGenerationMetadata that = (VertexAiImagenImageGenerationMetadata) o;
		return Objects.equals(prompt, that.prompt) && Objects.equals(model, that.model)
				&& Objects.equals(mimeType, that.mimeType);
	}

	@Override
	public int hashCode() {
		return Objects.hash(prompt, model, mimeType);
	}

}
