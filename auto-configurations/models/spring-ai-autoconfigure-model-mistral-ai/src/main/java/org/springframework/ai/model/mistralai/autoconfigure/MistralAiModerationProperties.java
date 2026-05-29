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

package org.springframework.ai.model.mistralai.autoconfigure;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.mistralai.moderation.MistralAiModerationOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Ricken Bazolo
 * @author Sebastien Deleuze
 */
@ConfigurationProperties(MistralAiModerationProperties.CONFIG_PREFIX)
public class MistralAiModerationProperties extends MistralAiParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.mistralai.moderation";

	private final Options options = new Options();

	public MistralAiModerationProperties() {
		super.setBaseUrl(MistralAiCommonProperties.DEFAULT_BASE_URL);
	}

	public Options getOptions() {
		return this.options;
	}

	public static class Options {

		private @Nullable String model;

		public @Nullable String getModel() {
			return this.model;
		}

		public void setModel(@Nullable String model) {
			this.model = model;
		}

		public MistralAiModerationOptions toOptions() {
			MistralAiModerationOptions.Builder builder = MistralAiModerationOptions.builder();
			if (this.model != null) {
				builder.model(this.model);
			}
			return builder.build();
		}

	}

}
