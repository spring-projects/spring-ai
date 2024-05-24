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
package org.springframework.ai.autoconfigure.zhipuai;

import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * @author Geng Rong
 */
@ConfigurationProperties(ZhiPuAiChatProperties.CONFIG_PREFIX)
public class ZhiPuAiChatProperties extends ZhiPuAiParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.zhipuai.chat";

	public static final String DEFAULT_CHAT_MODEL = ZhiPuAiApi.ChatModel.GLM_3_Turbo.value;

	private static final Double DEFAULT_TEMPERATURE = 0.7;

	/**
	 * Enable ZhiPuAI chat model.
	 */
	private boolean enabled = true;

	@NestedConfigurationProperty
	private ZhiPuAiChatOptions options = ZhiPuAiChatOptions.builder()
		.withModel(DEFAULT_CHAT_MODEL)
		.withTemperature(DEFAULT_TEMPERATURE.floatValue())
		.build();

	public ZhiPuAiChatOptions getOptions() {
		return options;
	}

	public void setOptions(ZhiPuAiChatOptions options) {
		this.options = options;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}
