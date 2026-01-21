/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.model.openaisdk.autoconfigure;

import com.openai.client.OpenAIClient;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.openaisdk.OpenAiSdkEmbeddingModel;
import org.springframework.ai.openaisdk.setup.OpenAiSdkSetup;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Embedding {@link AutoConfiguration Auto-configuration} for OpenAI SDK.
 *
 * @author Christian Tzolov
 */
@AutoConfiguration
@ConditionalOnProperty(name = SpringAIModelProperties.EMBEDDING_MODEL, havingValue = SpringAIModels.OPENAI_SDK,
		matchIfMissing = true)
@EnableConfigurationProperties({ OpenAiSdkConnectionProperties.class, OpenAiSdkEmbeddingProperties.class })
public class OpenAiSdkEmbeddingAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public OpenAiSdkEmbeddingModel openAiEmbeddingModel(OpenAiSdkConnectionProperties commonProperties,
			OpenAiSdkEmbeddingProperties embeddingProperties, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<EmbeddingModelObservationConvention> observationConvention) {

		var embeddingModel = new OpenAiSdkEmbeddingModel(this.openAiClient(commonProperties, embeddingProperties),
				embeddingProperties.getMetadataMode(), embeddingProperties.getOptions(),
				observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP));

		observationConvention.ifAvailable(embeddingModel::setObservationConvention);

		return embeddingModel;
	}

	private OpenAIClient openAiClient(OpenAiSdkConnectionProperties commonProperties,
			OpenAiSdkEmbeddingProperties embeddingProperties) {

		OpenAiSdkAutoConfigurationUtil.ResolvedConnectionProperties resolved = OpenAiSdkAutoConfigurationUtil
			.resolveConnectionProperties(commonProperties, embeddingProperties);

		return OpenAiSdkSetup.setupSyncClient(resolved.getBaseUrl(), resolved.getApiKey(), resolved.getCredential(),
				resolved.getMicrosoftDeploymentName(), resolved.getMicrosoftFoundryServiceVersion(),
				resolved.getOrganizationId(), resolved.isMicrosoftFoundry(), resolved.isGitHubModels(),
				resolved.getModel(), resolved.getTimeout(), resolved.getMaxRetries(), resolved.getProxy(),
				resolved.getCustomHeaders());
	}

}
