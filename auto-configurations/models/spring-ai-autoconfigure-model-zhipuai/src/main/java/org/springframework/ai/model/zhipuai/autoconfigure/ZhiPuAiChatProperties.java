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

package org.springframework.ai.model.zhipuai.autoconfigure;

import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for ZhiPuAI chat model.
 *
 * @author Geng Rong
 */
@ConfigurationProperties(ZhiPuAiChatProperties.CONFIG_PREFIX)
public class ZhiPuAiChatProperties extends ZhiPuAiParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.zhipuai.chat";

	public static final String DEFAULT_CHAT_MODEL = ZhiPuAiApi.ChatModel.GLM_4_Air.value;

	@NestedConfigurationProperty
	private final ZhiPuAiChatOptions options = ZhiPuAiChatOptions.builder().model(DEFAULT_CHAT_MODEL).build();

	public ZhiPuAiChatOptions getOptions() {
		return this.options;
	}

}
