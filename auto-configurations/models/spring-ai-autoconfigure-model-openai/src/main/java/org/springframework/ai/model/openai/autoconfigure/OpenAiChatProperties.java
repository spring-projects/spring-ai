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

package org.springframework.ai.model.openai.autoconfigure;

import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(OpenAiChatProperties.CONFIG_PREFIX)
public class OpenAiChatProperties extends OpenAiParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.openai.chat";

	public static final String DEFAULT_CHAT_MODEL = "gpt-5-mini";

	public static final String DEFAULT_COMPLETIONS_PATH = "/v1/chat/completions";

	public static final String DEFAULT_RESPONSES_PATH = "/v1/responses";

	private String completionsPath = DEFAULT_COMPLETIONS_PATH;

	private String responsesPath = DEFAULT_RESPONSES_PATH;

	@NestedConfigurationProperty
	private final OpenAiChatOptions options = OpenAiChatOptions.builder().model(DEFAULT_CHAT_MODEL).build();

	public OpenAiChatOptions getOptions() {
		return this.options;
	}

	public String getCompletionsPath() {
		return this.completionsPath;
	}

	public void setCompletionsPath(String completionsPath) {
		this.completionsPath = completionsPath;
	}

	public String getResponsesPath() {
		return this.responsesPath;
	}

	public void setResponsesPath(String responsesPath) {
		this.responsesPath = responsesPath;
	}

}
