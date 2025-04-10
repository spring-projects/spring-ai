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

package org.springframework.ai.model.azure.openai.autoconfigure;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.credential.KeyCredential;
import com.azure.core.credential.TokenCredential;
import com.azure.core.util.ClientOptions;
import com.azure.core.util.Header;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Azure OpenAI Client Builder configuration.
 *
 * @author Piotr Olaszewski
 * @author Soby Chacko
 * @author Manuel Andreo Garcia
 * @author Ilayaperumal Gopinathan
 */
@ConditionalOnClass({ OpenAIClientBuilder.class })
@EnableConfigurationProperties(AzureOpenAiConnectionProperties.class)
public class AzureOpenAiClientBuilderConfiguration {

	private static final String APPLICATION_ID = "spring-ai";

	@Bean
	@ConditionalOnMissingBean // ({ OpenAIClient.class, TokenCredential.class })
	public OpenAIClientBuilder openAIClientBuilder(AzureOpenAiConnectionProperties connectionProperties,
			ObjectProvider<AzureOpenAIClientBuilderCustomizer> customizers) {

		if (StringUtils.hasText(connectionProperties.getApiKey())) {

			Assert.hasText(connectionProperties.getEndpoint(), "Endpoint must not be empty");

			Map<String, String> customHeaders = connectionProperties.getCustomHeaders();
			List<Header> headers = customHeaders.entrySet()
				.stream()
				.map(entry -> new Header(entry.getKey(), entry.getValue()))
				.collect(Collectors.toList());
			ClientOptions clientOptions = new ClientOptions().setApplicationId(APPLICATION_ID).setHeaders(headers);
			OpenAIClientBuilder clientBuilder = new OpenAIClientBuilder().endpoint(connectionProperties.getEndpoint())
				.credential(new AzureKeyCredential(connectionProperties.getApiKey()))
				.clientOptions(clientOptions);
			applyOpenAIClientBuilderCustomizers(clientBuilder, customizers);
			return clientBuilder;
		}

		// Connect to OpenAI (e.g. not the Azure OpenAI). The deploymentName property is
		// used as OpenAI model name.
		if (StringUtils.hasText(connectionProperties.getOpenAiApiKey())) {
			OpenAIClientBuilder clientBuilder = new OpenAIClientBuilder().endpoint("https://api.openai.com/v1")
				.credential(new KeyCredential(connectionProperties.getOpenAiApiKey()))
				.clientOptions(new ClientOptions().setApplicationId(APPLICATION_ID));
			applyOpenAIClientBuilderCustomizers(clientBuilder, customizers);
			return clientBuilder;
		}

		throw new IllegalArgumentException("Either API key or OpenAI API key must not be empty");
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(TokenCredential.class)
	public OpenAIClientBuilder openAIClientWithTokenCredential(AzureOpenAiConnectionProperties connectionProperties,
			TokenCredential tokenCredential, ObjectProvider<AzureOpenAIClientBuilderCustomizer> customizers) {

		Assert.notNull(tokenCredential, "TokenCredential must not be null");
		Assert.hasText(connectionProperties.getEndpoint(), "Endpoint must not be empty");

		OpenAIClientBuilder clientBuilder = new OpenAIClientBuilder().endpoint(connectionProperties.getEndpoint())
			.credential(tokenCredential)
			.clientOptions(new ClientOptions().setApplicationId(APPLICATION_ID));
		applyOpenAIClientBuilderCustomizers(clientBuilder, customizers);
		return clientBuilder;
	}

	private void applyOpenAIClientBuilderCustomizers(OpenAIClientBuilder clientBuilder,
			ObjectProvider<AzureOpenAIClientBuilderCustomizer> customizers) {
		customizers.orderedStream().forEach(customizer -> customizer.customize(clientBuilder));
	}

}
