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

package org.springframework.ai.model.openai.autoconfigure;

import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for OpenAI SDK audio transcription.
 *
 * @author Michael Lavelle
 * @author Christian Tzolov
 * @author Piotr Olaszewski
 * @author Ilayaperumal Gopinathan
 */
@ConfigurationProperties(OpenAiAudioTranscriptionProperties.CONFIG_PREFIX)
public class OpenAiAudioTranscriptionProperties {

	/**
	 * Configuration prefix for OpenAI SDK audio transcription.
	 */
	public static final String CONFIG_PREFIX = "spring.ai.openai.audio.transcription";

	@NestedConfigurationProperty
	private final OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
		.model(OpenAiAudioTranscriptionOptions.DEFAULT_TRANSCRIPTION_MODEL)
		.responseFormat(OpenAiAudioTranscriptionOptions.DEFAULT_RESPONSE_FORMAT)
		.build();

	public OpenAiAudioTranscriptionOptions getOptions() {
		return this.options;
	}

}
