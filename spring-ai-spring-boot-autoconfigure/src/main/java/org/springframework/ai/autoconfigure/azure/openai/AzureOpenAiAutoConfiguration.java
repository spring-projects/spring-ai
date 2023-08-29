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
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
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
	public AzureOpenAiClient azureOpenAiClient(OpenAIClient msoftSdkOpenAiClient) {
		AzureOpenAiClient azureOpenAiClient = new AzureOpenAiClient(msoftSdkOpenAiClient);
		azureOpenAiClient.setTemperature(this.azureOpenAiProperties.getTemperature());
		azureOpenAiClient.setModel(this.azureOpenAiProperties.getModel());
		return azureOpenAiClient;
	}

	@Bean
	public AzureOpenAiEmbeddingClient azureOpenAiEmbeddingClient(OpenAIClient msoftSdkOpenAiClient) {
		return new AzureOpenAiEmbeddingClient(msoftSdkOpenAiClient, this.azureOpenAiProperties.getEmbeddingModel());
	}

}
