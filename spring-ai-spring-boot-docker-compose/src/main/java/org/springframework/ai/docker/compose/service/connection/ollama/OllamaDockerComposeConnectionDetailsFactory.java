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

package org.springframework.ai.docker.compose.service.connection.ollama;

import org.springframework.ai.model.ollama.autoconfigure.OllamaConnectionDetails;
import org.springframework.ai.ollama.api.common.OllamaApiConstants;
import org.springframework.boot.docker.compose.core.RunningService;
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionDetailsFactory;
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionSource;

/**
 * @author Eddú Meléndez
 * @author lambochen
 */
class OllamaDockerComposeConnectionDetailsFactory
		extends DockerComposeConnectionDetailsFactory<OllamaConnectionDetails> {

	private static final int OLLAMA_PORT = 11434;

	protected OllamaDockerComposeConnectionDetailsFactory() {
		super("ollama/ollama");
	}

	@Override
	protected OllamaConnectionDetails getDockerComposeConnectionDetails(DockerComposeConnectionSource source) {
		return new OllamaDockerComposeConnectionDetails(source.getRunningService());
	}

	/**
	 * {@link OllamaConnectionDetails} backed by a {@code Ollama} {@link RunningService}.
	 */
	static class OllamaDockerComposeConnectionDetails extends DockerComposeConnectionDetails
			implements OllamaConnectionDetails {

		private final String baseUrl;

		/**
		 * The path of the chat endpoint.
		 */
		private String chatPath = OllamaApiConstants.DEFAULT_CHAT_PATH;

		/**
		 * The path of the embed endpoint.
		 */
		private String embedPath = OllamaApiConstants.DEFAULT_EMBED_PATH;

		/**
		 * The path of the list models endpoint.
		 */
		private String listModelsPath = OllamaApiConstants.DEFAULT_LIST_MODELS_PATH;

		/**
		 * The path of the show model endpoint.
		 */
		private String showModelPath = OllamaApiConstants.DEFAULT_SHOW_MODEL_PATH;

		/**
		 * The path of the copy model endpoint.
		 */
		private String copyModelPath = OllamaApiConstants.DEFAULT_COPY_MODEL_PATH;

		/**
		 * The path of the delete model endpoint.
		 */
		private String deleteModelPath = OllamaApiConstants.DEFAULT_DELETE_MODEL_PATH;

		/**
		 * The path of the pull model endpoint.
		 */
		private String pullModelPath = OllamaApiConstants.DEFAULT_PULL_MODEL_PATH;

		OllamaDockerComposeConnectionDetails(RunningService service) {
			super(service);
			this.baseUrl = "http://" + service.host() + ":" + service.ports().get(OLLAMA_PORT);
		}

		@Override
		public String getBaseUrl() {
			return this.baseUrl;
		}

		@Override
		public String getChatPath() {
			return this.chatPath;
		}

		@Override
		public String getEmbedPath() {
			return this.embedPath;
		}

		@Override
		public String getListModelsPath() {
			return this.listModelsPath;
		}

		@Override
		public String getShowModelPath() {
			return this.showModelPath;
		}

		@Override
		public String getCopyModelPath() {
			return this.copyModelPath;
		}

		@Override
		public String getDeleteModelPath() {
			return this.deleteModelPath;
		}

		@Override
		public String getPullModelPath() {
			return this.pullModelPath;
		}

	}

}
