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

package org.springframework.ai.ollama.metadata;

import java.util.Optional;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.ollama.api.OllamaApi.EmbeddingsResponse;
import org.springframework.util.Assert;

/**
 * {@link Usage} implementation for {@literal Ollama} embeddings.
 *
 * @see Usage
 * @author Christian Tzolov
 */
public class OllamaEmbeddingUsage implements Usage {

	protected static final String AI_USAGE_STRING = "{ promptTokens: %1$d, generationTokens: %2$d, totalTokens: %3$d }";

	private Long promptTokens;

	public OllamaEmbeddingUsage(EmbeddingsResponse response) {
		this.promptTokens = Optional.ofNullable(response.promptEvalCount()).map(Integer::longValue).orElse(0L);
	}

	public static OllamaEmbeddingUsage from(EmbeddingsResponse response) {
		Assert.notNull(response, "OllamaApi.EmbeddingsResponse must not be null");
		return new OllamaEmbeddingUsage(response);
	}

	@Override
	public Long getPromptTokens() {
		return this.promptTokens;
	}

	@Override
	public Long getGenerationTokens() {
		return 0L;
	}

	@Override
	public String toString() {
		return AI_USAGE_STRING.formatted(getPromptTokens(), getGenerationTokens(), getTotalTokens());
	}

}
