/*
 * Copyright 2026-2026 the original author or authors.
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

import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.openaisdk.AbstractOpenAiSdkOptions;
import org.springframework.ai.openaisdk.OpenAiSdkAudioSpeechModel;
import org.springframework.ai.openaisdk.setup.OpenAiSdkSetup;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Audio Speech {@link AutoConfiguration Auto-configuration} for OpenAI SDK.
 *
 * @author Ilayaperumal Gopinathan
 * @since 2.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties({ OpenAiSdkConnectionProperties.class, OpenAiSdkAudioSpeechProperties.class })
@ConditionalOnProperty(name = SpringAIModelProperties.AUDIO_SPEECH_MODEL, havingValue = SpringAIModels.OPENAI_SDK,
		matchIfMissing = false)
public class OpenAiSdkAudioSpeechAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public OpenAiSdkAudioSpeechModel openAiSdkAudioSpeechModel(OpenAiSdkConnectionProperties commonProperties,
			OpenAiSdkAudioSpeechProperties speechProperties) {

		OpenAiSdkAutoConfigurationUtil.ResolvedConnectionProperties resolvedConnectionProperties = OpenAiSdkAutoConfigurationUtil
			.resolveConnectionProperties(commonProperties, speechProperties);

		OpenAIClient openAIClient = this.openAiClient(resolvedConnectionProperties);

		return OpenAiSdkAudioSpeechModel.builder()
			.openAiClient(openAIClient)
			.defaultOptions(speechProperties.getOptions())
			.build();
	}

	private OpenAIClient openAiClient(AbstractOpenAiSdkOptions resolved) {

		return OpenAiSdkSetup.setupSyncClient(resolved.getBaseUrl(), resolved.getApiKey(), resolved.getCredential(),
				resolved.getMicrosoftDeploymentName(), resolved.getMicrosoftFoundryServiceVersion(),
				resolved.getOrganizationId(), resolved.isMicrosoftFoundry(), resolved.isGitHubModels(),
				resolved.getModel(), resolved.getTimeout(), resolved.getMaxRetries(), resolved.getProxy(),
				resolved.getCustomHeaders());
	}

}
