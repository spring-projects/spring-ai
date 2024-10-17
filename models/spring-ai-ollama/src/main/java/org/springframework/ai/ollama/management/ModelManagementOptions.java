/*
 * Copyright 2024 the original author or authors.
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
package org.springframework.ai.ollama.management;

import java.time.Duration;

/**
 * Options for managing models in Ollama.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public record ModelManagementOptions(PullModelStrategy pullModelStrategy, Duration timeout, Integer maxRetries) {
	public static ModelManagementOptions defaults() {
		return new ModelManagementOptions(PullModelStrategy.NEVER, Duration.ofMinutes(5), 0);
	}
}
