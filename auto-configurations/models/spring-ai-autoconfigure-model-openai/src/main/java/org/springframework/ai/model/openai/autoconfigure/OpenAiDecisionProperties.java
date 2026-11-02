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

import org.jspecify.annotations.Nullable;

import org.springframework.ai.openai.OpenAiDecisionOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the OpenAI decision model.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
@ConfigurationProperties(OpenAiDecisionProperties.CONFIG_PREFIX)
public class OpenAiDecisionProperties extends AbstractOpenAiProperties {

	public static final String CONFIG_PREFIX = "spring.ai.openai.decision";

	/**
	 * Stable identifier of the end user, sent to OpenAI to help detect abuse.
	 */
	private @Nullable String safetyIdentifier;

	public @Nullable String getSafetyIdentifier() {
		return this.safetyIdentifier;
	}

	public void setSafetyIdentifier(@Nullable String safetyIdentifier) {
		this.safetyIdentifier = safetyIdentifier;
	}

	public OpenAiDecisionOptions toOptions() {
		OpenAiDecisionOptions.Builder builder = OpenAiDecisionOptions.builder();
		if (this.getModel() != null) {
			builder.model(this.getModel());
		}
		if (this.safetyIdentifier != null) {
			builder.safetyIdentifier(this.safetyIdentifier);
		}
		return builder.build();
	}

}
