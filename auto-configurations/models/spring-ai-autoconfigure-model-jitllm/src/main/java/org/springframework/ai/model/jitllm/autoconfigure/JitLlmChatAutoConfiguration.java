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

package org.springframework.ai.model.jitllm.autoconfigure;

import java.nio.file.Path;
import java.util.Objects;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.jitllm.JitLlmChatModel;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * {@link AutoConfiguration Auto-configuration} for the jitLLM chat model. Active when
 * {@code spring.ai.jitllm.chat.model-path} is set.
 *
 * @author Yuheng Zhou
 * @author Michalis Papadimitriou
 * @since 2.1.0
 */
@AutoConfiguration
@ConditionalOnClass(JitLlmChatModel.class)
@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = SpringAIModels.JITLLM,
		matchIfMissing = true)
@ConditionalOnProperty(prefix = JitLlmChatProperties.CONFIG_PREFIX, name = "model-path")
@EnableConfigurationProperties(JitLlmChatProperties.class)
public class JitLlmChatAutoConfiguration {

	@Bean(destroyMethod = "close")
	@ConditionalOnMissingBean
	public JitLlmChatModel jitLlmChatModel(JitLlmChatProperties properties, ToolCallingManager toolCallingManager,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ChatModelObservationConvention> observationConvention) {
		Path modelPath = Objects.requireNonNull(properties.getModelPath(),
				JitLlmChatProperties.CONFIG_PREFIX + ".model-path must be set");
		JitLlmChatModel chatModel = JitLlmChatModel.builder()
			.modelPath(modelPath)
			.modelName(properties.getModelName())
			.onGpu(properties.isOnGpu())
			.contextLength(properties.getContextLength())
			.defaultOptions(properties.toOptions())
			.toolCallingManager(toolCallingManager)
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.build();
		observationConvention.ifAvailable(chatModel::setObservationConvention);
		return chatModel;
	}

}
