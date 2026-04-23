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

import org.springframework.ai.openai.AbstractOpenAiOptions;
import org.springframework.ai.openai.batch.BatchExecutionRepository;
import org.springframework.ai.openai.batch.BatchRequestHandler;
import org.springframework.ai.openai.batch.InMemoryBatchExecutionRepository;
import org.springframework.ai.openai.batch.OpenAiBatchApi;
import org.springframework.ai.openai.batch.OpenAiBatchListener;
import org.springframework.ai.openai.batch.OpenAiBatchModel;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Batch API {@link AutoConfiguration Auto-configuration} for OpenAI SDK.
 * <p>
 * Enabled only when {@code spring.ai.openai.batch.enabled=true} is set. Wires together
 * the {@link OpenAiBatchApi}, {@link OpenAiBatchModel}, registered
 * {@link BatchRequestHandler}s, and {@link OpenAiBatchListener}s.
 *
 * @author Yasin Akbas
 * @since 2.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties({ OpenAiConnectionProperties.class, OpenAiBatchProperties.class })
@ConditionalOnProperty(name = "spring.ai.openai.batch.enabled", havingValue = "true")
public class OpenAiBatchAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public OpenAiBatchApi openAiBatchApi(OpenAiConnectionProperties commonProperties,
			OpenAiBatchProperties batchProperties) {
		OpenAiAutoConfigurationUtil.ResolvedConnectionProperties resolved = OpenAiAutoConfigurationUtil
			.resolveConnectionProperties(commonProperties, batchProperties);

		OpenAIClient openAIClient = this.openAiClient(resolved);

		return new OpenAiBatchApi(openAIClient, new com.fasterxml.jackson.databind.ObjectMapper());
	}

	@Bean
	@ConditionalOnMissingBean
	public BatchExecutionRepository batchExecutionRepository() {
		return new InMemoryBatchExecutionRepository();
	}

	@Bean
	@ConditionalOnMissingBean
	public OpenAiBatchModel openAiBatchModel(OpenAiBatchProperties batchProperties, OpenAiBatchApi batchApi,
			BatchExecutionRepository executionRepository, ObjectProvider<List<BatchRequestHandler<?>>> handlers,
			ObjectProvider<List<OpenAiBatchListener>> listeners) {
		return OpenAiBatchModel.builder()
			.batchApi(batchApi)
			.options(batchProperties.getOptions())
			.executionRepository(executionRepository)
			.handlers(handlers.getIfAvailable(List::of))
			.listeners(listeners.getIfAvailable(List::of))
			.build();
	}

	private OpenAIClient openAiClient(AbstractOpenAiOptions resolved) {
		return OpenAiSetup.setupSyncClient(resolved.getBaseUrl(), resolved.getApiKey(), resolved.getCredential(),
				resolved.getMicrosoftDeploymentName(), resolved.getMicrosoftFoundryServiceVersion(),
				resolved.getOrganizationId(), resolved.isMicrosoftFoundry(), resolved.isGitHubModels(),
				resolved.getModel(), resolved.getTimeout(), resolved.getMaxRetries(), resolved.getProxy(),
				resolved.getCustomHeaders());
	}

}
