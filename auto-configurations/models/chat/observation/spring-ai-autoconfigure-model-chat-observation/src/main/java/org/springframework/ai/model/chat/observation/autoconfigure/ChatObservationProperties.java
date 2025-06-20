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

import org.springframework.ai.chat.observation.trace.AiObservationContentFormatterName;
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
	 * Whether to trace the completion content in the observations.
	 */
	private boolean traceCompletion = false;

	/**
	 * Whether to trace the prompt content in the observations.
	 */
	private boolean tracePrompt = false;

	/**
	 * prompt size in trace, smaller than 1 is unlimit
	 */
	private int tracePromptSize = 10;

	/**
	 * prompt and completion formatter
	 */
	private AiObservationContentFormatterName contentFormatter = AiObservationContentFormatterName.TEXT;

	/**
	 * Whether to include error logging in the observations.
	 */
	private boolean includeErrorLogging = false;

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

	public boolean isTraceCompletion() {
		return traceCompletion;
	}

	public void setTraceCompletion(boolean traceCompletion) {
		this.traceCompletion = traceCompletion;
	}

	public boolean isTracePrompt() {
		return tracePrompt;
	}

	public void setTracePrompt(boolean tracePrompt) {
		this.tracePrompt = tracePrompt;
	}

	public int getTracePromptSize() {
		return tracePromptSize;
	}

	public void setTracePromptSize(int tracePromptSize) {
		this.tracePromptSize = tracePromptSize;
	}

	public AiObservationContentFormatterName getContentFormatter() {
		return contentFormatter;
	}

	public void setContentFormatter(AiObservationContentFormatterName contentFormatter) {
		this.contentFormatter = contentFormatter;
	}

	public boolean isIncludeErrorLogging() {
		return this.includeErrorLogging;
	}

	public void setIncludeErrorLogging(boolean includeErrorLogging) {
		this.includeErrorLogging = includeErrorLogging;
	}

}
