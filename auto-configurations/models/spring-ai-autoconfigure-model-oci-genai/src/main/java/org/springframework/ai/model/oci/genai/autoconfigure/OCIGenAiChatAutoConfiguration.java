/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.model.oci.genai.autoconfigure;

import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.oci.cohere.OCICohereChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Chat {@link AutoConfiguration Auto-configuration} for Oracle Cloud Infrastructure
 * Generative AI.
 *
 * @author Anders Swanson
 * @author Ilayaperumal Gopinathan
 * @author Yanming Zhou
 * @author Issam El-atif
 */
@AutoConfiguration(after = OCIGenAiInferenceClientAutoConfiguration.class,
		beforeName = "org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration")
@ConditionalOnClass(OCICohereChatModel.class)
@EnableConfigurationProperties(OCICohereChatModelProperties.class)
@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = SpringAIModels.OCI_GENAI,
		matchIfMissing = true)
public class OCIGenAiChatAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public OCICohereChatModel ociChatModel(GenerativeAiInferenceClient generativeAiClient,
			OCICohereChatModelProperties properties, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ChatModelObservationConvention> observationConvention) {
		var chatModel = new OCICohereChatModel(generativeAiClient, properties.getOptions(),
				observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP));
		observationConvention.ifAvailable(chatModel::setObservationConvention);

		return chatModel;
	}

}
