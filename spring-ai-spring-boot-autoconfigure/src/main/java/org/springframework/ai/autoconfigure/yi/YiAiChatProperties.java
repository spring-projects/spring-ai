/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.autoconfigure.yi;

import org.springframework.ai.autoconfigure.yi.YiAiParentProperties;
import org.springframework.ai.yi.YiAiChatOptions;
import org.springframework.ai.yi.api.YiAiApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * @author Geng Rong
 */
@ConfigurationProperties(YiAiChatProperties.CONFIG_PREFIX)
public class YiAiChatProperties extends YiAiParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.yi.chat";

	public static final String DEFAULT_CHAT_MODEL = YiAiApi.ChatModel.YI_LARGE.value;

	private static final Double DEFAULT_TEMPERATURE = 0.7;

	/**
	 * Enable YiAi chat model.
	 */
	private boolean enabled = true;

	@NestedConfigurationProperty
	private YiAiChatOptions options = YiAiChatOptions.builder()
		.withModel(DEFAULT_CHAT_MODEL)
		.withTemperature(DEFAULT_TEMPERATURE.floatValue())
		.build();

	public YiAiChatOptions getOptions() {
		return options;
	}

	public void setOptions(YiAiChatOptions options) {
		this.options = options;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}
