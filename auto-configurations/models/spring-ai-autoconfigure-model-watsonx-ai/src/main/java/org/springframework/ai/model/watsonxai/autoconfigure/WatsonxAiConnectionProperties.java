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

package org.springframework.ai.model.watsonxai.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WatsonX.ai connection autoconfiguration properties.
 *
 * @author Pablo Sanchidrian Herrera
 * @author John Jario Moreno Rojas
 * @since 1.0.0
 */
@ConfigurationProperties(WatsonxAiConnectionProperties.CONFIG_PREFIX)
public class WatsonxAiConnectionProperties {

	public static final String CONFIG_PREFIX = "spring.ai.watsonx.ai";

	private String baseUrl = "https://us-south.ml.cloud.ibm.com/";

	private String streamEndpoint = "ml/v1/text/generation_stream?version=2023-05-29";

	private String textEndpoint = "ml/v1/text/generation?version=2023-05-29";

	private String embeddingEndpoint = "ml/v1/text/embeddings?version=2023-05-29";

	private String projectId;

	private String IAMToken;

	public String getBaseUrl() {
		return this.baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public String getStreamEndpoint() {
		return this.streamEndpoint;
	}

	public void setStreamEndpoint(String streamEndpoint) {
		this.streamEndpoint = streamEndpoint;
	}

	public String getTextEndpoint() {
		return this.textEndpoint;
	}

	public void setTextEndpoint(String textEndpoint) {
		this.textEndpoint = textEndpoint;
	}

	public String getEmbeddingEndpoint() {
		return this.embeddingEndpoint;
	}

	public void setEmbeddingEndpoint(String embeddingEndpoint) {
		this.embeddingEndpoint = embeddingEndpoint;
	}

	public String getProjectId() {
		return this.projectId;
	}

	public void setProjectId(String projectId) {
		this.projectId = projectId;
	}

	public String getIAMToken() {
		return this.IAMToken;
	}

	public void setIAMToken(String IAMToken) {
		this.IAMToken = IAMToken;
	}

}
