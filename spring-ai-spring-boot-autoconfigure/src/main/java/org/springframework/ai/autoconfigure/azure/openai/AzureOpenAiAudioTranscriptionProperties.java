/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.autoconfigure.azure.openai;

import org.springframework.ai.azure.openai.AzureOpenAiAudioTranscriptionOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * @author Piotr Olaszewski
 */
@ConfigurationProperties(AzureOpenAiAudioTranscriptionProperties.CONFIG_PREFIX)
public class AzureOpenAiAudioTranscriptionProperties {

	public static final String CONFIG_PREFIX = "spring.ai.azure.openai.audio.transcription";

	/**
	 * Enable AzureOpenAI audio transcription model.
	 */
	private boolean enabled = true;

	@NestedConfigurationProperty
	private AzureOpenAiAudioTranscriptionOptions options = AzureOpenAiAudioTranscriptionOptions.builder().build();

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public AzureOpenAiAudioTranscriptionOptions getOptions() {
		return options;
	}

	public void setOptions(AzureOpenAiAudioTranscriptionOptions options) {
		this.options = options;
	}

}
