/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.model.openai.autoconfigure;

import static org.springframework.ai.model.openai.autoconfigure.OpenAIAutoConfigurationUtil.resolveConnectionProperties;

import org.springframework.ai.image.observation.ImageModelObservationConvention;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.openai.OpenAiImageEditModel;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

import io.micrometer.observation.ObservationRegistry;

/**
 * Image {@link AutoConfiguration Auto-configuration} for OpenAI.
 *
 * @author Minsoo Nam
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class, WebClientAutoConfiguration.class,
		SpringAiRetryAutoConfiguration.class })
@ConditionalOnClass(OpenAiApi.class)
@ConditionalOnProperty(name = SpringAIModelProperties.IMAGE_EDIT_MODEL, havingValue = SpringAIModels.OPENAI,
		matchIfMissing = true)
@EnableConfigurationProperties({ OpenAiConnectionProperties.class, OpenAiImageEditProperties.class })
@ImportAutoConfiguration(classes = { SpringAiRetryAutoConfiguration.class, RestClientAutoConfiguration.class,
		WebClientAutoConfiguration.class })
public class OpenAiImageEditAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public OpenAiImageEditModel openAiImageEditModel(OpenAiConnectionProperties commonProperties,
			OpenAiImageEditProperties imageEditProperties, ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			RetryTemplate retryTemplate, ResponseErrorHandler responseErrorHandler) {

		OpenAIAutoConfigurationUtil.ResolvedConnectionProperties resolved = resolveConnectionProperties(
				commonProperties, imageEditProperties, "image");

		var openAiImageApi = OpenAiImageApi.builder()
			.baseUrl(resolved.baseUrl())
			.apiKey(new SimpleApiKey(resolved.apiKey()))
			.headers(resolved.headers())
			.restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
			.responseErrorHandler(responseErrorHandler)
			.build();
		var imageModel = new OpenAiImageEditModel(openAiImageApi, imageEditProperties.getOptions(), retryTemplate);
		return imageModel;
	}

}
