/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.model.reckoning.autoconfigure;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.reckoning.ReckoningChatOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Chat properties for Reckoning.
 *
 * @since 2.1.0
 */
@ConfigurationProperties(ReckoningChatProperties.CONFIG_PREFIX)
@SuppressWarnings("NullAway")
public class ReckoningChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.reckoning.chat";

	private @Nullable String model;

	private @Nullable Double temperature;

	private @Nullable Integer maxTokens;

	private @Nullable Double topP;

	private @Nullable Integer topK;

	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	public @Nullable Double getTemperature() {
		return this.temperature;
	}

	public void setTemperature(@Nullable Double temperature) {
		this.temperature = temperature;
	}

	public @Nullable Integer getMaxTokens() {
		return this.maxTokens;
	}

	public void setMaxTokens(@Nullable Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	public @Nullable Double getTopP() {
		return this.topP;
	}

	public void setTopP(@Nullable Double topP) {
		this.topP = topP;
	}

	public @Nullable Integer getTopK() {
		return this.topK;
	}

	public void setTopK(@Nullable Integer topK) {
		this.topK = topK;
	}

	public ReckoningChatOptions toOptions() {
		return ReckoningChatOptions.builder()
			.model(this.model)
			.temperature(this.temperature)
			.maxTokens(this.maxTokens)
			.topP(this.topP)
			.topK(this.topK)
			.build();
	}

}
