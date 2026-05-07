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

import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.openai.OpenAiModerationModel;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Moderation {@link AutoConfiguration Auto-configuration} for OpenAI SDK.
 *
 * @author Thomas Vitale
 * @author Stefan Vassilev
 * @author Christian Tzolov
 * @author Yanming Zhou
 * @author Issam El-atif
 * @author Ilayaperumal Gopinathan
 */
@AutoConfiguration
@EnableConfigurationProperties({ OpenAiConnectionProperties.class, OpenAiModerationProperties.class })
@ConditionalOnProperty(name = SpringAIModelProperties.MODERATION_MODEL, havingValue = SpringAIModels.OPENAI,
		matchIfMissing = true)
public class OpenAiModerationAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public OpenAiModerationModel openAiSdkModerationModel(OpenAiConnectionProperties commonProperties,
			OpenAiModerationProperties moderationProperties) {

		OpenAIClient openAIClient = this.openAiClient(commonProperties);

		return OpenAiModerationModel.builder()
			.openAiClient(openAIClient)
			.options(moderationProperties.getOptions())
			.build();
	}

	private OpenAIClient openAiClient(OpenAiConnectionProperties resolved) {
		return OpenAiSetup.setupSyncClient(resolved.getBaseUrl(), resolved.getApiKey(), resolved.getCredential(),
				resolved.getMicrosoftDeploymentName(), resolved.getMicrosoftFoundryServiceVersion(),
				resolved.getOrganizationId(), resolved.isMicrosoftFoundry(), resolved.isGitHubModels(),
				resolved.getModel(), resolved.getTimeout(), resolved.getMaxRetries(), resolved.getProxy(),
				resolved.getCustomHeaders());
	}

}
