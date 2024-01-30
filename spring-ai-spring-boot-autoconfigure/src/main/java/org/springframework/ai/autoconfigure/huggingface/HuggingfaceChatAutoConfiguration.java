/*
 * Copyright 2023 the original author or authors.
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

package org.springframework.ai.autoconfigure.huggingface;

import org.springframework.ai.huggingface.HuggingfaceChatClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(HuggingfaceChatClient.class)
@EnableConfigurationProperties(HuggingfaceChatProperties.class)
public class HuggingfaceChatAutoConfiguration {

	private final HuggingfaceChatProperties huggingfaceChatProperties;

	public HuggingfaceChatAutoConfiguration(HuggingfaceChatProperties huggingfaceChatProperties) {
		this.huggingfaceChatProperties = huggingfaceChatProperties;
	}

	@Bean
	public HuggingfaceChatClient huggingfaceChatClient(HuggingfaceChatProperties huggingfaceChatProperties) {
		return new HuggingfaceChatClient(huggingfaceChatProperties.getApiKey(), huggingfaceChatProperties.getUrl());
	}

}
