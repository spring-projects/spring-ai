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

package org.springframework.ai.testcontainers.service.connection.ollama;

import org.springframework.ai.ollama.api.common.OllamaApiConstants;
import org.testcontainers.ollama.OllamaContainer;

import org.springframework.ai.model.ollama.autoconfigure.OllamaConnectionDetails;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionDetailsFactory;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionSource;

/**
 * @author Eddú Meléndez
 * @author lambochen
 */
class OllamaContainerConnectionDetailsFactory
		extends ContainerConnectionDetailsFactory<OllamaContainer, OllamaConnectionDetails> {

	@Override
	public OllamaConnectionDetails getContainerConnectionDetails(ContainerConnectionSource<OllamaContainer> source) {
		return new OllamaContainerConnectionDetails(source);
	}

	/**
	 * {@link OllamaConnectionDetails} backed by a {@link ContainerConnectionSource}.
	 */
	private static final class OllamaContainerConnectionDetails extends ContainerConnectionDetails<OllamaContainer>
			implements OllamaConnectionDetails {

		private OllamaContainerConnectionDetails(ContainerConnectionSource<OllamaContainer> source) {
			super(source);
		}

		@Override
		public String getBaseUrl() {
			return getContainer().getEndpoint();
		}

		@Override
		public String getChatPath() {
			return OllamaApiConstants.DEFAULT_CHAT_PATH;
		}

		@Override
		public String getEmbedPath() {
			return OllamaApiConstants.DEFAULT_EMBED_PATH;
		}

		@Override
		public String getListModelsPath() {
			return OllamaApiConstants.DEFAULT_LIST_MODELS_PATH;
		}

		@Override
		public String getShowModelPath() {
			return OllamaApiConstants.DEFAULT_SHOW_MODEL_PATH;
		}

		@Override
		public String getCopyModelPath() {
			return OllamaApiConstants.DEFAULT_COPY_MODEL_PATH;
		}

		@Override
		public String getDeleteModelPath() {
			return OllamaApiConstants.DEFAULT_DELETE_MODEL_PATH;
		}

		@Override
		public String getPullModelPath() {
			return OllamaApiConstants.DEFAULT_PULL_MODEL_PATH;
		}

	}

}
