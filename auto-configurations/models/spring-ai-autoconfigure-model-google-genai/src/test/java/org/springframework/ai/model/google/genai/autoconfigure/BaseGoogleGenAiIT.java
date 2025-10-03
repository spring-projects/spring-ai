/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.model.google.genai.autoconfigure;

import java.util.Arrays;
import java.util.stream.Stream;

import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingConnectionAutoConfiguration;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiTextEmbeddingAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;

/**
 * Base class to provide consistent AutoConfigurations for Google GenAI integration tests.
 */
public abstract class BaseGoogleGenAiIT {

	/**
	 * AutoConfigurations needed for Google GenAI Chat model.
	 */
	public static AutoConfigurations googleGenAiChatAutoConfig(Class<?>... additional) {
		Class<?>[] dependencies = { SpringAiRetryAutoConfiguration.class, ToolCallingAutoConfiguration.class,
				GoogleGenAiChatAutoConfiguration.class };
		Class<?>[] all = Stream.concat(Arrays.stream(dependencies), Arrays.stream(additional)).toArray(Class<?>[]::new);
		return AutoConfigurations.of(all);
	}

	/**
	 * AutoConfigurations needed for Google GenAI Text Embedding model.
	 */
	public static AutoConfigurations googleGenAiEmbeddingAutoConfig(Class<?>... additional) {
		Class<?>[] dependencies = { SpringAiRetryAutoConfiguration.class,
				GoogleGenAiEmbeddingConnectionAutoConfiguration.class,
				GoogleGenAiTextEmbeddingAutoConfiguration.class };
		Class<?>[] all = Stream.concat(Arrays.stream(dependencies), Arrays.stream(additional)).toArray(Class<?>[]::new);
		return AutoConfigurations.of(all);
	}

}
