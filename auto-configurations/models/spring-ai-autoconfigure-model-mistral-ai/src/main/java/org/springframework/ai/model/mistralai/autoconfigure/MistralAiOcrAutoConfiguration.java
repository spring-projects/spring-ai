/*
 * Copyright 2025-2026 the original author or authors.
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

package org.springframework.ai.model.mistralai.autoconfigure;

import org.springframework.ai.mistralai.ocr.MistralOcrApi;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * OCR {@link AutoConfiguration Auto-configuration} for Mistral AI OCR.
 *
 * @author Alexandros Pappas
 * @author Nicolas Krier
 * @since 1.1.0
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class })
@ConditionalOnClass(MistralOcrApi.class)
@ConditionalOnProperty(name = "spring.ai.model.ocr", havingValue = SpringAIModels.MISTRAL, matchIfMissing = true)
@EnableConfigurationProperties({ MistralAiCommonProperties.class, MistralAiOcrProperties.class })
public class MistralAiOcrAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	MistralOcrApi mistralOcrApi(MistralAiCommonProperties commonProperties, MistralAiOcrProperties ocrProperties,
			ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			ObjectProvider<ResponseErrorHandler> responseErrorHandler) {
		return new MistralOcrApi(ocrProperties.getBaseUrlOrDefaultFrom(commonProperties),
				ocrProperties.getApiKeyOrDefaultFrom(commonProperties),
				restClientBuilderProvider.getIfAvailable(RestClient::builder),
				responseErrorHandler.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER));
	}

}
