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

import org.springframework.ai.openai.OpenAiModerationOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

/**
 * OpenAI SDK Moderation autoconfiguration properties.
 *
 * @author Ahmed Yousri
 * @author Ilayaperumal Gopinathan
 */
@ConfigurationProperties(OpenAiModerationProperties.CONFIG_PREFIX)
public class OpenAiModerationProperties {

	public static final String CONFIG_PREFIX = "spring.ai.openai.moderation";

	private String model = OpenAiModerationOptions.DEFAULT_MODERATION_MODEL;

	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public OpenAiModerationOptions toOptions() {
		OpenAiModerationOptions.Builder builder = OpenAiModerationOptions.builder();
		if (this.getModel() != null) {
			builder.model(this.getModel());
		}
		return builder.build();
	}

	private Options options = new Options();

	@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.moderation")
	@Deprecated(since = "2.0.0", forRemoval = true)
	public Options getOptions() {
		return this.options;
	}

	public void setOptions(Options options) {
		this.options = options;
	}

	public class Options {

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.moderation.model")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public String getModel() {
			return OpenAiModerationProperties.this.getModel();
		}

		public void setModel(String model) {
			OpenAiModerationProperties.this.setModel(model);
		}

	}

}
