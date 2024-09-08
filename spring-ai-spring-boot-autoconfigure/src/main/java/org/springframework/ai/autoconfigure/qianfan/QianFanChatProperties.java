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
package org.springframework.ai.autoconfigure.qianfan;

import org.springframework.ai.qianfan.QianFanChatOptions;
import org.springframework.ai.qianfan.api.QianFanApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * @author Geng Rong
 */
@ConfigurationProperties(QianFanChatProperties.CONFIG_PREFIX)
public class QianFanChatProperties extends QianFanParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.qianfan.chat";

	public static final String DEFAULT_CHAT_MODEL = QianFanApi.ChatModel.ERNIE_Speed_8K.value;

	private static final Double DEFAULT_TEMPERATURE = 0.7;

	/**
	 * Enable QianFan chat client.
	 */
	private boolean enabled = true;

	@NestedConfigurationProperty
	private QianFanChatOptions options = QianFanChatOptions.builder()
		.withModel(DEFAULT_CHAT_MODEL)
		.withTemperature(DEFAULT_TEMPERATURE)
		.build();

	public QianFanChatOptions getOptions() {
		return options;
	}

	public void setOptions(QianFanChatOptions options) {
		this.options = options;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}
