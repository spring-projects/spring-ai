/*
 * Copyright 2024-2025 the original author or authors.
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
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

import org.springframework.ai.bedrock.converse.BedrockProxyChatModel;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.model.bedrock.autoconfigure.BedrockAwsConnectionConfiguration;
import org.springframework.ai.model.bedrock.autoconfigure.BedrockAwsConnectionProperties;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * {@link AutoConfiguration Auto-configuration} for Bedrock Converse Proxy Chat Client.
 *
 * Leverages the Spring Cloud AWS to resolve the {@link AwsCredentialsProvider}.
 *
 * @author Christian Tzolov
 * @author Wei Jiang
 * @author Pawel Potaczala
 */
@AutoConfiguration(after = ToolCallingAutoConfiguration.class,
		beforeName = "org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration")
@EnableConfigurationProperties({ BedrockConverseProxyChatProperties.class, BedrockAwsConnectionConfiguration.class })
@ConditionalOnClass({ BedrockProxyChatModel.class, BedrockRuntimeClient.class, BedrockRuntimeAsyncClient.class })
@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = SpringAIModels.BEDROCK_CONVERSE,
		matchIfMissing = true)
@Import(BedrockAwsConnectionConfiguration.class)
public class BedrockConverseProxyChatAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean({ AwsCredentialsProvider.class, AwsRegionProvider.class })
	public BedrockProxyChatModel bedrockProxyChatModel(AwsCredentialsProvider credentialsProvider,
			AwsRegionProvider regionProvider, BedrockAwsConnectionProperties connectionProperties,
			BedrockConverseProxyChatProperties chatProperties, ToolCallingManager toolCallingManager,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ChatModelObservationConvention> observationConvention,
			ObjectProvider<BedrockRuntimeClient> bedrockRuntimeClient,
			ObjectProvider<BedrockRuntimeAsyncClient> bedrockRuntimeAsyncClient,
			ObjectProvider<ToolExecutionEligibilityPredicate> bedrockToolExecutionEligibilityPredicate) {

		var chatModel = BedrockProxyChatModel.builder()
			.credentialsProvider(credentialsProvider)
			.region(regionProvider.getRegion())
			.timeout(connectionProperties.getTimeout())
			.connectionTimeout(connectionProperties.getConnectionTimeout())
			.asyncReadTimeout(connectionProperties.getAsyncReadTimeout())
			.connectionAcquisitionTimeout(connectionProperties.getConnectionAcquisitionTimeout())
			.socketTimeout(connectionProperties.getSocketTimeout())
			.defaultOptions(chatProperties.getOptions())
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.toolCallingManager(toolCallingManager)
			.toolExecutionEligibilityPredicate(
					bedrockToolExecutionEligibilityPredicate.getIfUnique(DefaultToolExecutionEligibilityPredicate::new))
			.bedrockRuntimeClient(bedrockRuntimeClient.getIfAvailable())
			.bedrockRuntimeAsyncClient(bedrockRuntimeAsyncClient.getIfAvailable())
			.build();

		observationConvention.ifAvailable(chatModel::setObservationConvention);

		return chatModel;
	}

}
