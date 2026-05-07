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

import com.openai.client.OpenAIClient;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.image.observation.ImageModelObservationConvention;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Image {@link AutoConfiguration Auto-configuration} for OpenAI.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Stefan Vassilev
 * @author Yanming Zhou
 * @author lambochen
 * @author Issam El-atif
 * @author Ilayaperumal Gopinathan
 * @author Sebastien Deleuze
 */
@AutoConfiguration
@ConditionalOnProperty(name = SpringAIModelProperties.IMAGE_MODEL, havingValue = SpringAIModels.OPENAI,
		matchIfMissing = true)
@EnableConfigurationProperties({ OpenAiCommonProperties.class, OpenAiImageProperties.class })
public class OpenAiImageAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public OpenAiImageModel openAiImageModel(OpenAiCommonProperties commonProperties,
			OpenAiImageProperties imageProperties, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ImageModelObservationConvention> observationConvention) {

		var imageModel = new OpenAiImageModel(openAiClient(commonProperties), imageProperties.getOptions(),
				observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP));

		observationConvention.ifAvailable(imageModel::setObservationConvention);

		return imageModel;
	}

	private OpenAIClient openAiClient(OpenAiCommonProperties commonProperties) {

		return OpenAiSetup.setupSyncClient(commonProperties.getBaseUrl(), commonProperties.getApiKey(),
				commonProperties.getCredential(), commonProperties.getMicrosoftDeploymentName(),
				commonProperties.getMicrosoftFoundryServiceVersion(), commonProperties.getOrganizationId(),
				commonProperties.isMicrosoftFoundry(), commonProperties.isGitHubModels(), commonProperties.getModel(),
				commonProperties.getTimeout(), commonProperties.getMaxRetries(), commonProperties.getProxy(),
				commonProperties.getCustomHeaders());
	}

}
