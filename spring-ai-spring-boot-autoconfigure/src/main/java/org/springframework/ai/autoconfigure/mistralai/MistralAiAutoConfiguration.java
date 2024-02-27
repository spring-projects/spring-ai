/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.autoconfigure.mistralai;

import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.mistral.MistralAiEmbeddingClient;
import org.springframework.ai.mistral.api.MistralAiApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * @author Ricken Bazolo
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class })
@EnableConfigurationProperties({ MistralAiEmbeddingProperties.class, MistralAiConnectionProperties.class })
@ConditionalOnClass(MistralAiApi.class)
public class MistralAiAutoConfiguration {

	public static final String API_KEY_MUST_BE_SET = "Mistral API key must be set";

	public static final String BASE_URL_MUST_BE_SET = "Mistral base URL must be set";

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = MistralAiEmbeddingProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public EmbeddingClient mistralAiEmbeddingClient(MistralAiConnectionProperties commonProperties,
			MistralAiEmbeddingProperties embeddingProperties, RestClient.Builder restClientBuilder) {

		var apiKey = StringUtils.hasText(embeddingProperties.getApiKey()) ? embeddingProperties.getApiKey()
				: commonProperties.getApiKey();
		var baseUrl = StringUtils.hasText(embeddingProperties.getBaseUrl()) ? embeddingProperties.getBaseUrl()
				: commonProperties.getBaseUrl();

		Assert.hasText(apiKey, API_KEY_MUST_BE_SET);
		Assert.hasText(baseUrl, BASE_URL_MUST_BE_SET);

		var mistralAiApi = new MistralAiApi(baseUrl, apiKey, restClientBuilder);

		return new MistralAiEmbeddingClient(mistralAiApi, embeddingProperties.getMetadataMode(),
				embeddingProperties.getOptions());
	}

}
