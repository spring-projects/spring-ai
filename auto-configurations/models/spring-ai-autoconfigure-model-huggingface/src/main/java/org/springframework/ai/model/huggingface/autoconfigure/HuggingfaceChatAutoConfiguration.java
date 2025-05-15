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

package org.springframework.ai.model.huggingface.autoconfigure;

import org.springframework.ai.huggingface.HuggingfaceChatModel;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.Assert;

@AutoConfiguration(beforeName = "org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration")
@ConditionalOnClass(HuggingfaceChatModel.class)
@EnableConfigurationProperties(HuggingfaceChatProperties.class)
@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = SpringAIModels.HUGGINGFACE,
		matchIfMissing = true)
public class HuggingfaceChatAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public HuggingfaceChatModel huggingfaceChatModel(HuggingfaceChatProperties huggingfaceChatProperties) {
		Assert.notNull(huggingfaceChatProperties.getApiKey(), "apiKey must not be null");
		Assert.notNull(huggingfaceChatProperties.getUrl(), "url must not be null");
		return new HuggingfaceChatModel(huggingfaceChatProperties.getApiKey(), huggingfaceChatProperties.getUrl());
	}

}
