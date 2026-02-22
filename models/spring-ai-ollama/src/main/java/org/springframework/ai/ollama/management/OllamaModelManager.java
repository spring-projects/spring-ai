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

package org.springframework.ai.ollama.management;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.util.retry.Retry;

import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaApi.DeleteModelRequest;
import org.springframework.ai.ollama.api.OllamaApi.ListModelResponse;
import org.springframework.ai.ollama.api.OllamaApi.PullModelRequest;
import org.springframework.ai.ollama.api.OllamaApi.CreateModelRequest;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Manage the lifecycle of models in Ollama.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class OllamaModelManager {

	private final Logger logger = LoggerFactory.getLogger(OllamaModelManager.class);

	private final OllamaApi ollamaApi;

	private final ModelManagementOptions options;

	public OllamaModelManager(OllamaApi ollamaApi) {
		this(ollamaApi, ModelManagementOptions.defaults());
	}

	public OllamaModelManager(OllamaApi ollamaApi, ModelManagementOptions options) {
		this.ollamaApi = ollamaApi;
		this.options = options;

		if (!CollectionUtils.isEmpty(options.additionalModels())) {
			options.additionalModels().forEach(this::pullModel);
		}
	}

	public boolean isModelAvailable(String modelName) {
		Assert.hasText(modelName, "modelName must not be empty");
		ListModelResponse listModelResponse = this.ollamaApi.listModels();
		if (!CollectionUtils.isEmpty(listModelResponse.models())) {
			var normalizedModelName = normalizeModelName(modelName);
			return listModelResponse.models().stream().anyMatch(m -> m.name().equals(normalizedModelName));
		}
		return false;
	}

	/**
	 * If the name follows the format "<string>:<string>", leave it as is. If the name
	 * follows the format "<string>" and doesn't include any ":" sign, then add ":latest"
	 * as a suffix.
	 */
	private String normalizeModelName(String modelName) {
		var modelNameWithoutSpaces = modelName.trim();
		if (modelNameWithoutSpaces.contains(":")) {
			return modelNameWithoutSpaces;
		}
		return modelNameWithoutSpaces + ":latest";
	}

	public void deleteModel(String modelName) {
		logger.info("Start deletion of model: {}", modelName);
		if (!isModelAvailable(modelName)) {
			logger.info("Model {} not found", modelName);
			return;
		}
		this.ollamaApi.deleteModel(new DeleteModelRequest(modelName));
		logger.info("Completed deletion of model: {}", modelName);
	}

	public void pullModel(String modelName) {
		pullModel(modelName, this.options.pullModelStrategy());
	}

	public void pullModel(String modelName, PullModelStrategy pullModelStrategy) {
		if (PullModelStrategy.NEVER.equals(pullModelStrategy)) {
			return;
		}

		if (PullModelStrategy.WHEN_MISSING.equals(pullModelStrategy)) {
			if (isModelAvailable(modelName)) {
				logger.debug("Model '{}' already available. Skipping pull operation.", modelName);
				return;
			}
		}

		// @formatter:off

		logger.info("Start pulling model: {}", modelName);
		this.ollamaApi.pullModel(new PullModelRequest(modelName))
				.bufferUntilChanged(OllamaApi.ProgressResponse::status)
				.doOnEach(signal -> {
					var progressResponses = signal.get();
					if (!CollectionUtils.isEmpty(progressResponses) && progressResponses.get(progressResponses.size() - 1) != null) {
						logger.info("Pulling the '{}' model - Status: {}", modelName, progressResponses.get(progressResponses.size() - 1).status());
					}
				})
				.takeUntil(progressResponses ->
					progressResponses.get(0) != null && "success".equals(progressResponses.get(0).status()))
				.timeout(this.options.timeout())
				.retryWhen(Retry.backoff(this.options.maxRetries(), Duration.ofSeconds(5)))
				.blockLast();
		logger.info("Completed pulling the '{}' model", modelName);

		// @formatter:on
	}

	public void createModel(String newModelName, String originalModelName) {
		// @formatter:off

		logger.info("Start creating model {} from {}", newModelName, originalModelName);
		this.ollamaApi.createModel(new CreateModelRequest(newModelName, originalModelName))
				.bufferUntilChanged(OllamaApi.ProgressResponse::status)
				.doOnEach(signal -> {
					var progressResponses = signal.get();
					if (!CollectionUtils.isEmpty(progressResponses) && progressResponses.get(progressResponses.size() - 1) != null) {
						logger.info("Creating the '{}' model - Status: {}", newModelName, progressResponses.get(progressResponses.size() - 1).status());
					}
				})
				.takeUntil(progressResponses ->
						progressResponses.get(0) != null && "success".equals(progressResponses.get(0).status()))
				.timeout(this.options.timeout())
				.retryWhen(Retry.backoff(this.options.maxRetries(), Duration.ofSeconds(5)))
				.blockLast();
		logger.info("Completed creating model {} from {}", newModelName, originalModelName);

		// @formatter:on
	}

}
