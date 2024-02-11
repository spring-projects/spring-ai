/*
 * Copyright 2023 the original author or authors.
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

package org.springframework.ai.autoconfigure.vertexai;

import org.springframework.ai.vertex.VertexAiChatOptions;
import org.springframework.ai.vertex.api.VertexAiApi;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(VertexAiChatProperties.CONFIG_PREFIX)
public class VertexAiChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vertex.ai.chat";

	/**
	 * Vertex AI PaLM API generative name. Defaults to chat-bison-001
	 */
	private String model = VertexAiApi.DEFAULT_GENERATE_MODEL;

	/**
	 * Vertex AI PaLM API generative options.
	 */
	private VertexAiChatOptions options = VertexAiChatOptions.builder()
		.withTemperature(0.7f)
		.withTopP(null)
		.withCandidateCount(1)
		.withTopK(20)
		.build();

	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public VertexAiChatOptions getOptions() {
		return this.options;
	}

	public void setOptions(VertexAiChatOptions options) {
		this.options = options;
	}

}
