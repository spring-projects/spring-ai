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
package org.springframework.ai.autoconfigure.bedrock.mistral;

import java.util.List;

import org.springframework.ai.autoconfigure.bedrock.BedrockAwsConnectionConfiguration;
import org.springframework.ai.autoconfigure.bedrock.BedrockAwsConnectionProperties;
import org.springframework.ai.autoconfigure.bedrock.api.BedrockConverseApiAutoConfiguration;
import org.springframework.ai.bedrock.api.BedrockConverseApi;
import org.springframework.ai.bedrock.mistral.BedrockMistralChatModel;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.util.CollectionUtils;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

/**
 * {@link AutoConfiguration Auto-configuration} for Bedrock Mistral Chat Client.
 *
 * Leverages the Spring Cloud AWS to resolve the {@link AwsCredentialsProvider}.
 *
 * @author Wei Jiang
 * @since 1.0.0
 */
@AutoConfiguration(after = BedrockConverseApiAutoConfiguration.class)
@ConditionalOnClass(BedrockConverseApi.class)
@EnableConfigurationProperties({ BedrockMistralChatProperties.class, BedrockAwsConnectionProperties.class })
@ConditionalOnProperty(prefix = BedrockMistralChatProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true")
@Import(BedrockAwsConnectionConfiguration.class)
public class BedrockMistralChatAutoConfiguration {

	@Bean
	@ConditionalOnBean(BedrockConverseApi.class)
	public BedrockMistralChatModel mistralChatModel(BedrockConverseApi converseApi,
			BedrockMistralChatProperties properties, FunctionCallbackContext functionCallbackContext,
			List<FunctionCallback> toolFunctionCallbacks) {
		if (!CollectionUtils.isEmpty(toolFunctionCallbacks)) {
			properties.getOptions().getFunctionCallbacks().addAll(toolFunctionCallbacks);
		}

		return new BedrockMistralChatModel(properties.getModel(), converseApi, properties.getOptions(),
				functionCallbackContext);
	}

	@Bean
	@ConditionalOnMissingBean
	public FunctionCallbackContext springAiFunctionManager(ApplicationContext context) {
		FunctionCallbackContext manager = new FunctionCallbackContext();
		manager.setApplicationContext(context);
		return manager;
	}

}
