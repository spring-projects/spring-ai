/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.model.google.genai.autoconfigure.embedding;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ConfigurationCondition;

/**
 * Condition that matches when any Google GenAI embedding credential is configured: either
 * an API key (Gemini Developer API mode) or a project ID (Vertex AI mode).
 *
 * @author Gorre Surya
 * @since 1.1.0
 */
class OnGoogleGenAiEmbeddingCredentials extends AnyNestedCondition {

	OnGoogleGenAiEmbeddingCredentials() {
		super(ConfigurationCondition.ConfigurationPhase.REGISTER_BEAN);
	}

	@ConditionalOnProperty(prefix = GoogleGenAiEmbeddingConnectionProperties.CONFIG_PREFIX, name = "api-key")
	static class HasApiKey {

	}

	@ConditionalOnProperty(prefix = GoogleGenAiEmbeddingConnectionProperties.CONFIG_PREFIX, name = "project-id")
	static class HasProjectId {

	}

}
