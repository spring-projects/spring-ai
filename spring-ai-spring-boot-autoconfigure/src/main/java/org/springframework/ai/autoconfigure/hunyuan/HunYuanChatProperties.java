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

package org.springframework.ai.autoconfigure.hunyuan;

import org.springframework.ai.hunyuan.HunYuanChatOptions;
import org.springframework.ai.hunyuan.api.HunYuanApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for HunYuan chat client.
 *
 * @author Guo Junyu
 */
@ConfigurationProperties(HunYuanChatProperties.CONFIG_PREFIX)
public class HunYuanChatProperties extends HunYuanParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.hunyuan.chat";

	public static final String DEFAULT_CHAT_MODEL = HunYuanApi.ChatModel.HUNYUAN_PRO.getValue();

	private static final Double DEFAULT_TEMPERATURE = 0.7;

	/**
	 * Enable HunYuan chat client.
	 */
	private boolean enabled = true;

	@NestedConfigurationProperty
	private HunYuanChatOptions options = HunYuanChatOptions.builder()
		.model(DEFAULT_CHAT_MODEL)
		.temperature(DEFAULT_TEMPERATURE)
		.build();

	public HunYuanChatOptions getOptions() {
		return this.options;
	}

	public void setOptions(HunYuanChatOptions options) {
		this.options = options;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}
