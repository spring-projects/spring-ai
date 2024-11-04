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

package org.springframework.ai.autoconfigure.bedrock.converse;

import java.util.List;

import io.micrometer.observation.ObservationRegistry;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

import org.springframework.ai.autoconfigure.bedrock.BedrockAwsConnectionConfiguration;
import org.springframework.ai.autoconfigure.bedrock.BedrockAwsConnectionProperties;
import org.springframework.ai.bedrock.converse.BedrockProxyChatModel;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * {@link AutoConfiguration Auto-configuration} for Bedrock Converse Proxy Chat Client.
 *
 * Leverages the Spring Cloud AWS to resolve the {@link AwsCredentialsProvider}.
 *
 * @author Christian Tzolov
 * @author Wei Jiang
 */
@AutoConfiguration
@EnableConfigurationProperties({ BedrockConverseProxyChatProperties.class, BedrockAwsConnectionConfiguration.class })
@ConditionalOnClass({ BedrockRuntimeClient.class, BedrockRuntimeAsyncClient.class })
@ConditionalOnProperty(prefix = BedrockConverseProxyChatProperties.CONFIG_PREFIX, name = "enabled",
		havingValue = "true", matchIfMissing = true)
@Import(BedrockAwsConnectionConfiguration.class)
public class BedrockConverseProxyChatAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean({ AwsCredentialsProvider.class, AwsRegionProvider.class })
	public BedrockProxyChatModel bedrockProxyChatModel(AwsCredentialsProvider credentialsProvider,
			AwsRegionProvider regionProvider, BedrockAwsConnectionProperties connectionProperties,
			BedrockConverseProxyChatProperties chatProperties, FunctionCallbackContext functionCallbackContext,
			List<FunctionCallback> toolFunctionCallbacks, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ChatModelObservationConvention> observationConvention,
			ObjectProvider<BedrockRuntimeClient> bedrockRuntimeClient,
			ObjectProvider<BedrockRuntimeAsyncClient> bedrockRuntimeAsyncClient) {

		var chatModel = BedrockProxyChatModel.builder()
			.withCredentialsProvider(credentialsProvider)
			.withRegion(regionProvider.getRegion())
			.withTimeout(connectionProperties.getTimeout())
			.withDefaultOptions(chatProperties.getOptions())
			.withObservationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.withFunctionCallbackContext(functionCallbackContext)
			.withToolFunctionCallbacks(toolFunctionCallbacks)
			.withBedrockRuntimeClient(bedrockRuntimeClient.getIfAvailable())
			.withBedrockRuntimeAsyncClient(bedrockRuntimeAsyncClient.getIfAvailable())
			.build();

		observationConvention.ifAvailable(chatModel::setObservationConvention);

		return chatModel;
	}

	@Bean
	@ConditionalOnMissingBean
	public FunctionCallbackContext springAiFunctionManager(ApplicationContext context) {
		FunctionCallbackContext manager = new FunctionCallbackContext();
		manager.setApplicationContext(context);
		return manager;
	}

}
