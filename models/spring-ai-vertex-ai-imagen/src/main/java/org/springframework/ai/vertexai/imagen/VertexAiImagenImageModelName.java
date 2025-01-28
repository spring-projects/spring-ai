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

package org.springframework.ai.vertexai.imagen;

/**
 * Imagen on VertexAI Models:
 * - <a href="https://cloud.google.com/vertex-ai/generative-ai/docs/image/model-versioning">Image generation</a>
 *
 * @author Sami Marzouki
 */
public enum VertexAiImagenImageModelName {

	IMAGEN_3("imagen-3.0-generate-001"),

	IMAGEN_3_FAST("imagen-3.0-fast-generate-001"),

	IMAGEN_3_CUSTOMIZATION_AND_EDITING("imagen-3.0-capability-001"),

	IMAGEN_2_V006("imagegeneration@006"),

	IMAGEN_2_V005("imagegeneration@005"),

	IMAGEN_1_V002("imagegeneration@002");

	private final String value;

	VertexAiImagenImageModelName(String value) {
		this.value = value;
	}

	public String getValue() {
		return this.value;
	}
}
