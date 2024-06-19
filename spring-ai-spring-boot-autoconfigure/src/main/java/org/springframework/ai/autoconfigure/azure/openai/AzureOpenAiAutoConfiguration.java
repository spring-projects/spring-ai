/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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
import com.azure.core.credential.KeyCredential;
import com.azure.core.credential.TokenCredential;
import com.azure.core.util.ClientOptions;
import org.springframework.ai.azure.openai.AzureOpenAiAudioTranscriptionModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingModel;
import org.springframework.ai.azure.openai.AzureOpenAiImageModel;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * @author Piotr Olaszewski
 */
@AutoConfiguration
@ConditionalOnClass({OpenAIClientBuilder.class, AzureOpenAiChatModel.class})
@EnableConfigurationProperties({AzureOpenAiChatProperties.class, AzureOpenAiEmbeddingProperties.class,
		AzureOpenAiConnectionProperties.class, AzureOpenAiImageOptionsProperties.class,
		AzureOpenAiAudioTranscriptionProperties.class })
public class AzureOpenAiAutoConfiguration {

	private final static String APPLICATION_ID = "spring-ai";

	@Bean
	@ConditionalOnMissingBean({ OpenAIClient.class, TokenCredential.class })
	public OpenAIClient openAIClient(AzureOpenAiConnectionProperties connectionProperties) {

		if (StringUtils.hasText(connectionProperties.getApiKey())) {

			Assert.hasText(connectionProperties.getEndpoint(), "Endpoint must not be empty");

			return new OpenAIClientBuilder().endpoint(connectionProperties.getEndpoint())
				.credential(new AzureKeyCredential(connectionProperties.getApiKey()))
				.clientOptions(new ClientOptions().setApplicationId(APPLICATION_ID))
				.buildClient();
		}

		// Connect to OpenAI (e.g. not the Azure OpenAI). The deploymentName property is
		// used as OpenAI model name.
		if (StringUtils.hasText(connectionProperties.getOpenAiApiKey())) {
			return new OpenAIClientBuilder().endpoint("https://api.openai.com/v1")
				.credential(new KeyCredential(connectionProperties.getOpenAiApiKey()))
				.clientOptions(new ClientOptions().setApplicationId(APPLICATION_ID))
				.buildClient();
		}

		throw new IllegalArgumentException("Either API key or OpenAI API key must not be empty");
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(TokenCredential.class)
	public OpenAIClient openAIClientWithTokenCredential(AzureOpenAiConnectionProperties connectionProperties,
			TokenCredential tokenCredential) {

		Assert.notNull(tokenCredential, "TokenCredential must not be null");
		Assert.hasText(connectionProperties.getEndpoint(), "Endpoint must not be empty");

		return new OpenAIClientBuilder().endpoint(connectionProperties.getEndpoint())
			.credential(tokenCredential)
			.clientOptions(new ClientOptions().setApplicationId(APPLICATION_ID))
				.buildClient();
	}

	@Bean
	@ConditionalOnProperty(prefix = AzureOpenAiChatProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public AzureOpenAiChatModel azureOpenAiChatModel(OpenAIClient openAIClient,
													 AzureOpenAiChatProperties chatProperties, List<FunctionCallback> toolFunctionCallbacks,
													 FunctionCallbackContext functionCallbackContext) {

		if (!CollectionUtils.isEmpty(toolFunctionCallbacks)) {
			chatProperties.getOptions().getFunctionCallbacks().addAll(toolFunctionCallbacks);
		}

		return new AzureOpenAiChatModel(openAIClient, chatProperties.getOptions(), functionCallbackContext);
	}

	@Bean
	@ConditionalOnProperty(prefix = AzureOpenAiEmbeddingProperties.CONFIG_PREFIX, name = "enabled",
			havingValue = "true", matchIfMissing = true)
	public AzureOpenAiEmbeddingModel azureOpenAiEmbeddingModel(OpenAIClient openAIClient,
															   AzureOpenAiEmbeddingProperties embeddingProperties) {
		return new AzureOpenAiEmbeddingModel(openAIClient, embeddingProperties.getMetadataMode(),
				embeddingProperties.getOptions());
	}

	@Bean
	@ConditionalOnMissingBean
	public FunctionCallbackContext springAiFunctionManager(ApplicationContext context) {
		FunctionCallbackContext manager = new FunctionCallbackContext();
		manager.setApplicationContext(context);
		return manager;
	}

	@Bean
	@ConditionalOnProperty(prefix = AzureOpenAiImageOptionsProperties.CONFIG_PREFIX, name = "enabled",
			havingValue = "true", matchIfMissing = true)
	public AzureOpenAiImageModel azureOpenAiImageClient(OpenAIClient openAIClient,
														AzureOpenAiImageOptionsProperties imageProperties) {

		return new AzureOpenAiImageModel(openAIClient, imageProperties.getOptions());
	}

	@Bean
	@ConditionalOnProperty(prefix = AzureOpenAiAudioTranscriptionProperties.CONFIG_PREFIX, name = "enabled",
			havingValue = "true", matchIfMissing = true)
	public AzureOpenAiAudioTranscriptionModel azureOpenAiAudioTranscriptionModel(OpenAIClient openAIClient,
																				 AzureOpenAiAudioTranscriptionProperties audioProperties) {
		return new AzureOpenAiAudioTranscriptionModel(openAIClient, audioProperties.getOptions());
	}

}
