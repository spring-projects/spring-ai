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

package org.springframework.ai.model.minimax.autoconfigure;

import org.springframework.ai.minimax.MiniMaxChatOptions;
import org.springframework.ai.minimax.api.MiniMaxApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for MiniMax chat model.
 *
 * @author Geng Rong
 */
@ConfigurationProperties(MiniMaxChatProperties.CONFIG_PREFIX)
public class MiniMaxChatProperties extends MiniMaxParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.minimax.chat";

	public static final String DEFAULT_CHAT_MODEL = MiniMaxApi.ChatModel.ABAB_5_5_Chat.value;

	@NestedConfigurationProperty
	private final MiniMaxChatOptions options = MiniMaxChatOptions.builder().model(DEFAULT_CHAT_MODEL).build();

	public MiniMaxChatOptions getOptions() {
		return this.options;
	}

}
