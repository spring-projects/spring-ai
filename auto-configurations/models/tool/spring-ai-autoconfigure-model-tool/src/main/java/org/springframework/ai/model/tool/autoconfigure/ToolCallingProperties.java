/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.model.tool.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for tool calling.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
@ConfigurationProperties(ToolCallingProperties.CONFIG_PREFIX)
public class ToolCallingProperties {

	public static final String CONFIG_PREFIX = "spring.ai.tools";

	private final Observations observations = new Observations();

	public Observations getObservations() {
		return this.observations;
	}

	/**
	 * If true, tool calling errors are thrown as exceptions for the caller to handle. If
	 * false, errors are converted to messages and sent back to the AI model, allowing it
	 * to process and respond to the error.
	 */
	private boolean throwExceptionOnError = false;

	public boolean isThrowExceptionOnError() {
		return this.throwExceptionOnError;
	}

	public void setThrowExceptionOnError(boolean throwExceptionOnError) {
		this.throwExceptionOnError = throwExceptionOnError;
	}

	public static class Observations {

		/**
		 * Whether to include the tool call content in the observations.
		 */
		private boolean includeContent = false;

		public boolean isIncludeContent() {
			return this.includeContent;
		}

		public void setIncludeContent(boolean includeContent) {
			this.includeContent = includeContent;
		}

	}

}
