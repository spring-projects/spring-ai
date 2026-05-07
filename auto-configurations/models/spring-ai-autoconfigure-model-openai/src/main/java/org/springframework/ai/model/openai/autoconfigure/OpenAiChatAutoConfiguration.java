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
import com.openai.client.OpenAIClientAsync;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.openai.OpenAiChatModel;
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
			ObjectProvider<ChatModelObservationConvention> observationConvention,
			ObjectProvider<ToolExecutionEligibilityPredicate> openAiToolExecutionEligibilityPredicate) {

		OpenAIClient openAIClient = this.openAiClient(commonProperties);

		OpenAIClientAsync openAIClientAsync = this.openAiClientAsync(commonProperties);

		var chatModel = OpenAiChatModel.builder()
			.openAiClient(openAIClient)
			.openAiClientAsync(openAIClientAsync)
			.options(chatProperties.toOptions())
			.toolCallingManager(toolCallingManager)
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.toolExecutionEligibilityPredicate(
					openAiToolExecutionEligibilityPredicate.getIfUnique(DefaultToolExecutionEligibilityPredicate::new))
			.build();

		observationConvention.ifAvailable(chatModel::setObservationConvention);

		return chatModel;
	}

	private OpenAIClient openAiClient(OpenAiCommonProperties commonProperties) {

		return OpenAiSetup.setupSyncClient(commonProperties.getBaseUrl(), commonProperties.getApiKey(),
				commonProperties.getCredential(), commonProperties.getMicrosoftDeploymentName(),
				commonProperties.getMicrosoftFoundryServiceVersion(), commonProperties.getOrganizationId(),
				commonProperties.isMicrosoftFoundry(), commonProperties.isGitHubModels(), commonProperties.getModel(),
				commonProperties.getTimeout(), commonProperties.getMaxRetries(), commonProperties.getProxy(),
				commonProperties.getCustomHeaders());
	}

	private OpenAIClientAsync openAiClientAsync(OpenAiCommonProperties commonProperties) {

		return OpenAiSetup.setupAsyncClient(commonProperties.getBaseUrl(), commonProperties.getApiKey(),
				commonProperties.getCredential(), commonProperties.getMicrosoftDeploymentName(),
				commonProperties.getMicrosoftFoundryServiceVersion(), commonProperties.getOrganizationId(),
				commonProperties.isMicrosoftFoundry(), commonProperties.isGitHubModels(), commonProperties.getModel(),
				commonProperties.getTimeout(), commonProperties.getMaxRetries(), commonProperties.getProxy(),
				commonProperties.getCustomHeaders());
	}

}
