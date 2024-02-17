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
import org.springframework.ai.mistral.MistralAiChatClient;
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
 * @author Christian Tzolov
 * @since 0.8.1
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class })
@EnableConfigurationProperties({ MistralAiEmbeddingProperties.class, MistralAiCommonProperties.class,
		MistralAiChatProperties.class })
@ConditionalOnClass(MistralAiApi.class)
public class MistralAiAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = MistralAiEmbeddingProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public EmbeddingClient mistralAiEmbeddingClient(MistralAiCommonProperties commonProperties,
			MistralAiEmbeddingProperties embeddingProperties, RestClient.Builder restClientBuilder) {

		var mistralAiApi = mistralAiApi(embeddingProperties.getApiKey(), commonProperties.getApiKey(),
				embeddingProperties.getBaseUrl(), commonProperties.getBaseUrl(), restClientBuilder);

		return new MistralAiEmbeddingClient(mistralAiApi, embeddingProperties.getMetadataMode(),
				embeddingProperties.getOptions());
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = MistralAiChatProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public MistralAiChatClient mistralAiChatClient(MistralAiCommonProperties commonProperties,
			MistralAiChatProperties chatProperties, RestClient.Builder restClientBuilder) {

		var mistralAiApi = mistralAiApi(chatProperties.getApiKey(), commonProperties.getApiKey(),
				chatProperties.getBaseUrl(), commonProperties.getBaseUrl(), restClientBuilder);

		return new MistralAiChatClient(mistralAiApi, chatProperties.getOptions());
	}

	private MistralAiApi mistralAiApi(String apiKey, String commonApiKey, String baseUrl, String commonBaseUrl,
			RestClient.Builder restClientBuilder) {

		var resolvedApiKey = StringUtils.hasText(apiKey) ? apiKey : commonApiKey;
		var resoledBaseUrl = StringUtils.hasText(baseUrl) ? baseUrl : commonBaseUrl;

		Assert.hasText(resolvedApiKey, "Mistral API key must be set");
		Assert.hasText(resoledBaseUrl, "Mistral base URL must be set");

		return new MistralAiApi(resoledBaseUrl, resolvedApiKey, restClientBuilder);
	}

}
