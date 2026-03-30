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

package org.springframework.ai.model.openaisdk.autoconfigure;

import org.springframework.ai.openaisdk.AbstractOpenAiSdkOptions;
import org.springframework.ai.openaisdk.OpenAiSdkAudioTranscriptionOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for OpenAI SDK audio transcription.
 *
 * @author Ilayaperumal Gopinathan
 * @author Michael Lavelle
 * @author Christian Tzolov
 * @author Piotr Olaszewski
 * @since 2.0.0
 */
@ConfigurationProperties(OpenAiSdkAudioTranscriptionProperties.CONFIG_PREFIX)
public class OpenAiSdkAudioTranscriptionProperties extends AbstractOpenAiSdkOptions {

	/**
	 * Configuration prefix for OpenAI SDK audio transcription.
	 */
	public static final String CONFIG_PREFIX = "spring.ai.openai-sdk.audio.transcription";

	@NestedConfigurationProperty
	private final OpenAiSdkAudioTranscriptionOptions options = OpenAiSdkAudioTranscriptionOptions.builder()
		.model(OpenAiSdkAudioTranscriptionOptions.DEFAULT_TRANSCRIPTION_MODEL)
		.responseFormat(OpenAiSdkAudioTranscriptionOptions.DEFAULT_RESPONSE_FORMAT)
		.build();

	public OpenAiSdkAudioTranscriptionOptions getOptions() {
		return this.options;
	}

}
