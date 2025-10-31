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

package org.springframework.ai.autoconfigure.vertexai.imagen;

import org.springframework.ai.vertexai.imagen.VertexAiImagenImageOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Vertex AI Imagen.
 *
 * @author Sami Marzouki
 */
@ConfigurationProperties(VertexAiImagenImageProperties.CONFIG_PREFIX)
public class VertexAiImagenImageProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vertex.ai.imagen.generator";

	private boolean enabled = true;

	/**
	 * Vertex AI Imagen API options.
	 */
	private VertexAiImagenImageOptions options = VertexAiImagenImageOptions.builder()
			.model(VertexAiImagenImageOptions.DEFAULT_MODEL_NAME)
			.build();

	public VertexAiImagenImageOptions getOptions() {
		return this.options;
	}

	public void setOptions(VertexAiImagenImageOptions options) {
		this.options = options;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}
