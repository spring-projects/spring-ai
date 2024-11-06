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

package org.springframework.ai.autoconfigure.ollama;

import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.ollama.OllamaContainer;

import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.OllamaModelManager;
import org.springframework.ai.ollama.management.PullModelStrategy;

public class BaseOllamaIT {

	private static OllamaContainer ollamaContainer;

	// Toggle for running tests locally on native Ollama for a faster feedback loop.
	private static final boolean useTestcontainers = true;

	@BeforeAll
	public static void setUp() {
		if (useTestcontainers && !isDisabled()) {
			ollamaContainer = new OllamaContainer(OllamaImage.IMAGE).withReuse(true);
			ollamaContainer.start();
		}
	}

	@AfterAll
	public static void tearDown() {
		if (ollamaContainer != null) {
			ollamaContainer.stop();
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

	public static String buildConnectionWithModel(String model) {
		var baseUrl = "http://localhost:11434";
		if (useTestcontainers) {
			baseUrl = ollamaContainer.getEndpoint();
		}

		var modelManagementOptions = ModelManagementOptions.builder()
			.withMaxRetries(2)
			.withTimeout(Duration.ofMinutes(10))
			.build();
		var ollamaModelManager = new OllamaModelManager(new OllamaApi(baseUrl), modelManagementOptions);
		ollamaModelManager.pullModel(model, PullModelStrategy.WHEN_MISSING);
		return baseUrl;
	}

}
