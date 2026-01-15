/*
 * Copyright 2023-2026 the original author or authors.
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

package org.springframework.ai.model.mistralai.autoconfigure;

import org.springframework.ai.mistralai.MistralAiChatOptions;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for Mistral AI chat.
 *
 * @author Ricken Bazolo
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Alexandros Pappas
 * @since 0.8.1
 */
@ConfigurationProperties(MistralAiChatProperties.CONFIG_PREFIX)
public class MistralAiChatProperties extends MistralAiParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.mistralai.chat";

	public static final String DEFAULT_CHAT_MODEL = MistralAiApi.ChatModel.MISTRAL_SMALL.getValue();

	private static final Double DEFAULT_TEMPERATURE = 0.7;

	private static final Double DEFAULT_TOP_P = 1.0;

	private static final Boolean IS_ENABLED = false;

	@NestedConfigurationProperty
	private final MistralAiChatOptions options = MistralAiChatOptions.builder()
		.model(DEFAULT_CHAT_MODEL)
		.temperature(DEFAULT_TEMPERATURE)
		.safePrompt(!IS_ENABLED)
		.topP(DEFAULT_TOP_P)
		.build();

	public MistralAiChatProperties() {
		super.setBaseUrl(MistralAiCommonProperties.DEFAULT_BASE_URL);
	}

	public MistralAiChatOptions getOptions() {
		return this.options;
	}

}
