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

package org.springframework.ai.model.chat.client.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the chat client builder.
 *
 * @author Christian Tzolov
 * @author Mark Pollack
 * @author Josh Long
 * @author Arjen Poutsma
 * @since 1.0.0
 */
@ConfigurationProperties(ChatClientBuilderProperties.CONFIG_PREFIX)
public class ChatClientBuilderProperties {

	public static final String CONFIG_PREFIX = "spring.ai.chat.client";

	/**
	 * Enable chat client builder.
	 */
	private boolean enabled = true;

	private Observations observations = new Observations();

	public Observations getObservations() {
		return this.observations;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public static class Observations {

		/**
		 * Whether to include the input content in the observations.
		 */
		private boolean includeInput = false;

		public boolean isIncludeInput() {
			return this.includeInput;
		}

		public void setIncludeInput(boolean includeCompletion) {
			this.includeInput = includeCompletion;
		}

	}

}
