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

package org.springframework.ai.model.vertexai.autoconfigure;

import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.model.vertexai.autoconfigure.embedding.VertexAiEmbeddingConnectionAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;

/**
 * Utility class for VertexAI integration tests.
 *
 * @author Pawel Potaczala
 */
public final class VertexAiITUtil {

	private VertexAiITUtil() {
	}

	public static AutoConfigurations vertexAiToolAutoConfig(Class<?>... additionalAutoConfigurations) {
		Class<?>[] dependencies = new Class[] { SpringAiRetryAutoConfiguration.class,
				ToolCallingAutoConfiguration.class };
		Class<?>[] allAutoConfigurations = new Class[dependencies.length + additionalAutoConfigurations.length];
		System.arraycopy(dependencies, 0, allAutoConfigurations, 0, dependencies.length);
		System.arraycopy(additionalAutoConfigurations, 0, allAutoConfigurations, dependencies.length,
				additionalAutoConfigurations.length);

		return AutoConfigurations.of(allAutoConfigurations);
	}

	public static AutoConfigurations vertexAiEmbeddingAutoConfig(Class<?>... additionalAutoConfigurations) {
		Class<?>[] dependencies = new Class[] { SpringAiRetryAutoConfiguration.class,
				VertexAiEmbeddingConnectionAutoConfiguration.class };
		Class<?>[] allAutoConfigurations = new Class[dependencies.length + additionalAutoConfigurations.length];
		System.arraycopy(dependencies, 0, allAutoConfigurations, 0, dependencies.length);
		System.arraycopy(additionalAutoConfigurations, 0, allAutoConfigurations, dependencies.length,
				additionalAutoConfigurations.length);

		return AutoConfigurations.of(allAutoConfigurations);
	}

}
