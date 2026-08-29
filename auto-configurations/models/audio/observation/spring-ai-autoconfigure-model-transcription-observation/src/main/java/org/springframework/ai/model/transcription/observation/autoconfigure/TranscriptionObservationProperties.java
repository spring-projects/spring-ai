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

package org.springframework.ai.model.transcription.observation.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for transcription model observations.
 *
 * @author Olivier Le Quellec
 * @since 2.0.1
 */
@ConfigurationProperties(TranscriptionObservationProperties.CONFIG_PREFIX)
public class TranscriptionObservationProperties {

	public static final String CONFIG_PREFIX = "spring.ai.transcription.observations";

	/**
	 * Whether to log the prompt content in the observations.
	 */
	private boolean logPrompt = false;

	public boolean isLogPrompt() {
		return this.logPrompt;
	}

	public void setLogPrompt(boolean logPrompt) {
		this.logPrompt = logPrompt;
	}

}
