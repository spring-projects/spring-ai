/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.model.elevenlabs.autoconfigure;

import org.springframework.ai.elevenlabs.ElevenLabsTextToSpeechOptions;
import org.springframework.ai.elevenlabs.api.ElevenLabsApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for the ElevenLabs Text-to-Speech API.
 *
 * @author Alexandros Pappas
 */
@ConfigurationProperties(ElevenLabsSpeechProperties.CONFIG_PREFIX)
public class ElevenLabsSpeechProperties {

	public static final String CONFIG_PREFIX = "spring.ai.elevenlabs.tts";

	public static final String DEFAULT_MODEL_ID = "eleven_turbo_v2_5";

	private static final String DEFAULT_VOICE_ID = "9BWtsMINqrJLrRacOk9x";

	private static final ElevenLabsApi.OutputFormat DEFAULT_OUTPUT_FORMAT = ElevenLabsApi.OutputFormat.MP3_22050_32;

	@NestedConfigurationProperty
	private final ElevenLabsTextToSpeechOptions options = ElevenLabsTextToSpeechOptions.builder()
		.modelId(DEFAULT_MODEL_ID)
		.voiceId(DEFAULT_VOICE_ID)
		.outputFormat(DEFAULT_OUTPUT_FORMAT.getValue())
		.build();

	public ElevenLabsTextToSpeechOptions getOptions() {
		return this.options;
	}

}
