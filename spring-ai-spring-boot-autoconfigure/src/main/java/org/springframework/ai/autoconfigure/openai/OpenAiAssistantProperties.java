/*
 * Copyright 2024 the original author or authors.
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
package org.springframework.ai.autoconfigure.openai;

import org.springframework.ai.openai.OpenAiAssistantOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * OpenAI Assistants autoconfiguration properties.
 *
 * @author Alexandros Pappas
 */
@ConfigurationProperties(OpenAiAssistantProperties.CONFIG_PREFIX)
public class OpenAiAssistantProperties extends OpenAiParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.openai.assistant";

	public static final String DEFAULT_CHAT_MODEL = "gpt-4o-mini-2024-07-18";

	/**
	 * Enable OpenAI Assistant api.
	 */
	private boolean enabled = true;

	/**
	 * Options for OpenAI Assistant API.
	 */
	@NestedConfigurationProperty
	private OpenAiAssistantOptions options = OpenAiAssistantOptions.builder().withModel(DEFAULT_CHAT_MODEL).build();

	public OpenAiAssistantOptions getOptions() {
		return this.options;
	}

	public void setOptions(OpenAiAssistantOptions options) {
		this.options = options;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}
