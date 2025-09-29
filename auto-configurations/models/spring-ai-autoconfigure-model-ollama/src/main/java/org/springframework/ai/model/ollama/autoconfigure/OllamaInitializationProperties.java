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
import java.util.List;

import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ollama initialization configuration properties.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
@ConfigurationProperties(OllamaInitializationProperties.CONFIG_PREFIX)
public class OllamaInitializationProperties {

	public static final String CONFIG_PREFIX = "spring.ai.ollama.init";

	/**
	 * Chat models initialization settings.
	 */
	private final ModelTypeInit chat = new ModelTypeInit();

	/**
	 * Embedding models initialization settings.
	 */
	private final ModelTypeInit embedding = new ModelTypeInit();

	/**
	 * Whether to pull models at startup-time and how.
	 */
	private PullModelStrategy pullModelStrategy = PullModelStrategy.NEVER;

	/**
	 * How long to wait for a model to be pulled.
	 */
	private Duration timeout = Duration.ofMinutes(5);

	/**
	 * Maximum number of retries for the model pull operation.
	 */
	private int maxRetries = 0;

	public PullModelStrategy getPullModelStrategy() {
		return this.pullModelStrategy;
	}

	public void setPullModelStrategy(PullModelStrategy pullModelStrategy) {
		this.pullModelStrategy = pullModelStrategy;
	}

	public ModelTypeInit getChat() {
		return this.chat;
	}

	public ModelTypeInit getEmbedding() {
		return this.embedding;
	}

	public Duration getTimeout() {
		return this.timeout;
	}

	public void setTimeout(Duration timeout) {
		this.timeout = timeout;
	}

	public int getMaxRetries() {
		return this.maxRetries;
	}

	public void setMaxRetries(int maxRetries) {
		this.maxRetries = maxRetries;
	}

	public static class ModelTypeInit {

		/**
		 * Include this type of models in the initialization task.
		 */
		private boolean include = true;

		/**
		 * Additional models to initialize besides the ones configured via default
		 * properties.
		 */
		private List<String> additionalModels = List.of();

		public boolean isInclude() {
			return this.include;
		}

		public void setInclude(boolean include) {
			this.include = include;
		}

		public List<String> getAdditionalModels() {
			return this.additionalModels;
		}

		public void setAdditionalModels(List<String> additionalModels) {
			this.additionalModels = additionalModels;
		}

	}

}
