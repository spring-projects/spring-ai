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

import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Fail-fast diagnostic when {@code spring.ai.anthropic.backend=vertex-ai} is set but
 * {@code com.anthropic:anthropic-java-vertex} is missing from the classpath.
 *
 * @author dragonfsky
 * @since 2.0.0
 */
@AutoConfiguration
@ConditionalOnMissingClass("com.anthropic.vertex.backends.VertexBackend")
@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = SpringAIModels.ANTHROPIC,
		matchIfMissing = true)
@ConditionalOnProperty(prefix = AnthropicConnectionProperties.CONFIG_PREFIX, name = "backend",
		havingValue = "vertex-ai")
public final class MissingAnthropicVertexDependencyConfiguration {

	private MissingAnthropicVertexDependencyConfiguration() {
	}

	@Bean
	static BeanFactoryPostProcessor anthropicVertexMissingDependencyFailure() {
		return new BeanFactoryPostProcessor() {
			@Override
			public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
				throw new IllegalStateException(
						"spring.ai.anthropic.backend=vertex-ai requires com.anthropic:anthropic-java-vertex "
								+ "on the classpath. Add the dependency to your build, "
								+ "or use spring-ai-starter-model-anthropic-vertex if available.");
			}
		};
	}

}
