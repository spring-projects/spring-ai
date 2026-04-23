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

import org.springframework.ai.openai.AbstractOpenAiOptions;
import org.springframework.ai.openai.batch.OpenAiBatchOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * OpenAI SDK Batch API autoconfiguration properties.
 * <p>
 * Configuration properties are available under the {@code spring.ai.openai.batch} prefix.
 * All batch-specific settings (rate limits, token budgets, retry policies) are
 * configurable, eliminating the need for hardcoded values.
 *
 * @author Yasin Akbas
 * @since 2.0.0
 */
@ConfigurationProperties(OpenAiBatchProperties.CONFIG_PREFIX)
public class OpenAiBatchProperties extends AbstractOpenAiOptions {

	public static final String CONFIG_PREFIX = "spring.ai.openai.batch";

	/**
	 * Whether the OpenAI Batch API support is enabled.
	 */
	private boolean enabled = false;

	@NestedConfigurationProperty
	private final OpenAiBatchOptions options = OpenAiBatchOptions.builder().build();

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public OpenAiBatchOptions getOptions() {
		return this.options;
	}

}
