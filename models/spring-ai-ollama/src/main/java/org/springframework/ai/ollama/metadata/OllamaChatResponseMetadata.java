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

import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.util.Assert;

/**
 * {@link ChatResponseMetadata} implementation for {@literal Ollama}
 *
 * @see ChatResponseMetadata
 * @author Fu Cheng
 */
public class OllamaChatResponseMetadata implements ChatResponseMetadata {

	protected static final String AI_METADATA_STRING = "{ @type: %1$s, usage: %2$s, rateLimit: %3$s }";

	public static OllamaChatResponseMetadata from(OllamaApi.ChatResponse response) {
		Assert.notNull(response, "OllamaApi.ChatResponse must not be null");
		Usage usage = OllamaUsage.from(response);
		return new OllamaChatResponseMetadata(usage);
	}

	private final Usage usage;

	protected OllamaChatResponseMetadata(Usage usage) {
		this.usage = usage;
	}

	@Override
	public Usage getUsage() {
		return this.usage;
	}

	@Override
	public String toString() {
		return AI_METADATA_STRING.formatted(getClass().getTypeName(), getUsage(), getRateLimit());
	}

}
