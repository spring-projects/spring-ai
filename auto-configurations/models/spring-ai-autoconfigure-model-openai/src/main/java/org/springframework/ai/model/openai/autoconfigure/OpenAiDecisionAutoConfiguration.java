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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.decision.observation.DecisionModelObservationConvention;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAutoConfigurationUtil.ResolvedConnectionProperties;
import org.springframework.ai.openai.OpenAiDecisionModel;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Decision {@link AutoConfiguration Auto-configuration} for OpenAI SDK.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
@AutoConfiguration
@EnableConfigurationProperties({ OpenAiCommonProperties.class, OpenAiDecisionProperties.class })
@Import(OpenAiConnectionDetailsConfiguration.class)
@ConditionalOnProperty(name = SpringAIModelProperties.DECISION_MODEL, havingValue = SpringAIModels.OPENAI,
		matchIfMissing = true)
public class OpenAiDecisionAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public OpenAiDecisionModel openAiDecisionModel(OpenAiConnectionDetails connectionDetails,
			OpenAiCommonProperties commonProperties, OpenAiDecisionProperties decisionProperties,
			ObjectProvider<ObservationRegistry> observationRegistry, ObjectProvider<MeterRegistry> meterRegistry,
			ObjectProvider<DecisionModelObservationConvention> observationConvention,
			ObjectProvider<OpenAiHttpClientBuilderCustomizer> httpClientBuilderCustomizers) {

		var resolvedProperties = OpenAiAutoConfigurationUtil.resolveCommonProperties(connectionDetails,
				commonProperties, decisionProperties);

		List<OpenAiHttpClientBuilderCustomizer> customizers = httpClientBuilderCustomizers.orderedStream().toList();

		OpenAIClient openAiClient = this.openAiClient(resolvedProperties, observationRegistry, meterRegistry,
				customizers);

		return OpenAiDecisionModel.builder()
			.openAiClient(openAiClient)
			.options(decisionProperties.toOptions())
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.observationConvention(observationConvention.getIfAvailable())
			.build();
	}

	private OpenAIClient openAiClient(ResolvedConnectionProperties commonProperties,
			ObjectProvider<ObservationRegistry> observationRegistry, ObjectProvider<MeterRegistry> meterRegistry,
			List<OpenAiHttpClientBuilderCustomizer> httpClientCustomizers) {

		MeterRegistry meterRegistryToUse = commonProperties.isConnectionPoolMetricsEnabled()
				? meterRegistry.getIfAvailable() : null;

		return OpenAiSetup.setupSyncClient(commonProperties.getBaseUrl(), commonProperties.getApiKey(),
				commonProperties.getCredential(), commonProperties.getMicrosoftDeploymentName(),
				commonProperties.getMicrosoftFoundryServiceVersion(), commonProperties.getOrganizationId(),
				commonProperties.isMicrosoftFoundry(), commonProperties.isGitHubModels(), commonProperties.getModel(),
				commonProperties.getTimeout(), commonProperties.getMaxRetries(), commonProperties.getProxy(),
				commonProperties.getCustomHeaders(), observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP),
				meterRegistryToUse, httpClientCustomizers);
	}

}
