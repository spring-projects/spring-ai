/*
 * Copyright 2026-2026 the original author or authors.
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
import org.springframework.ai.openaisdk.OpenAiSdkAudioSpeechOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * OpenAI SDK Audio Speech autoconfiguration properties.
 *
 * @author Ilayaperumal Gopinathan
 * @since 2.0.0
 */
@ConfigurationProperties(OpenAiSdkAudioSpeechProperties.CONFIG_PREFIX)
public class OpenAiSdkAudioSpeechProperties extends AbstractOpenAiSdkOptions {

	public static final String CONFIG_PREFIX = "spring.ai.openai-sdk.audio.speech";

	public static final String DEFAULT_SPEECH_MODEL = OpenAiSdkAudioSpeechOptions.DEFAULT_SPEECH_MODEL;

	@NestedConfigurationProperty
	private final OpenAiSdkAudioSpeechOptions options = OpenAiSdkAudioSpeechOptions.builder()
		.model(DEFAULT_SPEECH_MODEL)
		.voice(OpenAiSdkAudioSpeechOptions.DEFAULT_VOICE)
		.responseFormat(OpenAiSdkAudioSpeechOptions.DEFAULT_RESPONSE_FORMAT)
		.speed(OpenAiSdkAudioSpeechOptions.DEFAULT_SPEED)
		.build();

	public OpenAiSdkAudioSpeechOptions getOptions() {
		return this.options;
	}

}
