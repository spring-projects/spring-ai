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

package org.springframework.ai.model.openai.autoconfigure;

import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for OpenAI audio speech.
 *
 * Default values for required options are model = tts_1, response format = mp3, voice =
 * alloy, and speed = 1.
 *
 * @author Ahmed Yousri
 * @author Stefan Vassilev
 * @author Jonghoon Park
 */
@ConfigurationProperties(OpenAiAudioSpeechProperties.CONFIG_PREFIX)
public class OpenAiAudioSpeechProperties extends OpenAiParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.openai.audio.speech";

	public static final String DEFAULT_SPEECH_MODEL = OpenAiAudioApi.TtsModel.GPT_4_O_MINI_TTS.getValue();

	public static final String DEFAULT_SPEECH_PATH = "/v1/audio/speech";

	private static final Double SPEED = 1.0;

	private static final String VOICE = OpenAiAudioApi.SpeechRequest.Voice.ALLOY.getValue();

	private String speechPath = DEFAULT_SPEECH_PATH;

	private static final OpenAiAudioApi.SpeechRequest.AudioResponseFormat DEFAULT_RESPONSE_FORMAT = OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3;

	@NestedConfigurationProperty
	private final OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder()
		.model(DEFAULT_SPEECH_MODEL)
		.responseFormat(DEFAULT_RESPONSE_FORMAT)
		.voice(VOICE)
		.speed(SPEED)
		.build();

	public String getSpeechPath() {
		return this.speechPath;
	}

	public void setSpeechPath(String speechPath) {
		this.speechPath = speechPath;
	}

	public OpenAiAudioSpeechOptions getOptions() {
		return this.options;
	}

}
