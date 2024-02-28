/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.autoconfigure.vertexai.gemini;

import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Vertex AI Gemini Chat.
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
@ConfigurationProperties(VertexAiGeminiChatProperties.CONFIG_PREFIX)
public class VertexAiGeminiChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vertex.ai.gemini.chat";

	/**
	 * Vertex AI Gemini API generative options.
	 */
	private VertexAiGeminiChatOptions options = VertexAiGeminiChatOptions.builder()
		.withTemperature(0.7f)
		.withCandidateCount(1)
		.build();

	public VertexAiGeminiChatOptions getOptions() {
		return this.options;
	}

	public void setOptions(VertexAiGeminiChatOptions options) {
		this.options = options;
	}

}
