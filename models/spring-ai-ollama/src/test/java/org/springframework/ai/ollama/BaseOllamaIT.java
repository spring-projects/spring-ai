/*
 * Copyright 2023-2025 the original author or authors.
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

import org.junit.jupiter.api.AfterAll;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.ollama.OllamaContainer;

import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.OllamaModelManager;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.util.Assert;

@Testcontainers
public abstract class BaseOllamaIT {

	private static final String OLLAMA_LOCAL_URL = "http://localhost:11434";

	private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);

	private static final int DEFAULT_MAX_RETRIES = 2;

	// Environment variable to control whether to create a new container or use existing
	// Ollama instance
	private static final boolean SKIP_CONTAINER_CREATION = Boolean
		.parseBoolean(System.getenv().getOrDefault("OLLAMA_WITH_REUSE", "false"));

	protected static final boolean KEEP_ADDITIONAL_MODELS = Boolean
		.parseBoolean(System.getenv().getOrDefault("PERSIST_MODELS", "false"));

	private static OllamaContainer ollamaContainer;

	private static final ThreadLocal<OllamaApi> ollamaApi = new ThreadLocal<>();

	/**
	 * Initialize the Ollama container and API with the specified model. This method
	 * should be called from @BeforeAll in subclasses.
	 * @param models the Ollama models to initialize (must not be null or empty)
	 * @return configured OllamaApi instance
	 * @throws IllegalArgumentException if model is null or empty
	 */
	protected static OllamaApi initializeOllama(String... models) {
		Assert.notEmpty(models, "at least one model name must be provided");

		if (!SKIP_CONTAINER_CREATION) {
			ollamaContainer = new OllamaContainer(OllamaImage.DEFAULT_IMAGE).withReuse(true);
			ollamaContainer.start();
		}

		final OllamaApi api = buildOllamaApiWithModel(models);
		ollamaApi.set(api);
		return api;
	}

	/**
	 * Get the initialized OllamaApi instance.
	 * @return the OllamaApi instance
	 * @throws IllegalStateException if called before initialization
	 */
	protected static OllamaApi getOllamaApi() {
		OllamaApi api = ollamaApi.get();
		Assert.state(api != null, "OllamaApi not initialized. Call initializeOllama first.");
		return api;
	}

	@AfterAll
	public static void tearDown() {
		if (ollamaContainer != null) {
			ollamaContainer.stop();
		}
	}

	private static OllamaApi buildOllamaApiWithModel(String... models) {
		final String baseUrl = SKIP_CONTAINER_CREATION ? OLLAMA_LOCAL_URL : ollamaContainer.getEndpoint();
		final OllamaApi api = OllamaApi.builder().baseUrl(baseUrl).build();
		ensureModelIsPresent(api, models);
		return api;
	}

	private static void ensureModelIsPresent(final OllamaApi ollamaApi, String... models) {
		final var modelManagementOptions = ModelManagementOptions.builder()
			.maxRetries(DEFAULT_MAX_RETRIES)
			.timeout(DEFAULT_TIMEOUT)
			.build();
		final var ollamaModelManager = new OllamaModelManager(ollamaApi, modelManagementOptions);
		for (String model : models) {
			ollamaModelManager.pullModel(model, PullModelStrategy.WHEN_MISSING);
		}
	}

}
