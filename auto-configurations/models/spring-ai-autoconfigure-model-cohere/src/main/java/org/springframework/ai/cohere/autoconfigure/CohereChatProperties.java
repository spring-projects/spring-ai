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

package org.springframework.ai.cohere.autoconfigure;

import org.springframework.ai.cohere.api.CohereApi;
import org.springframework.ai.cohere.chat.CohereChatOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for Cohere chat.
 *
 * @author Ricken Bazolo
 */
@ConfigurationProperties(CohereChatProperties.CONFIG_PREFIX)
public class CohereChatProperties extends CohereParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.cohere.chat";

	public static final String DEFAULT_CHAT_MODEL = CohereApi.ChatModel.COMMAND_A_R7B.getValue();

	private static final Double DEFAULT_TEMPERATURE = 0.3;

	private static final Double DEFAULT_TOP_P = 1.0;

	@NestedConfigurationProperty
	private CohereChatOptions options = CohereChatOptions.builder()
		.model(DEFAULT_CHAT_MODEL)
		.temperature(DEFAULT_TEMPERATURE)
		.topP(DEFAULT_TOP_P)
		.presencePenalty(0.0)
		.frequencyPenalty(0.0)
		.logprobs(false)
		.build();

	public CohereChatProperties() {
		super.setBaseUrl(CohereCommonProperties.DEFAULT_BASE_URL);
	}

	public CohereChatOptions getOptions() {
		return this.options;
	}

	public void setOptions(CohereChatOptions options) {
		this.options = options;
	}

}
