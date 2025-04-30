/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.model.mistralai.autoconfigure;

import org.springframework.ai.mistralai.api.MistralAiModerationApi;
import org.springframework.ai.mistralai.moderation.MistralAiModerationOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * @author Ricken Bazolo
 */
@ConfigurationProperties(MistralAiModerationProperties.CONFIG_PREFIX)
public class MistralAiModerationProperties extends MistralAiParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.mistralai.moderation";

	private static final String DEFAULT_MODERATION_MODEL = MistralAiModerationApi.Model.MISTRAL_MODERATION.getValue();

	@NestedConfigurationProperty
	private MistralAiModerationOptions options = MistralAiModerationOptions.builder()
		.model(DEFAULT_MODERATION_MODEL)
		.build();

	public MistralAiModerationProperties() {
		super.setBaseUrl(MistralAiCommonProperties.DEFAULT_BASE_URL);
	}

	public MistralAiModerationOptions getOptions() {
		return this.options;
	}

	public void setOptions(MistralAiModerationOptions options) {
		this.options = options;
	}

}
