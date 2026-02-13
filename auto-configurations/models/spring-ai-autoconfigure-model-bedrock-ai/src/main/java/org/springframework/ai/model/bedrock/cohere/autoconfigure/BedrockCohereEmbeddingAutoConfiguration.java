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

package org.springframework.ai.model.bedrock.cohere.autoconfigure;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.bedrock.cohere.BedrockCohereEmbeddingModel;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.model.bedrock.autoconfigure.BedrockAwsConnectionConfiguration;
import org.springframework.ai.model.bedrock.autoconfigure.BedrockAwsConnectionProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * {@link AutoConfiguration Auto-configuration} for Bedrock Cohere Embedding Model.
 *
 * @author Christian Tzolov
 * @author Wei Jiang
 * @since 0.8.0
 */
@AutoConfiguration
@ConditionalOnClass(CohereEmbeddingBedrockApi.class)
@EnableConfigurationProperties({ BedrockCohereEmbeddingProperties.class, BedrockAwsConnectionProperties.class })
@ConditionalOnProperty(name = SpringAIModelProperties.EMBEDDING_MODEL, havingValue = SpringAIModels.BEDROCK_COHERE,
		matchIfMissing = true)
@Import(BedrockAwsConnectionConfiguration.class)
public class BedrockCohereEmbeddingAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean({ AwsCredentialsProvider.class, AwsRegionProvider.class })
	public CohereEmbeddingBedrockApi cohereEmbeddingApi(AwsCredentialsProvider credentialsProvider,
			AwsRegionProvider regionProvider, BedrockCohereEmbeddingProperties properties,
			BedrockAwsConnectionProperties awsProperties, JsonMapper jsonMapper) {
		return new CohereEmbeddingBedrockApi(properties.getModel(), credentialsProvider, regionProvider.getRegion(),
				jsonMapper, awsProperties.getTimeout());
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(CohereEmbeddingBedrockApi.class)
	public BedrockCohereEmbeddingModel cohereEmbeddingModel(CohereEmbeddingBedrockApi cohereEmbeddingApi,
			BedrockCohereEmbeddingProperties properties) {

		return new BedrockCohereEmbeddingModel(cohereEmbeddingApi, properties.getOptions());
	}

}
