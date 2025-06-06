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

import org.springframework.ai.ollama.api.common.OllamaApiConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ollama connection autoconfiguration properties.
 *
 * @author Christian Tzolov
 * @author lambochen
 * @since 0.8.0
 */
@ConfigurationProperties(OllamaConnectionProperties.CONFIG_PREFIX)
public class OllamaConnectionProperties {

	public static final String CONFIG_PREFIX = "spring.ai.ollama";

	/**
	 * Base URL where Ollama API server is running.
	 */
	private String baseUrl = OllamaApiConstants.DEFAULT_BASE_URL;

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

	public String getBaseUrl() {
		return this.baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public String getChatPath() {
		return chatPath;
	}

	public void setChatPath(String chatPath) {
		this.chatPath = chatPath;
	}

	public String getEmbedPath() {
		return embedPath;
	}

	public void setEmbedPath(String embedPath) {
		this.embedPath = embedPath;
	}

	public String getListModelsPath() {
		return listModelsPath;
	}

	public void setListModelsPath(String listModelsPath) {
		this.listModelsPath = listModelsPath;
	}

	public String getShowModelPath() {
		return showModelPath;
	}

	public void setShowModelPath(String showModelPath) {
		this.showModelPath = showModelPath;
	}

	public String getCopyModelPath() {
		return copyModelPath;
	}

	public void setCopyModelPath(String copyModelPath) {
		this.copyModelPath = copyModelPath;
	}

	public String getDeleteModelPath() {
		return deleteModelPath;
	}

	public void setDeleteModelPath(String deleteModelPath) {
		this.deleteModelPath = deleteModelPath;
	}

	public String getPullModelPath() {
		return pullModelPath;
	}

	public void setPullModelPath(String pullModelPath) {
		this.pullModelPath = pullModelPath;
	}

}
