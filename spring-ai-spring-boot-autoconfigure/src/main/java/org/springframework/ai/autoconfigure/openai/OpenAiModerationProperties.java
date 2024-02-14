/*
 * Copyright 2024 the original author or authors.
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

package org.springframework.ai.autoconfigure.openai;

import org.springframework.ai.openai.OpenAiModerationOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * OpenAI Moderation autoconfiguration properties.
 *
 * @author Ahmed Yousri
 * @since 0.9.0
 */
@ConfigurationProperties(OpenAiModerationProperties.CONFIG_PREFIX)
public class OpenAiModerationProperties extends OpenAiParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.openai.moderation";

	/**
	 * Options for OpenAI Moderation API.
	 */
	@NestedConfigurationProperty
	private OpenAiModerationOptions options = OpenAiModerationOptions.builder().build();

	public OpenAiModerationOptions getOptions() {
		return options;
	}

	public void setOptions(OpenAiModerationOptions options) {
		this.options = options;
	}

}
