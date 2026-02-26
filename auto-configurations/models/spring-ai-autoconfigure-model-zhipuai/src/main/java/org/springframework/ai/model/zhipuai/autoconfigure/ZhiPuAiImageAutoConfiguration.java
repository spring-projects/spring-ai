/*
 * Copyright 2023-2026 the original author or authors.
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

package org.springframework.ai.model.zhipuai.autoconfigure;

import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.zhipuai.ZhiPuAiImageModel;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.ai.zhipuai.api.ZhiPuAiImageApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * Image {@link AutoConfiguration Auto-configuration} for ZhiPuAI.
 *
 * @author Geng Rong
 * @author Ilayaperumal Gopinathan
 * @author Yanming Zhou
 */
@AutoConfiguration(after = RestClientAutoConfiguration.class)
@ConditionalOnClass(ZhiPuAiApi.class)
@ConditionalOnProperty(name = SpringAIModelProperties.IMAGE_MODEL, havingValue = SpringAIModels.ZHIPUAI,
		matchIfMissing = true)
@EnableConfigurationProperties({ ZhiPuAiConnectionProperties.class, ZhiPuAiImageProperties.class })
public class ZhiPuAiImageAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public ZhiPuAiImageModel zhiPuAiImageModel(ZhiPuAiConnectionProperties commonProperties,
			ZhiPuAiImageProperties imageProperties, ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			ObjectProvider<RetryTemplate> retryTemplate, ObjectProvider<ResponseErrorHandler> responseErrorHandler) {

		String apiKey = StringUtils.hasText(imageProperties.getApiKey()) ? imageProperties.getApiKey()
				: commonProperties.getApiKey();

		String baseUrl = StringUtils.hasText(imageProperties.getBaseUrl()) ? imageProperties.getBaseUrl()
				: commonProperties.getBaseUrl();

		Assert.hasText(apiKey, "ZhiPuAI API key must be set");
		Assert.hasText(baseUrl, "ZhiPuAI base URL must be set");

		// TODO add ZhiPuAiApi support for image
		var zhiPuAiImageApi = new ZhiPuAiImageApi(baseUrl, apiKey,
				restClientBuilderProvider.getIfAvailable(RestClient::builder),
				responseErrorHandler.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER));

		return new ZhiPuAiImageModel(zhiPuAiImageApi, imageProperties.getOptions(),
				retryTemplate.getIfUnique(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE));
	}

}
