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
import com.azure.core.util.Header;
import com.azure.core.util.HttpClientOptions;
import com.azure.identity.DefaultAzureCredentialBuilder;

import org.springframework.beans.factory.ObjectProvider;
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
@ConditionalOnClass(OpenAIClientBuilder.class)
@EnableConfigurationProperties(AzureOpenAiConnectionProperties.class)
public class AzureOpenAiClientBuilderConfiguration {

	private static final String APPLICATION_ID = "spring-ai";

	@Bean
	@ConditionalOnMissingBean // ({ OpenAIClient.class, TokenCredential.class })
	public OpenAIClientBuilder openAIClientBuilder(AzureOpenAiConnectionProperties connectionProperties,
			ObjectProvider<AzureOpenAIClientBuilderCustomizer> customizers) {

		final OpenAIClientBuilder clientBuilder;

		HttpClientOptions clientOptions = createHttpClientOptions(connectionProperties);

		// Connect to OpenAI (e.g. not the Azure OpenAI). The deploymentName property is
		// used as OpenAI model name.
		if (StringUtils.hasText(connectionProperties.getOpenAiApiKey())) {
			clientBuilder = new OpenAIClientBuilder().endpoint("https://api.openai.com/v1")
				.credential(new KeyCredential(connectionProperties.getOpenAiApiKey()))
				.clientOptions(clientOptions);
			applyOpenAIClientBuilderCustomizers(clientBuilder, customizers);
			return clientBuilder;
		}
		Assert.hasText(connectionProperties.getEndpoint(), "Endpoint must not be empty");

		if (!StringUtils.hasText(connectionProperties.getApiKey())) {
			// Entra ID configuration, as the API key is not set
			clientBuilder = new OpenAIClientBuilder().endpoint(connectionProperties.getEndpoint())
				.credential(new DefaultAzureCredentialBuilder().build())
				.clientOptions(clientOptions);
		}
		else {
			// Azure OpenAI configuration using API key and endpoint
			clientBuilder = new OpenAIClientBuilder().endpoint(connectionProperties.getEndpoint())
				.credential(new AzureKeyCredential(connectionProperties.getApiKey()))
				.clientOptions(clientOptions);
		}
		applyOpenAIClientBuilderCustomizers(clientBuilder, customizers);
		return clientBuilder;
	}

	private void applyOpenAIClientBuilderCustomizers(OpenAIClientBuilder clientBuilder,
			ObjectProvider<AzureOpenAIClientBuilderCustomizer> customizers) {
		customizers.orderedStream().forEach(customizer -> customizer.customize(clientBuilder));
	}

	/**
	 * Create HttpClientOptions
	 */
	private HttpClientOptions createHttpClientOptions(AzureOpenAiConnectionProperties connectionProperties) {
		// Create HttpClientOptions and apply the configuration
		HttpClientOptions options = new HttpClientOptions();

		options.setApplicationId(APPLICATION_ID);

		Map<String, String> customHeaders = connectionProperties.getCustomHeaders();
		List<Header> headers = customHeaders.entrySet()
			.stream()
			.map(entry -> new Header(entry.getKey(), entry.getValue()))
			.collect(Collectors.toList());

		options.setHeaders(headers);

		if (connectionProperties.getConnectTimeout() != null) {
			options.setConnectTimeout(connectionProperties.getConnectTimeout());
		}

		if (connectionProperties.getReadTimeout() != null) {
			options.setReadTimeout(connectionProperties.getReadTimeout());
		}

		if (connectionProperties.getWriteTimeout() != null) {
			options.setWriteTimeout(connectionProperties.getWriteTimeout());
		}

		if (connectionProperties.getResponseTimeout() != null) {
			options.setResponseTimeout(connectionProperties.getResponseTimeout());
		}

		if (connectionProperties.getMaximumConnectionPoolSize() != null) {
			options.setMaximumConnectionPoolSize(connectionProperties.getMaximumConnectionPoolSize());
		}

		return options;
	}

}
