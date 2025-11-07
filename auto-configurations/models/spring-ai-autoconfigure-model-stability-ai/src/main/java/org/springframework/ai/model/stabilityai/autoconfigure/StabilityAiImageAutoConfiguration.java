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

package org.springframework.ai.model.stabilityai.autoconfigure;

import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.stabilityai.StabilityAiImageModel;
import org.springframework.ai.stabilityai.api.StabilityAiApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * {@link AutoConfiguration Auto-configuration} for StabilityAI Image Model.
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @author Ilayaperumal Gopinathan
 * @since 0.8.0
 */
@AutoConfiguration(after = RestClientAutoConfiguration.class)
@ConditionalOnClass(StabilityAiApi.class)
@ConditionalOnProperty(name = SpringAIModelProperties.IMAGE_MODEL, havingValue = SpringAIModels.STABILITY_AI,
		matchIfMissing = true)
@EnableConfigurationProperties({ StabilityAiConnectionProperties.class, StabilityAiImageProperties.class })
public class StabilityAiImageAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public StabilityAiApi stabilityAiApi(StabilityAiConnectionProperties commonProperties,
			StabilityAiImageProperties imageProperties, ObjectProvider<RestClient.Builder> restClientBuilderProvider) {

		String apiKey = StringUtils.hasText(imageProperties.getApiKey()) ? imageProperties.getApiKey()
				: commonProperties.getApiKey();

		String baseUrl = StringUtils.hasText(imageProperties.getBaseUrl()) ? imageProperties.getBaseUrl()
				: commonProperties.getBaseUrl();

		Assert.hasText(apiKey, "StabilityAI API key must be set");
		Assert.hasText(baseUrl, "StabilityAI base URL must be set");

		return new StabilityAiApi(apiKey, imageProperties.getOptions().getModel(), baseUrl,
				restClientBuilderProvider.getIfAvailable(RestClient::builder));
	}

	@Bean
	@ConditionalOnMissingBean
	public StabilityAiImageModel stabilityAiImageModel(StabilityAiApi stabilityAiApi,
			StabilityAiImageProperties stabilityAiImageProperties) {
		return new StabilityAiImageModel(stabilityAiApi, stabilityAiImageProperties.getOptions());
	}

}
