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

package org.springframework.ai.autoconfigure.huggingface;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Hugging Face chat model.
 *
 * @author Christian Tzolov
 * @author Josh Long
 * @author Mark Pollack
 * @author Thomas Vitale
 */
@ConfigurationProperties(HuggingfaceChatProperties.CONFIG_PREFIX)
public class HuggingfaceChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.huggingface.chat";

	/**
	 * API Key to authenticate with the Inference Endpoint.
	 */
	private String apiKey;

	/**
	 * URL of the Inference Endpoint.
	 */
	private String url;

	/**
	 * Enable Hugging Face chat model.
	 */
	private boolean enabled = true;

	public String getApiKey() {
		return this.apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getUrl() {
		return this.url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}
