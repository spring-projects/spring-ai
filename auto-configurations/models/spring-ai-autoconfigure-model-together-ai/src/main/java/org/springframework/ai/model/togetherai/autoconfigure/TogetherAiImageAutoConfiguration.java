/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.model.togetherai.autoconfigure;

import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.togetherai.TogetherAiImageModel;
import org.springframework.ai.togetherai.api.TogetherAiApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Together AI image auto-configuration.
 *
 * @since 2.0.1
 * @author Maksym Uimanov
 */
@AutoConfiguration
@ConditionalOnClass(TogetherAiApi.class)
@ConditionalOnProperty(
		name = SpringAIModelProperties.IMAGE_MODEL,
		havingValue = SpringAIModels.TOGETHER_AI,
		matchIfMissing = true
)
@EnableConfigurationProperties({
		TogetherAiConnectionProperties.class,
		TogetherAiImageProperties.class
})
public class TogetherAiImageAutoConfiguration {
	@Bean
	@ConditionalOnMissingBean
	public TogetherAiApi togetherAiApi(
			TogetherAiConnectionProperties commonProperties,
			TogetherAiImageProperties imageProperties,
			ObjectProvider<RestClient.Builder> restClientBuilderProvider
	) {
		String apiKey = StringUtils.hasText(imageProperties.getApiKey())
				? imageProperties.getApiKey()
				: commonProperties.getApiKey();

		String baseUrl = StringUtils.hasText(imageProperties.getBaseUrl())
				? imageProperties.getBaseUrl()
				: commonProperties.getBaseUrl();

		Assert.hasText(apiKey, "TogetherAI API key must be set");
		Assert.hasText(baseUrl, "TogetherAI base URL must be set");

		return new TogetherAiApi(apiKey, baseUrl, restClientBuilderProvider.getIfAvailable(RestClient::builder));
	}

	@Bean
	@ConditionalOnMissingBean
	public TogetherAiImageModel togetherAiImageModel(
			TogetherAiApi togetherAiApi,
			TogetherAiImageProperties togetherAiImageProperties
	) {
		return new TogetherAiImageModel(togetherAiApi, togetherAiImageProperties.toOptions());
	}
}
