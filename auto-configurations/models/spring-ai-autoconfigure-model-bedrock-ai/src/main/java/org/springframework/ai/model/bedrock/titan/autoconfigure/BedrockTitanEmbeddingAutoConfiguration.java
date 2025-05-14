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

package org.springframework.ai.model.bedrock.titan.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.observation.ObservationRegistry;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;

import org.springframework.ai.bedrock.titan.BedrockTitanEmbeddingModel;
import org.springframework.ai.bedrock.titan.api.TitanEmbeddingBedrockApi;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.model.bedrock.autoconfigure.BedrockAwsConnectionConfiguration;
import org.springframework.ai.model.bedrock.autoconfigure.BedrockAwsConnectionProperties;
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
 * {@link AutoConfiguration Auto-configuration} for Bedrock Titan Embedding Model.
 *
 * @author Christian Tzolov
 * @author Wei Jiang
 * @author SriVarshan P
 * @since 0.8.0
 */
@AutoConfiguration
@ConditionalOnClass(TitanEmbeddingBedrockApi.class)
@EnableConfigurationProperties({ BedrockTitanEmbeddingProperties.class, BedrockAwsConnectionProperties.class })
@ConditionalOnProperty(name = SpringAIModelProperties.EMBEDDING_MODEL, havingValue = SpringAIModels.BEDROCK_TITAN,
		matchIfMissing = true)
@Import(BedrockAwsConnectionConfiguration.class)
public class BedrockTitanEmbeddingAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean({ AwsCredentialsProvider.class, AwsRegionProvider.class })
	public TitanEmbeddingBedrockApi titanEmbeddingBedrockApi(AwsCredentialsProvider credentialsProvider,
			AwsRegionProvider regionProvider, BedrockTitanEmbeddingProperties properties,
			BedrockAwsConnectionProperties awsProperties, ObjectMapper objectMapper) {

		// Validate required properties
		if (properties.getModel() == null || awsProperties.getTimeout() == null) {
			throw new IllegalArgumentException("Required properties for TitanEmbeddingBedrockApi are missing.");
		}

		return new TitanEmbeddingBedrockApi(properties.getModel(), credentialsProvider, regionProvider.getRegion(),
				objectMapper, awsProperties.getTimeout());
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(TitanEmbeddingBedrockApi.class)
	public BedrockTitanEmbeddingModel titanEmbeddingModel(TitanEmbeddingBedrockApi titanEmbeddingApi,
			BedrockTitanEmbeddingProperties properties, ObjectProvider<ObservationRegistry> observationRegistry) {

		// Validate required properties
		if (properties.getInputType() == null) {
			throw new IllegalArgumentException("InputType property for BedrockTitanEmbeddingModel is missing.");
		}

		return new BedrockTitanEmbeddingModel(titanEmbeddingApi,
				observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.withInputType(properties.getInputType());
	}

}
