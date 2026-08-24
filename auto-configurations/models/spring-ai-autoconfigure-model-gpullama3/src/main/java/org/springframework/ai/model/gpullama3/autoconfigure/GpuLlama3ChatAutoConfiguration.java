/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.model.gpullama3.autoconfigure;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.gpullama3.GpuLlama3ChatModel;
import org.springframework.ai.gpullama3.GpuLlama3ChatOptions;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot autoconfiguration for the GPULlama3 chat model.
 */
@AutoConfiguration
@ConditionalOnClass(GpuLlama3ChatModel.class)
@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = SpringAIModels.GPULLAMA3,
		matchIfMissing = true)
@ConditionalOnProperty(prefix = GpuLlama3ChatProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
		matchIfMissing = true)
@ConditionalOnProperty(prefix = GpuLlama3ChatProperties.CONFIG_PREFIX, name = "model-path")
@EnableConfigurationProperties(GpuLlama3ChatProperties.class)
public class GpuLlama3ChatAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public GpuLlama3ChatOptions gpullama3ChatOptions(GpuLlama3ChatProperties properties) {
		return properties.toOptions();
	}

	@Bean(destroyMethod = "close")
	@ConditionalOnMissingBean(ChatModel.class)
	public GpuLlama3ChatModel gpullama3ChatModel(GpuLlama3ChatOptions options) {
		return new GpuLlama3ChatModel(options);
	}

}
