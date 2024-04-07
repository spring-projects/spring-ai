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
package org.springframework.ai.autoconfigure.bedrock.titan;

import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

import org.springframework.ai.autoconfigure.bedrock.BedrockAwsConnectionConfiguration;
import org.springframework.ai.autoconfigure.bedrock.BedrockAwsConnectionProperties;
import org.springframework.ai.bedrock.titan.BedrockTitanEmbeddingClient;
import org.springframework.ai.bedrock.titan.api.TitanEmbeddingBedrockApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * {@link AutoConfiguration Auto-configuration} for Bedrock Titan Embedding Client.
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
@AutoConfiguration
@ConditionalOnClass(TitanEmbeddingBedrockApi.class)
@EnableConfigurationProperties({ BedrockTitanEmbeddingProperties.class, BedrockAwsConnectionProperties.class })
@ConditionalOnProperty(prefix = BedrockTitanEmbeddingProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true")
@Import(BedrockAwsConnectionConfiguration.class)
public class BedrockTitanEmbeddingAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public TitanEmbeddingBedrockApi titanEmbeddingBedrockApi(AwsCredentialsProvider credentialsProvider,
			BedrockTitanEmbeddingProperties properties, BedrockAwsConnectionProperties awsProperties) {
		return new TitanEmbeddingBedrockApi(properties.getModel(), credentialsProvider, awsProperties.getRegion(),
				new ObjectMapper(), awsProperties.getTimeout());
	}

	@Bean
	@ConditionalOnMissingBean
	public BedrockTitanEmbeddingClient titanEmbeddingClient(TitanEmbeddingBedrockApi titanEmbeddingApi,
			BedrockTitanEmbeddingProperties properties) {

		return new BedrockTitanEmbeddingClient(titanEmbeddingApi).withInputType(properties.getInputType());
	}

}
