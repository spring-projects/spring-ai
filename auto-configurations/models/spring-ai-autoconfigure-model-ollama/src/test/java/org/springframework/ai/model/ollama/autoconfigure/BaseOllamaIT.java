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

package org.springframework.ai.model.ollama.autoconfigure;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.ollama.OllamaContainer;

import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.OllamaModelManager;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.util.Assert;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "OLLAMA_AUTOCONF_TESTS_ENABLED", matches = "true")
public abstract class BaseOllamaIT {

	static {
		System.out.println("OLLAMA_AUTOCONF_TESTS_ENABLED=" + System.getenv("OLLAMA_AUTOCONF_TESTS_ENABLED"));
		System.out.println("System property=" + System.getProperty("OLLAMA_AUTOCONF_TESTS_ENABLED"));
	}
	private static final String OLLAMA_LOCAL_URL = "http://localhost:11434";

	private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);

	private static final int DEFAULT_MAX_RETRIES = 2;

	// Environment variable to control whether to create a new container or use existing
	// Ollama instance
	private static final boolean SKIP_CONTAINER_CREATION = Boolean
		.parseBoolean(System.getenv().getOrDefault("OLLAMA_WITH_REUSE", "false"));

	private static OllamaContainer ollamaContainer;

	private static final ThreadLocal<OllamaApi> ollamaApi = new ThreadLocal<>();

	/**
	 * Initialize the Ollama API with the specified model. When OLLAMA_WITH_REUSE=true
	 * (default), uses TestContainers withReuse feature. When OLLAMA_WITH_REUSE=false,
	 * connects to local Ollama instance.
	 * @param model the Ollama model to initialize (must not be null or empty)
	 * @return configured OllamaApi instance
	 * @throws IllegalArgumentException if model is null or empty
	 */
	protected static OllamaApi initializeOllama(final String model) {
		Assert.hasText(model, "Model name must be provided");

		if (!SKIP_CONTAINER_CREATION) {
			ollamaContainer = new OllamaContainer(OllamaImage.DEFAULT_IMAGE).withReuse(true);
			ollamaContainer.start();
		}

		final OllamaApi api = buildOllamaApiWithModel(model);
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

	public static OllamaApi buildOllamaApiWithModel(final String model) {
		final String baseUrl = SKIP_CONTAINER_CREATION ? OLLAMA_LOCAL_URL : ollamaContainer.getEndpoint();
		final OllamaApi api = OllamaApi.builder().baseUrl(baseUrl).build();
		ensureModelIsPresent(api, model);
		return api;
	}

	public String getBaseUrl() {
		return SKIP_CONTAINER_CREATION ? OLLAMA_LOCAL_URL : ollamaContainer.getEndpoint();
	}

	private static void ensureModelIsPresent(final OllamaApi ollamaApi, final String model) {
		final var modelManagementOptions = ModelManagementOptions.builder()
			.maxRetries(DEFAULT_MAX_RETRIES)
			.timeout(DEFAULT_TIMEOUT)
			.build();
		final var ollamaModelManager = new OllamaModelManager(ollamaApi, modelManagementOptions);
		ollamaModelManager.pullModel(model, PullModelStrategy.WHEN_MISSING);
	}

	public static AutoConfigurations ollamaAutoConfig(Class<?>... additionalAutoConfigurations) {
		List<Class<?>> autoConfigurations = new ArrayList<>(Arrays.asList(additionalAutoConfigurations));
		autoConfigurations.add(OllamaApiAutoConfiguration.class);
		return SpringAiTestAutoConfigurations.of(autoConfigurations.toArray(new Class<?>[0]));
	}

}
