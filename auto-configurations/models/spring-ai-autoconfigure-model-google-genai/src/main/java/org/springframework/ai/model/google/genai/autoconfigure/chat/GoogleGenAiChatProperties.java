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

package org.springframework.ai.model.google.genai.autoconfigure.chat;

import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for Google GenAI Chat.
 *
 * @author Christian Tzolov
 * @author Hyunsang Han
 * @since 1.1.0
 */
@ConfigurationProperties(GoogleGenAiChatProperties.CONFIG_PREFIX)
public class GoogleGenAiChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.google.genai.chat";

	public static final String DEFAULT_MODEL = GoogleGenAiChatModel.ChatModel.GEMINI_2_0_FLASH.getValue();

	/**
	 * Google GenAI API generative options.
	 */
	@NestedConfigurationProperty
	private GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
		.temperature(0.7)
		.candidateCount(1)
		.model(DEFAULT_MODEL)
		.build();

	public GoogleGenAiChatOptions getOptions() {
		return this.options;
	}

	public void setOptions(GoogleGenAiChatOptions options) {
		this.options = options;
	}

}
