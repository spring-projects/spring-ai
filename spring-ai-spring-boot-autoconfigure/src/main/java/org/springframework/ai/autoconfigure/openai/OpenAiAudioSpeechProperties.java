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

package org.springframework.ai.autoconfigure.openai;

import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * @author Ahmed Yousri
 */

@ConfigurationProperties(OpenAiAudioSpeechProperties.CONFIG_PREFIX)
public class OpenAiAudioSpeechProperties extends OpenAiParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.openai.audio.speech";

	public static final String DEFAULT_SPEECH_MODEL = OpenAiAudioApi.TtsModel.TTS_1.getValue();

	private static final Float SPEED = 1.0f;

	private static final OpenAiAudioApi.SpeechRequest.Voice VOICE = OpenAiAudioApi.SpeechRequest.Voice.ALLOY;

	private static final OpenAiAudioApi.SpeechRequest.AudioResponseFormat DEFAULT_RESPONSE_FORMAT = OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3;

	@NestedConfigurationProperty
	private OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder()
		.withModel(DEFAULT_SPEECH_MODEL)
		.withResponseFormat(DEFAULT_RESPONSE_FORMAT)
		.withVoice(VOICE)
		.withSpeed(SPEED)
		.build();

	public OpenAiAudioSpeechOptions getOptions() {
		return options;
	}

	public void setOptions(OpenAiAudioSpeechOptions options) {
		this.options = options;
	}

}
