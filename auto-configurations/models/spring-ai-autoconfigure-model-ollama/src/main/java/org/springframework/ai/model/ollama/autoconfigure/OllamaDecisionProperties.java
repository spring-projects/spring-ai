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

package org.springframework.ai.model.ollama.autoconfigure;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.ollama.decision.OllamaDecisionOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ollama Decision autoconfiguration properties.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
@ConfigurationProperties(OllamaDecisionProperties.CONFIG_PREFIX)
public class OllamaDecisionProperties {

	public static final String CONFIG_PREFIX = "spring.ai.ollama.decision";

	/**
	 * The decision model to use. Defaults to {@code d1-omni:600m}.
	 */
	private @Nullable String model;

	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	public OllamaDecisionOptions toOptions() {
		return OllamaDecisionOptions.builder().model(this.model).build();
	}

}
