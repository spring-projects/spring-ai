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

package org.springframework.ai.model.anthropic.autoconfigure;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Anthropic Message Batches autoconfiguration properties.
 *
 * <p>
 * The batch model is opt-in: set {@code spring.ai.anthropic.batch.enabled=true} to get an
 * {@link org.springframework.ai.anthropic.AnthropicBatchModel} bean. Connection settings
 * ({@code spring.ai.anthropic.*}) and the model defaults
 * ({@code spring.ai.anthropic.chat.*}) are shared with the chat model; the two properties
 * here only override the model and output-token ceiling for batch entries when batches
 * need to differ from realtime calls.
 *
 * @author Ricken Bazolo
 * @since 2.0.0
 */
@ConfigurationProperties(AnthropicBatchProperties.CONFIG_PREFIX)
public class AnthropicBatchProperties {

	public static final String CONFIG_PREFIX = "spring.ai.anthropic.batch";

	/**
	 * Whether to expose an Anthropic batch model bean. Disabled by default so that
	 * applications that do not use batches do not pay for a second HTTP client.
	 */
	private boolean enabled = false;

	/**
	 * Model to use for batch entries. Falls back to
	 * {@code spring.ai.anthropic.chat.model}.
	 */
	private @Nullable String model;

	/**
	 * Maximum number of tokens to generate per batch entry. Falls back to
	 * {@code spring.ai.anthropic.chat.max-tokens}.
	 */
	private @Nullable Integer maxTokens;

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	public @Nullable Integer getMaxTokens() {
		return this.maxTokens;
	}

	public void setMaxTokens(@Nullable Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

}
