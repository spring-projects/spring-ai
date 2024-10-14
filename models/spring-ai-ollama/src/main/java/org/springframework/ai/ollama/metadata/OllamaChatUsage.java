/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.util.Assert;

/**
 * {@link Usage} implementation for {@literal Ollama}
 *
 * @see Usage
 * @author Fu Cheng
 */
public class OllamaChatUsage implements Usage {

	protected static final String AI_USAGE_STRING = "{ promptTokens: %1$d, generationTokens: %2$d, totalTokens: %3$d }";

	public static OllamaChatUsage from(OllamaApi.ChatResponse response) {
		Assert.notNull(response, "OllamaApi.ChatResponse must not be null");
		return new OllamaChatUsage(response);
	}

	private final OllamaApi.ChatResponse response;

	public OllamaChatUsage(OllamaApi.ChatResponse response) {
		this.response = response;
	}

	@Override
	public Long getPromptTokens() {
		return Optional.ofNullable(response.promptEvalCount()).map(Integer::longValue).orElse(0L);
	}

	@Override
	public Long getGenerationTokens() {
		return Optional.ofNullable(response.evalCount()).map(Integer::longValue).orElse(0L);
	}

	@Override
	public String toString() {
		return AI_USAGE_STRING.formatted(getPromptTokens(), getGenerationTokens(), getTotalTokens());
	}

}
