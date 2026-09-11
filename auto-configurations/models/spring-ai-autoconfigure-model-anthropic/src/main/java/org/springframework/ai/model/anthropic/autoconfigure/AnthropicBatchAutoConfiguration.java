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

package org.springframework.ai.model.anthropic.autoconfigure;

import java.util.List;

import com.anthropic.client.AnthropicClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.anthropic.AnthropicBatchModel;
import org.springframework.ai.anthropic.AnthropicBatchObservationConvention;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.DefaultAnthropicBatchModel;
import org.springframework.ai.anthropic.http.okhttp.AnthropicHttpClientBuilderCustomizer;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * {@link AutoConfiguration Auto-configuration} for the Anthropic Message Batches model.
 *
 * <p>
 * Opt-in: the bean is only created when {@code spring.ai.anthropic.batch.enabled=true},
 * so applications that do not submit batches do not pay for a second HTTP client.
 * Connection settings ({@code spring.ai.anthropic.*}) and model defaults
 * ({@code spring.ai.anthropic.chat.*}) are shared with
 * {@link AnthropicChatAutoConfiguration}, so a batch reuses the same credentials, base
 * URL, timeout, retries, proxy, custom headers and HTTP client customizers as realtime
 * calls.
 *
 * @author Ricken Bazolo
 * @since 2.0.0
 */
@AutoConfiguration(after = AnthropicChatAutoConfiguration.class)
@EnableConfigurationProperties({ AnthropicConnectionProperties.class, AnthropicChatProperties.class,
		AnthropicBatchProperties.class })
@ConditionalOnClass(AnthropicClient.class)
@ConditionalOnProperty(prefix = AnthropicBatchProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true")
public class AnthropicBatchAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public AnthropicBatchModel anthropicBatchModel(AnthropicConnectionProperties connectionProperties,
			AnthropicChatProperties chatProperties, AnthropicBatchProperties batchProperties,
			ToolCallingManager toolCallingManager, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<MeterRegistry> meterRegistry,
			ObjectProvider<AnthropicBatchObservationConvention> observationConvention,
			ObjectProvider<AnthropicHttpClientBuilderCustomizer> httpClientBuilderCustomizers) {

		AnthropicChatOptions.Builder builder = chatProperties.toOptions().mutate();
		if (connectionProperties.getApiKey() != null) {
			builder.apiKey(connectionProperties.getApiKey());
		}
		if (connectionProperties.getBaseUrl() != null) {
			builder.baseUrl(connectionProperties.getBaseUrl());
		}
		if (connectionProperties.getTimeout() != null) {
			builder.timeout(connectionProperties.getTimeout());
		}
		if (connectionProperties.getMaxRetries() != null) {
			builder.maxRetries(connectionProperties.getMaxRetries());
		}
		if (connectionProperties.getProxy() != null) {
			builder.proxy(connectionProperties.getProxy());
		}
		if (!connectionProperties.getCustomHeaders().isEmpty()) {
			builder.customHeaders(connectionProperties.getCustomHeaders());
		}
		// Batch-specific overrides of the shared chat defaults.
		if (batchProperties.getModel() != null) {
			builder.model(batchProperties.getModel());
		}
		if (batchProperties.getMaxTokens() != null) {
			builder.maxTokens(batchProperties.getMaxTokens());
		}
		AnthropicChatOptions options = builder.build();

		List<AnthropicHttpClientBuilderCustomizer> customizers = httpClientBuilderCustomizers.orderedStream().toList();

		DefaultAnthropicBatchModel batchModel = DefaultAnthropicBatchModel.builder()
			.options(options)
			.toolCallingManager(toolCallingManager)
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.meterRegistry(chatProperties.isConnectionPoolMetricsEnabled() ? meterRegistry.getIfAvailable() : null)
			.httpClientBuilderCustomizers(customizers)
			.build();

		observationConvention.ifAvailable(batchModel::setObservationConvention);

		return batchModel;
	}

}
