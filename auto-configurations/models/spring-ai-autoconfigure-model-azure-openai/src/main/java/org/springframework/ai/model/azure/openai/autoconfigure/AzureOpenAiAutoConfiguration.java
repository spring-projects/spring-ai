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
import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.azure.openai.AzureOpenAiAudioTranscriptionModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingModel;
import org.springframework.ai.azure.openai.AzureOpenAiImageModel;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.model.function.DefaultFunctionCallbackResolver;
import org.springframework.ai.model.function.FunctionCallbackResolver;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * {@link AutoConfiguration Auto-configuration} for Azure OpenAI.
 *
 * @author Piotr Olaszewski
 * @author Soby Chacko
 * @author Manuel Andreo Garcia
 * @author Ilayaperumal Gopinathan
 */
@AutoConfiguration(after = { ToolCallingAutoConfiguration.class })
@ConditionalOnClass({ OpenAIClientBuilder.class, AzureOpenAiChatModel.class })
@EnableConfigurationProperties({ AzureOpenAiChatProperties.class, AzureOpenAiEmbeddingProperties.class,
		AzureOpenAiConnectionProperties.class, AzureOpenAiImageOptionsProperties.class,
		AzureOpenAiAudioTranscriptionProperties.class })
@ImportAutoConfiguration(classes = { ToolCallingAutoConfiguration.class })
public class AzureOpenAiAutoConfiguration {

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

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = SpringAIModels.AZURE_OPENAI,
			matchIfMissing = true)
	public AzureOpenAiChatModel azureOpenAiChatModel(OpenAIClientBuilder openAIClientBuilder,
			AzureOpenAiChatProperties chatProperties, ToolCallingManager toolCallingManager,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ChatModelObservationConvention> observationConvention) {

		var chatModel = AzureOpenAiChatModel.builder()
			.openAIClientBuilder(openAIClientBuilder)
			.defaultOptions(chatProperties.getOptions())
			.toolCallingManager(toolCallingManager)
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.build();
		observationConvention.ifAvailable(chatModel::setObservationConvention);

		return chatModel;
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(name = SpringAIModelProperties.EMBEDDING_MODEL, havingValue = SpringAIModels.AZURE_OPENAI,
			matchIfMissing = true)
	public AzureOpenAiEmbeddingModel azureOpenAiEmbeddingModel(OpenAIClientBuilder openAIClient,
			AzureOpenAiEmbeddingProperties embeddingProperties, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<EmbeddingModelObservationConvention> observationConvention) {

		var embeddingModel = new AzureOpenAiEmbeddingModel(openAIClient.buildClient(),
				embeddingProperties.getMetadataMode(), embeddingProperties.getOptions(),
				observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP));

		observationConvention.ifAvailable(embeddingModel::setObservationConvention);

		return embeddingModel;

	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(name = SpringAIModelProperties.IMAGE_MODEL, havingValue = SpringAIModels.AZURE_OPENAI,
			matchIfMissing = true)
	public AzureOpenAiImageModel azureOpenAiImageModel(OpenAIClientBuilder openAIClientBuilder,
			AzureOpenAiImageOptionsProperties imageProperties) {

		return new AzureOpenAiImageModel(openAIClientBuilder.buildClient(), imageProperties.getOptions());
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(name = SpringAIModelProperties.AUDIO_TRANSCRIPTION_MODEL,
			havingValue = SpringAIModels.AZURE_OPENAI, matchIfMissing = true)
	public AzureOpenAiAudioTranscriptionModel azureOpenAiAudioTranscriptionModel(OpenAIClientBuilder openAIClient,
			AzureOpenAiAudioTranscriptionProperties audioProperties) {
		return new AzureOpenAiAudioTranscriptionModel(openAIClient.buildClient(), audioProperties.getOptions());
	}

	private void applyOpenAIClientBuilderCustomizers(OpenAIClientBuilder clientBuilder,
			ObjectProvider<AzureOpenAIClientBuilderCustomizer> customizers) {
		customizers.orderedStream().forEach(customizer -> customizer.customize(clientBuilder));
	}

	@Bean
	@ConditionalOnMissingBean
	public FunctionCallbackResolver springAiFunctionManager(ApplicationContext context) {
		DefaultFunctionCallbackResolver manager = new DefaultFunctionCallbackResolver();
		manager.setApplicationContext(context);
		return manager;
	}

}
