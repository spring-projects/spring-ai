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

package org.springframework.ai.model.systemone.autoconfigure;

import java.time.Duration;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.decision.observation.DecisionModelObservationConvention;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.systemone.SystemOneDecisionModel;
import org.springframework.ai.systemone.api.SystemOneClient;
import org.springframework.ai.systemone.api.SystemOneRetryUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * {@link AutoConfiguration Auto-configuration} of the System One decision model: a
 * {@link SystemOneClient} from the {@code spring.ai.systemone} connection properties and
 * a {@link SystemOneDecisionModel} over it. Either bean backs off to one the application
 * defines.
 *
 * <p>
 * The {@code spring.ai.systemone.decision.model} property is applied only when it is set.
 * Unset, the decision model uses the default model of the client, so a client the
 * application defines is asked for the model it was built with.
 *
 * <p>
 * The client sends through the {@link RestClient.Builder} of the application when there
 * is one, so that customizers, proxies and SSL bundles apply. A configured read timeout
 * is applied on top of the application's {@link HttpClientSettings}; unset, the
 * application's transport is used as is. Without a builder, the client builds its own
 * transport.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
@AutoConfiguration
@ConditionalOnClass({ SystemOneDecisionModel.class, RestClient.class, ClientHttpRequestFactoryBuilder.class })
@ConditionalOnProperty(name = SpringAIModelProperties.DECISION_MODEL, havingValue = SpringAIModels.SYSTEMONE,
		matchIfMissing = true)
@EnableConfigurationProperties({ SystemOneConnectionProperties.class, SystemOneDecisionProperties.class })
public class SystemOneDecisionAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public SystemOneClient systemOneClient(SystemOneConnectionProperties connectionProperties,
			SystemOneDecisionProperties decisionProperties, ObjectProvider<RestClient.Builder> restClientBuilder,
			ObjectProvider<ClientHttpRequestFactoryBuilder<?>> requestFactoryBuilder,
			ObjectProvider<HttpClientSettings> httpClientSettings) {
		String apiKey = connectionProperties.getApiKey();
		Assert.state(StringUtils.hasText(apiKey), () -> "No System One API key configured. Set "
				+ SystemOneConnectionProperties.CONFIG_PREFIX + ".api-key.");

		SystemOneClient.Builder clientBuilder = SystemOneClient.builder()
			.apiKey(apiKey)
			.baseUrl(connectionProperties.getBaseUrl())
			.retryTemplate(SystemOneRetryUtils.retryTemplate(connectionProperties.getRetry().toRetryPolicy()));
		String model = decisionProperties.getModel();
		if (StringUtils.hasText(model)) {
			// Unset, the client keeps its own default model.
			clientBuilder.defaultModel(model);
		}

		Duration timeout = connectionProperties.getTimeout();
		RestClient.Builder transport = restClientBuilder.getIfAvailable();
		if (transport == null) {
			// No builder to share: the client builds its own transport.
			if (timeout != null) {
				clientBuilder.timeout(timeout);
			}
			return clientBuilder.build();
		}
		transport = transport.clone();
		if (timeout != null) {
			// Asked for explicitly: rebuild the request factory the way Boot does, with
			// the read timeout on top, so proxies, SSL bundles and the other HTTP client
			// settings stay in force. Unset, the application's transport is used as is.
			ClientHttpRequestFactoryBuilder<?> factoryBuilder = requestFactoryBuilder
				.getIfAvailable(() -> ClientHttpRequestFactoryBuilder.detect());
			HttpClientSettings settings = httpClientSettings.getIfAvailable(HttpClientSettings::defaults)
				.withReadTimeout(timeout);
			transport.requestFactory(factoryBuilder.build(settings));
		}
		return clientBuilder.restClientBuilder(transport).build();
	}

	@Bean
	@ConditionalOnMissingBean
	public SystemOneDecisionModel systemOneDecisionModel(SystemOneClient systemOneClient,
			SystemOneDecisionProperties decisionProperties, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<DecisionModelObservationConvention> observationConvention) {
		SystemOneDecisionModel.Builder builder = SystemOneDecisionModel.builder(systemOneClient)
			.options(decisionProperties.toOptions())
			.provider(decisionProperties.getProvider())
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP));
		observationConvention.ifAvailable(builder::observationConvention);
		return builder.build();
	}

}
