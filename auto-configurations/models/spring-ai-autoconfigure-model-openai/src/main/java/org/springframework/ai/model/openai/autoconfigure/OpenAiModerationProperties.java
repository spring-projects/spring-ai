/*
 * Copyright 2023-2024 the original author or authors.
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

	public static final String DEFAULT_MODERATION_PATH = "/v1/moderations";

	private String moderationPath = DEFAULT_MODERATION_PATH;

	/**
	 * Options for OpenAI Moderation API.
	 */
	@NestedConfigurationProperty
	private final OpenAiModerationOptions options = OpenAiModerationOptions.builder().build();

	public String getModerationPath() {
		return this.moderationPath;
	}

	public void setModerationPath(String moderationPath) {
		this.moderationPath = moderationPath;
	}

	public OpenAiModerationOptions getOptions() {
		return this.options;
	}

}
