/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.model.openai.autoconfigure;

import java.util.List;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAutoConfigurationUtil.ResolvedConnectionProperties;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions.Api;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Chat {@link AutoConfiguration Auto-configuration} for OpenAI SDK.
 *
 * @author Christian Tzolov
 * @author Soby Chacko
 * @author Thomas Vitale
 * @author Stefan Vassilev
 * @author Yanming Zhou
 * @author Issam El-atif
 * @author Ilayaperumal Gopinathan
 * @author Sebastien Deleuze
 * @author Dimitar Proynov
 */
@AutoConfiguration
@EnableConfigurationProperties({ OpenAiCommonProperties.class, OpenAiChatProperties.class })
@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = SpringAIModels.OPENAI,
		matchIfMissing = true)
public class OpenAiChatAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public OpenAiChatModel openAiChatModel(OpenAiCommonProperties commonProperties, OpenAiChatProperties chatProperties,
			ToolCallingManager toolCallingManager, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<MeterRegistry> meterRegistry,
			ObjectProvider<ChatModelObservationConvention> observationConvention,
			ObjectProvider<OpenAiHttpClientBuilderCustomizer> httpClientBuilderCustomizers) {

		var resolvedProperties = OpenAiAutoConfigurationUtil.resolveCommonProperties(commonProperties, chatProperties);

		MeterRegistry meterRegistryToUse = resolvedProperties.isConnectionPoolMetricsEnabled()
				? meterRegistry.getIfAvailable() : null;

		List<OpenAiHttpClientBuilderCustomizer> customizers = httpClientBuilderCustomizers.orderedStream().toList();

		OpenAIClient openAIClient = this.openAiClient(resolvedProperties, observationRegistry, meterRegistryToUse,
				customizers);

		OpenAIClientAsync openAIClientAsync = this.openAiClientAsync(resolvedProperties, observationRegistry,
				meterRegistryToUse, customizers);

		var chatModel = OpenAiChatModel.builder()
			.openAiClient(openAIClient)
			.openAiClientAsync(openAIClientAsync)
			.options(resolveOptions(chatProperties, resolvedProperties))
			.toolCallingManager(toolCallingManager)
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.meterRegistry(meterRegistryToUse)
			.build();

		observationConvention.ifAvailable(chatModel::setObservationConvention);

		return chatModel;
	}

	/**
	 * The options the {@code ChatModel} is configured with, pinned to Chat Completions
	 * when the provider serves nothing else.
	 * <p>
	 * Which endpoint a request goes to is otherwise the model's own business, but the
	 * provider is not: it is decided by the connection properties, which never reach
	 * {@link OpenAiChatOptions}, so the one provider that cannot serve the Responses API
	 * has to be settled here.
	 */
	private OpenAiChatOptions resolveOptions(OpenAiChatProperties chatProperties,
			ResolvedConnectionProperties resolvedProperties) {

		OpenAiChatOptions options = chatProperties.toOptions();
		boolean gitHubModels = OpenAiSetup.detectModelProvider(resolvedProperties.isMicrosoftFoundry(),
				resolvedProperties.isGitHubModels(), resolvedProperties.getBaseUrl(),
				resolvedProperties.getMicrosoftDeploymentName(),
				resolvedProperties.getMicrosoftFoundryServiceVersion()) == OpenAiSetup.ModelProvider.GITHUB_MODELS;
		if (!gitHubModels) {
			return options;
		}
		if (options.getApi() == Api.RESPONSES) {
			throw new IllegalStateException("GitHub Models does not support the OpenAI Responses API. " + "Remove "
					+ OpenAiChatProperties.CONFIG_PREFIX + ".api=responses, "
					+ "or point spring.ai.openai.base-url at a provider that serves it.");
		}
		return options.mutate().api(Api.CHAT_COMPLETIONS).build();
	}

	private OpenAIClient openAiClient(ResolvedConnectionProperties commonProperties,
			ObjectProvider<ObservationRegistry> observationRegistry, @Nullable MeterRegistry meterRegistry,
			List<OpenAiHttpClientBuilderCustomizer> httpClientCustomizers) {

		return OpenAiSetup.setupSyncClient(commonProperties.getBaseUrl(), commonProperties.getApiKey(),
				commonProperties.getCredential(), commonProperties.getMicrosoftDeploymentName(),
				commonProperties.getMicrosoftFoundryServiceVersion(), commonProperties.getOrganizationId(),
				commonProperties.isMicrosoftFoundry(), commonProperties.isGitHubModels(), commonProperties.getModel(),
				commonProperties.getTimeout(), commonProperties.getMaxRetries(), commonProperties.getProxy(),
				commonProperties.getCustomHeaders(), observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP),
				meterRegistry, httpClientCustomizers);
	}

	private OpenAIClientAsync openAiClientAsync(ResolvedConnectionProperties commonProperties,
			ObjectProvider<ObservationRegistry> observationRegistry, @Nullable MeterRegistry meterRegistry,
			List<OpenAiHttpClientBuilderCustomizer> httpClientCustomizers) {

		return OpenAiSetup.setupAsyncClient(commonProperties.getBaseUrl(), commonProperties.getApiKey(),
				commonProperties.getCredential(), commonProperties.getMicrosoftDeploymentName(),
				commonProperties.getMicrosoftFoundryServiceVersion(), commonProperties.getOrganizationId(),
				commonProperties.isMicrosoftFoundry(), commonProperties.isGitHubModels(), commonProperties.getModel(),
				commonProperties.getTimeout(), commonProperties.getMaxRetries(), commonProperties.getProxy(),
				commonProperties.getCustomHeaders(), observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP),
				meterRegistry, httpClientCustomizers);
	}

}
