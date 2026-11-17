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
	 * Whether to log the completion content in the observations.
	 */
	private boolean logCompletion = false;

	/**
	 * Whether to log the prompt content in the observations.
	 */
	private boolean logPrompt = false;

	/**
	 * Whether to include prompt content as high-cardinality key-values on the observation
	 * span.
	 */
	private boolean includePrompt = false;

	/**
	 * Whether to include completion content as high-cardinality key-values on the
	 * observation span.
	 */
	private boolean includeCompletion = false;

	/**
	 * Maximum character size limit for prompt and completion content appended to
	 * observations.
	 */
	private int maxContentLength = 2000;

	/**
	 * Whether to include error logging in the observations.
	 */
	private boolean includeErrorLogging = false;

	private Keys keys = new Keys();

	public boolean isLogCompletion() {
		return this.logCompletion;
	}

	public void setLogCompletion(boolean logCompletion) {
		this.logCompletion = logCompletion;
	}

	public boolean isLogPrompt() {
		return this.logPrompt;
	}

	public void setLogPrompt(boolean logPrompt) {
		this.logPrompt = logPrompt;
	}

	public boolean isIncludePrompt() {
		return this.includePrompt;
	}

	public void setIncludePrompt(boolean includePrompt) {
		this.includePrompt = includePrompt;
	}

	public boolean isIncludeCompletion() {
		return this.includeCompletion;
	}

	public void setIncludeCompletion(boolean includeCompletion) {
		this.includeCompletion = includeCompletion;
	}

	public int getMaxContentLength() {
		return this.maxContentLength;
	}

	public void setMaxContentLength(int maxContentLength) {
		this.maxContentLength = maxContentLength;
	}

	public boolean isIncludeErrorLogging() {
		return this.includeErrorLogging;
	}

	public void setIncludeErrorLogging(boolean includeErrorLogging) {
		this.includeErrorLogging = includeErrorLogging;
	}

	public Keys getKeys() {
		return this.keys;
	}

	public void setKeys(Keys keys) {
		this.keys = keys;
	}

	public static class Keys {

		private String prompt = "gen_ai.prompt";

		private String completion = "gen_ai.completion";

		public String getPrompt() {
			return this.prompt;
		}

		public void setPrompt(String prompt) {
			this.prompt = prompt;
		}

		public String getCompletion() {
			return this.completion;
		}

		public void setCompletion(String completion) {
			this.completion = completion;
		}

	}

}
