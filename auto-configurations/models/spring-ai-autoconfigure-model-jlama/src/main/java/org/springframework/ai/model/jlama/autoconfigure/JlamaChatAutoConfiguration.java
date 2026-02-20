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
import org.springframework.ai.jlama.api.JlamaChatOptions;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * {@link AutoConfiguration Auto-configuration} for Jlama Chat Model.
 *
 * @author chabinhwang
 */
@AutoConfiguration
@ConditionalOnClass(JlamaChatModel.class)
@EnableConfigurationProperties({ JlamaChatProperties.class, JlamaLegacyChatProperties.class })
@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = SpringAIModels.JLAMA,
		matchIfMissing = true)
public class JlamaChatAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public JlamaChatModel jlamaChatModel(JlamaChatProperties properties, JlamaLegacyChatProperties legacyProperties) {
		String modelPath = resolveModelPath(properties, legacyProperties);
		String workingDirectory = resolveWorkingDirectory(properties, legacyProperties);
		JlamaChatOptions options = resolveOptions(properties, legacyProperties, modelPath);

		if (StringUtils.hasText(workingDirectory)) {
			return new JlamaChatModel(modelPath, workingDirectory, options);
		}
		return new JlamaChatModel(modelPath, options);
	}

	private static String resolveModelPath(JlamaChatProperties properties, JlamaLegacyChatProperties legacyProperties) {
		String modelPath = StringUtils.hasText(properties.getModel()) ? properties.getModel()
				: legacyProperties.getModel();
		Assert.hasText(modelPath,
				"Jlama model path must be set via 'spring.ai.jlama.chat.model' (or legacy 'spring.ai.jlama.model')");
		return modelPath;
	}

	private static String resolveWorkingDirectory(JlamaChatProperties properties,
			JlamaLegacyChatProperties legacyProperties) {
		return StringUtils.hasText(properties.getWorkingDirectory()) ? properties.getWorkingDirectory()
				: legacyProperties.getWorkingDirectory();
	}

	private static JlamaChatOptions resolveOptions(JlamaChatProperties properties,
			JlamaLegacyChatProperties legacyProperties, String modelPath) {
		JlamaChatOptions options = ModelOptionsUtils.merge(properties.getOptions(), legacyProperties.getOptions(),
				JlamaChatOptions.class);
		options.setModel(modelPath);
		return options;
	}

}
