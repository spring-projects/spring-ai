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

package org.springframework.ai.model.ollama.autoconfigure;

import io.micrometer.observation.ObservationRegistry;
import org.springaicommunity.typesafe.TypeSafeClient;

import org.springframework.ai.decision.observation.DecisionModelObservationConvention;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.ollama.OllamaDecisionModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * {@link AutoConfiguration Auto-configuration} for Ollama Decision Model.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
@AutoConfiguration
@ConditionalOnClass({ OllamaDecisionModel.class, TypeSafeClient.class })
@ConditionalOnProperty(name = SpringAIModelProperties.DECISION_MODEL, havingValue = SpringAIModels.OLLAMA,
		matchIfMissing = true)
@EnableConfigurationProperties({ OllamaDecisionProperties.class, OllamaInitializationProperties.class })
public class OllamaDecisionAutoConfiguration {

	/**
	 * Ollama ignores the API key, but the SystemOne SDK requires one.
	 */
	private static final String PLACEHOLDER_API_KEY = "ollama";

	@Bean
	@ConditionalOnMissingBean
	public OllamaDecisionModel ollamaDecisionModel(OllamaApi ollamaApi, OllamaConnectionDetails connectionDetails,
			OllamaDecisionProperties properties, OllamaInitializationProperties initProperties,
			ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<DecisionModelObservationConvention> observationConvention) {
		var decisionModelPullStrategy = initProperties.getDecision().isInclude() ? initProperties.getPullModelStrategy()
				: PullModelStrategy.NEVER;

		// The System One client talks to the same Ollama server as the Ollama API.
		var systemOneClient = TypeSafeClient.builder()
			.baseUrl(connectionDetails.getBaseUrl())
			.apiKey(PLACEHOLDER_API_KEY)
			.restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
			.build();

		var decisionModelBuilder = OllamaDecisionModel.builder()
			.ollamaApi(ollamaApi)
			.systemOneClient(systemOneClient)
			.decisionOptions(properties.toOptions())
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.modelManagementOptions(new ModelManagementOptions(decisionModelPullStrategy,
					initProperties.getDecision().getAdditionalModels(), initProperties.getTimeout(),
					initProperties.getMaxRetries()));

		observationConvention.ifAvailable(decisionModelBuilder::observationConvention);

		return decisionModelBuilder.build();
	}

}
