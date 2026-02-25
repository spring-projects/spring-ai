/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.model.bedrock.converse.autoconfigure;

import io.micrometer.observation.ObservationRegistry;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClientBuilder;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClientBuilder;

import org.springframework.ai.bedrock.converse.BedrockProxyChatModel;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.model.bedrock.autoconfigure.BedrockAwsConnectionProperties;
import org.springframework.ai.model.bedrock.autoconfigure.BedrockAwsCredentialsAndRegionAutoConfiguration;
import org.springframework.ai.model.bedrock.configure.BedrockAwsClientConfigurer;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * {@link AutoConfiguration Auto-configuration} for Bedrock Converse Proxy Chat Client.
 *
 * Leverages the Spring Cloud AWS to resolve the {@link AwsCredentialsProvider}.
 *
 * @author Christian Tzolov
 * @author Wei Jiang
 * @author Pawel Potaczala
 * @author Matej Nedic
 */
@AutoConfiguration(after = ToolCallingAutoConfiguration.class)
@EnableConfigurationProperties({ BedrockConverseProxyChatProperties.class, BedrockAwsConnectionProperties.class })
@ConditionalOnClass({ BedrockProxyChatModel.class, BedrockRuntimeClient.class, BedrockRuntimeAsyncClient.class })
@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = SpringAIModels.BEDROCK_CONVERSE,
		matchIfMissing = true)
@AutoConfigureAfter(BedrockAwsCredentialsAndRegionAutoConfiguration.class)
public class BedrockConverseProxyChatAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean({ AwsCredentialsProvider.class, AwsRegionProvider.class })
	public BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient(BedrockAwsConnectionProperties properties,
			AwsRegionProvider awsRegionProvider, AwsCredentialsProvider awsCredentialsProvider) {
		BedrockRuntimeAsyncClientBuilder builder = BedrockAwsClientConfigurer
			.configure(BedrockRuntimeAsyncClient.builder(), properties, awsRegionProvider, awsCredentialsProvider);
		BedrockAwsClientConfigurer.configureAsyncHttpClient(builder, properties);
		return builder.build();
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean({ AwsCredentialsProvider.class, AwsRegionProvider.class })
	public BedrockRuntimeClient bedrockRuntimeClient(BedrockAwsConnectionProperties properties,
			AwsRegionProvider awsRegionProvider, AwsCredentialsProvider awsCredentialsProvider) {
		BedrockRuntimeClientBuilder builder = BedrockAwsClientConfigurer.configure(BedrockRuntimeClient.builder(),
				properties, awsRegionProvider, awsCredentialsProvider);
		BedrockAwsClientConfigurer.configureSyncHttpClient(builder, properties);
		return builder.build();
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean({ BedrockRuntimeAsyncClient.class, BedrockRuntimeClient.class })
	public BedrockProxyChatModel bedrockProxyChatModel(BedrockConverseProxyChatProperties chatProperties,
			ToolCallingManager toolCallingManager, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ChatModelObservationConvention> observationConvention,
			BedrockRuntimeClient bedrockRuntimeClient, BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient,
			ObjectProvider<ToolExecutionEligibilityPredicate> bedrockToolExecutionEligibilityPredicate) {

		var chatModel = BedrockProxyChatModel.builder()
			.defaultOptions(chatProperties.getOptions())
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.toolCallingManager(toolCallingManager)
			.toolExecutionEligibilityPredicate(
					bedrockToolExecutionEligibilityPredicate.getIfUnique(DefaultToolExecutionEligibilityPredicate::new))
			.bedrockRuntimeClient(bedrockRuntimeClient)
			.bedrockRuntimeAsyncClient(bedrockRuntimeAsyncClient)
			.build();

		observationConvention.ifAvailable(chatModel::setObservationConvention);

		return chatModel;
	}

}
