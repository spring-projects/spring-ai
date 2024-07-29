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
package org.springframework.ai.autoconfigure.bedrock.llama;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.bedrock.llama.BedrockLlamaChatModel;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;

import org.springframework.ai.autoconfigure.bedrock.BedrockAwsConnectionConfiguration;
import org.springframework.ai.autoconfigure.bedrock.BedrockAwsConnectionProperties;
import org.springframework.ai.bedrock.llama.api.LlamaChatBedrockApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * {@link AutoConfiguration Auto-configuration} for Bedrock Llama Chat Client.
 *
 * Leverages the Spring Cloud AWS to resolve the {@link AwsCredentialsProvider}.
 *
 * @author Christian Tzolov
 * @author Wei Jiang
 * @since 0.8.0
 */
@AutoConfiguration
@ConditionalOnClass(LlamaChatBedrockApi.class)
@EnableConfigurationProperties({ BedrockLlamaChatProperties.class, BedrockAwsConnectionProperties.class })
@ConditionalOnProperty(prefix = BedrockLlamaChatProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true")
@Import(BedrockAwsConnectionConfiguration.class)
public class BedrockLlamaChatAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean({ AwsCredentialsProvider.class, AwsRegionProvider.class })
	public LlamaChatBedrockApi llamaApi(AwsCredentialsProvider credentialsProvider, AwsRegionProvider regionProvider,
			BedrockLlamaChatProperties properties, BedrockAwsConnectionProperties awsProperties) {
		return new LlamaChatBedrockApi(properties.getModel(), credentialsProvider, regionProvider.getRegion(),
				new ObjectMapper(), awsProperties.getTimeout());
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(LlamaChatBedrockApi.class)
	public BedrockLlamaChatModel llamaChatModel(LlamaChatBedrockApi llamaApi, BedrockLlamaChatProperties properties) {

		return new BedrockLlamaChatModel(llamaApi, properties.getOptions());
	}

}
