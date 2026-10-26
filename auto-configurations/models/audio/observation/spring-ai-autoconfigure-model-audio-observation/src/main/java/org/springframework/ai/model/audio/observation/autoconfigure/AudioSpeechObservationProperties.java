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

package org.springframework.ai.model.audio.observation.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for text-to-speech model observations.
 *
 * @author Christian Tzolov
 * @since 2.0.2
 */
@ConfigurationProperties(AudioSpeechObservationProperties.CONFIG_PREFIX)
public class AudioSpeechObservationProperties {

	public static final String CONFIG_PREFIX = "spring.ai.audio.speech.observations";

	/**
	 * Whether to log the text given to the model in the observations.
	 */
	private boolean logText = false;

	public boolean isLogText() {
		return this.logText;
	}

	public void setLogText(boolean logText) {
		this.logText = logText;
	}

}
