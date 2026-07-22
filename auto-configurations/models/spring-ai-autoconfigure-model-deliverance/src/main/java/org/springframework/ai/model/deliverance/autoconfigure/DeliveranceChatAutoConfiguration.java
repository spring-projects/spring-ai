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

package org.springframework.ai.model.deliverance.autoconfigure;

import tools.jackson.databind.ObjectMapper;

import org.springframework.ai.deliverance.DeliveranceChatModel;
import org.springframework.ai.deliverance.api.DeliveranceApi;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * {@link AutoConfiguration Auto-configuration} for Deliverance Chat Model.
 *
 * @author Edward Capriolo
 * @since 2.0.1
 */
@AutoConfiguration
@ConditionalOnClass(DeliveranceApi.class)
@EnableConfigurationProperties({ DeliveranceConnectionProperties.class, DeliveranceChatProperties.class })
@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = SpringAIModels.DELIVERANCE)
public class DeliveranceChatAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public DeliveranceChatModel deliveranceChatModel(DeliveranceConnectionProperties connectionProperties,
			DeliveranceChatProperties chatProperties, ToolCallingManager toolCallingManager) {
		return new DeliveranceChatModel(
				DeliveranceApi.create(connectionProperties.getBaseUrl(), connectionProperties.getApiKey()),
				new ObjectMapper(), chatProperties.toOptions(), toolCallingManager);
	}

}
