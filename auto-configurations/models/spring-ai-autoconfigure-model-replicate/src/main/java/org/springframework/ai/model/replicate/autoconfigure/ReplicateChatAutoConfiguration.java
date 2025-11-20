/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.model.replicate.autoconfigure;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.replicate.ReplicateChatModel;
import org.springframework.ai.replicate.ReplicateMediaModel;
import org.springframework.ai.replicate.ReplicateStringModel;
import org.springframework.ai.replicate.ReplicateStructuredModel;
import org.springframework.ai.replicate.api.ReplicateApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * {@link AutoConfiguration Auto-configuration} for Replicate models.
 *
 * @author Rene Maierhofer
 * @since 1.1.0
 */
@AutoConfiguration(after = RestClientAutoConfiguration.class)
@ConditionalOnClass(ReplicateApi.class)
@EnableConfigurationProperties({ ReplicateConnectionProperties.class, ReplicateChatProperties.class,
		ReplicateMediaProperties.class, ReplicateStringProperties.class, ReplicateStructuredProperties.class })
@ConditionalOnProperty(prefix = ReplicateConnectionProperties.CONFIG_PREFIX, name = "api-token")
public class ReplicateChatAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = ReplicateConnectionProperties.CONFIG_PREFIX, name = "api-token")
	public ReplicateApi replicateApi(ReplicateConnectionProperties connectionProperties,
			ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			ObjectProvider<ResponseErrorHandler> responseErrorHandlerProvider) {

		if (!StringUtils.hasText(connectionProperties.getApiToken())) {
			throw new IllegalArgumentException(
					"Replicate API token must be configured via spring.ai.replicate.api-token");
		}

		var builder = ReplicateApi.builder()
			.apiKey(connectionProperties.getApiToken())
			.baseUrl(connectionProperties.getBaseUrl());

		RestClient.Builder restClientBuilder = restClientBuilderProvider.getIfAvailable(RestClient::builder);
		if (restClientBuilder != null) {
			builder.restClientBuilder(restClientBuilder);
		}

		ResponseErrorHandler errorHandler = responseErrorHandlerProvider.getIfAvailable();
		if (errorHandler != null) {
			builder.responseErrorHandler(errorHandler);
		}

		return builder.build();
	}

	@Bean
	@ConditionalOnMissingBean
	public ReplicateChatModel replicateChatModel(ReplicateApi replicateApi, ReplicateChatProperties chatProperties,
			ObjectProvider<ObservationRegistry> observationRegistry) {
		return ReplicateChatModel.builder()
			.replicateApi(replicateApi)
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.defaultOptions(chatProperties.getOptions())
			.build();
	}

	@Bean
	@ConditionalOnMissingBean
	public ReplicateMediaModel replicateMediaModel(ReplicateApi replicateApi,
			ReplicateMediaProperties mediaProperties) {
		return ReplicateMediaModel.builder()
			.replicateApi(replicateApi)
			.defaultOptions(mediaProperties.getOptions())
			.build();
	}

	@Bean
	@ConditionalOnMissingBean
	public ReplicateStringModel replicateStringModel(ReplicateApi replicateApi,
			ReplicateStringProperties stringProperties) {
		return ReplicateStringModel.builder()
			.replicateApi(replicateApi)
			.defaultOptions(stringProperties.getOptions())
			.build();
	}

	@Bean
	@ConditionalOnMissingBean
	public ReplicateStructuredModel replicateStructuredModel(ReplicateApi replicateApi,
			ReplicateStructuredProperties structuredProperties) {

		return ReplicateStructuredModel.builder()
			.replicateApi(replicateApi)
			.defaultOptions(structuredProperties.getOptions())
			.build();
	}

}
