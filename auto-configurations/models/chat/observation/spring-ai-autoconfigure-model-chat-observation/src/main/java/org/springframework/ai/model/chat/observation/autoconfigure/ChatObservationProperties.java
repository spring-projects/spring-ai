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

package org.springframework.ai.model.chat.observation.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for chat model observations.
 *
 * @author Thomas Vitale
 * @author Christian Tzolov
 * @since 1.0.0
 */
@ConfigurationProperties(ChatObservationProperties.CONFIG_PREFIX)
public class ChatObservationProperties {

	public static final String CONFIG_PREFIX = "spring.ai.chat.observations";

	/**
	 * Whether to include the completion content in the observations.
	 */
	private boolean includeCompletion = false;

	/**
	 * Whether to log the prompt content in the observations.
	 */
	private boolean logPrompt = false;

	/**
	 * Whether to include error logging in the observations.
	 */
	private boolean includeErrorLogging = false;

	public boolean isIncludeCompletion() {
		return this.includeCompletion;
	}

	public void setIncludeCompletion(boolean includeCompletion) {
		this.includeCompletion = includeCompletion;
	}

	public boolean isLogPrompt() {
		return this.logPrompt;
	}

	public void setLogPrompt(boolean logPrompt) {
		this.logPrompt = logPrompt;
	}

	public boolean isIncludeErrorLogging() {
		return this.includeErrorLogging;
	}

	public void setIncludeErrorLogging(boolean includeErrorLogging) {
		this.includeErrorLogging = includeErrorLogging;
	}

}
