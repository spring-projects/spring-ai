/*
 * Copyright 2023 the original author or authors.
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

package org.springframework.ai.autoconfigure.azure.openai;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;

import org.springframework.ai.azure.openai.client.AzureOpenAiClient;
import org.springframework.ai.azure.openai.embedding.AzureOpenAiEmbeddingClient;
import org.springframework.ai.client.AiClient;
import org.springframework.ai.client.RetryAiClient;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.embedding.RetryEmbeddingClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.StringUtils;

@AutoConfiguration
@ConditionalOnClass(OpenAIClientBuilder.class)
@EnableConfigurationProperties(AzureOpenAiProperties.class)
public class AzureOpenAiAutoConfiguration {

	private final AzureOpenAiProperties azureOpenAiProperties;

	public AzureOpenAiAutoConfiguration(AzureOpenAiProperties azureOpenAiProperties) {
		this.azureOpenAiProperties = azureOpenAiProperties;
	}

	@Bean
	@ConditionalOnMissingBean
	public OpenAIClient msoftSdkOpenAiClient(AzureOpenAiProperties azureOpenAiProperties) {
		if (!StringUtils.hasText(azureOpenAiProperties.getApiKey())) {
			throw new IllegalArgumentException("You must provide an API key with the property name "
					+ AzureOpenAiProperties.CONFIG_PREFIX + ".api-key");
		}
		OpenAIClient msoftSdkOpenAiClient = new OpenAIClientBuilder().endpoint(this.azureOpenAiProperties.getEndpoint())
			.credential(new AzureKeyCredential(this.azureOpenAiProperties.getApiKey()))
			.buildClient();
		return msoftSdkOpenAiClient;
	}

	@Bean
	@ConditionalOnMissingBean
	public AiClient azureOpenAiClient(OpenAIClient msoftSdkOpenAiClient, AzureOpenAiProperties azureOpenAiProperties,
			RetryTemplate retryTemplate) {
		AzureOpenAiClient azureOpenAiClient = new AzureOpenAiClient(msoftSdkOpenAiClient);
		azureOpenAiClient.setTemperature(this.azureOpenAiProperties.getTemperature());
		azureOpenAiClient.setModel(this.azureOpenAiProperties.getModel());

		return (azureOpenAiProperties.getRetry().isEnabled()) ? new RetryAiClient(retryTemplate, azureOpenAiClient)
				: azureOpenAiClient;
	}

	@Bean
	@ConditionalOnMissingBean
	public EmbeddingClient azureOpenAiEmbeddingClient(OpenAIClient msoftSdkOpenAiClient,
			AzureOpenAiProperties azureOpenAiProperties, RetryTemplate retryTemplate) {
		var embeddingClient = new AzureOpenAiEmbeddingClient(msoftSdkOpenAiClient,
				this.azureOpenAiProperties.getEmbeddingModel());
		return (azureOpenAiProperties.getRetry().isEnabled()) ? new RetryEmbeddingClient(retryTemplate, embeddingClient)
				: embeddingClient;
	}

	@Bean
	@ConditionalOnMissingBean
	public RetryTemplate retryTemplate(AzureOpenAiProperties openAiProperties) {
		var retry = openAiProperties.getRetry();
		// currentInterval = Math.min(initialInterval * Math.pow(multiplier, retryNum),
		// maxInterval)}
		return RetryTemplate.builder()
			.maxAttempts(retry.getMaxAttempts())
			.exponentialBackoff(retry.getInitialInterval(), retry.getBackoffIntervalMultiplier(),
					retry.getMaximumBackoffDuration())
			.build();
	}

}
