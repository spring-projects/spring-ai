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

package org.springframework.ai.model.jlama.autoconfigure;

import org.springframework.ai.jlama.JlamaChatModel;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * {@link AutoConfiguration Auto-configuration} for Jlama Chat Model.
 *
 * @author chabinhwang
 */
@AutoConfiguration
@ConditionalOnClass(JlamaChatModel.class)
@EnableConfigurationProperties(JlamaChatProperties.class)
@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = SpringAIModels.JLAMA,
		matchIfMissing = true)
public class JlamaChatAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public JlamaChatModel jlamaChatModel(JlamaChatProperties properties) {
		if (StringUtils.hasText(properties.getWorkingDirectory())) {
			return new JlamaChatModel(properties.getModel(), properties.getWorkingDirectory(), properties.getOptions());
		}
		return new JlamaChatModel(properties.getModel(), properties.getOptions());
	}

}
