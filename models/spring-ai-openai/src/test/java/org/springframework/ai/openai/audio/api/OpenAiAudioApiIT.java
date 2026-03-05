/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.openai.audio.api;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.api.OpenAiAudioApi.SpeechRequest;
import org.springframework.ai.openai.api.OpenAiAudioApi.SpeechRequest.Voice;
import org.springframework.ai.openai.api.OpenAiAudioApi.StructuredResponse;
import org.springframework.ai.openai.api.OpenAiAudioApi.TranscriptionModels;
import org.springframework.ai.openai.api.OpenAiAudioApi.TranscriptionRequest;
import org.springframework.ai.openai.api.OpenAiAudioApi.TranslationRequest;
import org.springframework.ai.openai.api.OpenAiAudioApi.TtsModel;
import org.springframework.util.FileCopyUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Jonghoon Park
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiAudioApiIT {

	OpenAiAudioApi audioApi = OpenAiAudioApi.builder()
		.apiKey(new SimpleApiKey(System.getenv("OPENAI_API_KEY")))
		.speechPath("/v1/audio/speech")
		.transcriptionPath("/v1/audio/transcriptions")
		.build();

	@SuppressWarnings("null")
	@Test
	void speechTranscriptionAndTranslation() throws IOException {

		byte[] speech = this.audioApi
			.createSpeech(SpeechRequest.builder()
				.model(TtsModel.GPT_4_O_MINI_TTS.getValue())
				.input("Hello, my name is Chris and I love Spring A.I.")
				.voice(Voice.ONYX.getValue())
				.build())
			.getBody();

		assertThat(speech).isNotEmpty();

		FileCopyUtils.copy(speech, new File("target/speech.mp3"));

		StructuredResponse translation = this.audioApi
			.createTranslation(TranslationRequest.builder()
				.model(TranscriptionModels.WHISPER_1.getValue())
				.file(speech)
				.fileName("speech.mp3")
				.build(), StructuredResponse.class)
			.getBody();

		assertThat(translation.text().replaceAll(",", "")).isEqualTo("Hello my name is Chris and I love Spring AI.");

		StructuredResponse transcriptionEnglish = this.audioApi
			.createTranscription(TranscriptionRequest.builder()
				.model(TranscriptionModels.WHISPER_1.getValue())
				.file(speech)
				.fileName("speech.mp3")
				.build(), StructuredResponse.class)
			.getBody();

		assertThat(transcriptionEnglish.text().replaceAll(",", ""))
			.isEqualTo("Hello my name is Chris and I love Spring AI.");

		StructuredResponse transcriptionDutch = this.audioApi
			.createTranscription(
					TranscriptionRequest.builder().file(speech).fileName("speech.mp3").language("nl").build(),
					StructuredResponse.class)
			.getBody();

		assertThat(transcriptionDutch.text()).containsAnyOf("Hallo, mijn naam is Chris en ik hou van Spring AI.",
				"Hallo, mijn naam is Chris en ik houd van Spring AI.");
	}

}
