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

package org.springframework.ai.ollama;

import java.time.Duration;

import org.testcontainers.ollama.OllamaContainer;

import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.OllamaModelManager;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.util.StringUtils;

public class BaseOllamaIT {

	// Toggle for running tests locally on native Ollama for a faster feedback loop.
	private static final boolean useTestcontainers = true;

	public static OllamaContainer ollamaContainer;

	static {
		if (useTestcontainers) {
			ollamaContainer = new OllamaContainer(OllamaImage.DEFAULT_IMAGE).withReuse(true);
			ollamaContainer.start();
		}
	}

	/**
	 * Change the return value to false in order to run multiple Ollama IT tests locally
	 * reusing the same container image.
	 *
	 * Also, add the entry
	 *
	 * testcontainers.reuse.enable=true
	 *
	 * to the file ".testcontainers.properties" located in your home directory
	 */
	public static boolean isDisabled() {
		return true;
	}

	public static OllamaApi buildOllamaApi() {
		return buildOllamaApiWithModel(null);
	}

	public static OllamaApi buildOllamaApiWithModel(String model) {
		var baseUrl = "http://localhost:11434";
		if (useTestcontainers) {
			baseUrl = ollamaContainer.getEndpoint();
		}
		var ollamaApi = new OllamaApi(baseUrl);

		if (StringUtils.hasText(model)) {
			ensureModelIsPresent(ollamaApi, model);
		}

		return ollamaApi;
	}

	public static void ensureModelIsPresent(OllamaApi ollamaApi, String model) {
		var modelManagementOptions = ModelManagementOptions.builder()
			.withMaxRetries(2)
			.withTimeout(Duration.ofMinutes(10))
			.build();
		var ollamaModelManager = new OllamaModelManager(ollamaApi, modelManagementOptions);
		ollamaModelManager.pullModel(model, PullModelStrategy.WHEN_MISSING);
	}

}
