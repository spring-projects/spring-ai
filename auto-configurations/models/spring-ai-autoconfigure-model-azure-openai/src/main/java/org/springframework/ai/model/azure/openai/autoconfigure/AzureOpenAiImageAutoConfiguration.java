/*
 * Copyright 2023-2026 the original author or authors.
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

import com.azure.ai.openai.OpenAIClientBuilder;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.azure.openai.AzureOpenAiImageModel;
import org.springframework.ai.image.observation.ImageModelObservationConvention;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.retry.RetryTemplate;

/**
 * {@link AutoConfiguration Auto-configuration} for Azure OpenAI.
 *
 * @author Piotr Olaszewski
 * @author Soby Chacko
 * @author Manuel Andreo Garcia
 * @author Ilayaperumal Gopinathan
 * @author Yanming Zhou
 */
@AutoConfiguration
@ConditionalOnClass(AzureOpenAiImageModel.class)
@ConditionalOnProperty(name = SpringAIModelProperties.IMAGE_MODEL, havingValue = SpringAIModels.AZURE_OPENAI,
		matchIfMissing = true)
@EnableConfigurationProperties(AzureOpenAiImageOptionsProperties.class)
@Import(AzureOpenAiClientBuilderConfiguration.class)
public class AzureOpenAiImageAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public AzureOpenAiImageModel azureOpenAiImageModel(OpenAIClientBuilder openAIClientBuilder,
			AzureOpenAiImageOptionsProperties imageProperties, ObjectProvider<RetryTemplate> retryTemplate,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ImageModelObservationConvention> observationConvention) {

		var imageModel = new AzureOpenAiImageModel(openAIClientBuilder.buildClient(), imageProperties.getOptions(),
				retryTemplate.getIfUnique(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE),
				observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP));
		observationConvention.ifAvailable(imageModel::setObservationConvention);
		return imageModel;
	}

}
