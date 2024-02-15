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

import com.azure.core.util.ClientOptions;
import org.springframework.ai.azure.openai.AzureOpenAiChatClient;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.Assert;

@AutoConfiguration
@ConditionalOnClass(OpenAIClientBuilder.class)
@EnableConfigurationProperties({ AzureOpenAiChatProperties.class, AzureOpenAiEmbeddingProperties.class,
		AzureOpenAiConnectionProperties.class })
public class AzureOpenAiAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public OpenAIClient openAIClient(AzureOpenAiConnectionProperties connectionProperties) {

		Assert.hasText(connectionProperties.getApiKey(), "API key must not be empty");
		Assert.hasText(connectionProperties.getEndpoint(), "Endpoint must not be empty");

		return new OpenAIClientBuilder().endpoint(connectionProperties.getEndpoint())
			.credential(new AzureKeyCredential(connectionProperties.getApiKey()))
			.clientOptions(new ClientOptions().setApplicationId("spring-ai"))
			.buildClient();
	}

	@Bean
	public AzureOpenAiChatClient azureOpenAiChatClient(OpenAIClient openAIClient,
			AzureOpenAiChatProperties chatProperties) {

		AzureOpenAiChatClient azureOpenAiChatClient = new AzureOpenAiChatClient(openAIClient,
				chatProperties.getOptions());

		return azureOpenAiChatClient;
	}

	@Bean
	public AzureOpenAiEmbeddingClient azureOpenAiEmbeddingClient(OpenAIClient openAIClient,
			AzureOpenAiEmbeddingProperties embeddingProperties) {
		return new AzureOpenAiEmbeddingClient(openAIClient, embeddingProperties.getMetadataMode(),
				embeddingProperties.getOptions());
	}

}
