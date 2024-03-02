/*
 * Copyright 2024-2024 the original author or authors.
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

import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.api.OpenAiAudioApi.SpeechRequest;
import org.springframework.ai.openai.api.OpenAiAudioApi.TranscriptionRequest;
import org.springframework.ai.openai.api.OpenAiAudioApi.StructuredResponse;
import org.springframework.ai.openai.api.OpenAiAudioApi.TranslationRequest;
import org.springframework.ai.openai.api.OpenAiAudioApi.SpeechRequest.Voice;
import org.springframework.ai.openai.api.OpenAiAudioApi.TtsModel;
import org.springframework.ai.openai.api.OpenAiAudioApi.WhisperModel;
import org.springframework.util.FileCopyUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiAudioApiIT {

	OpenAiAudioApi audioApi = new OpenAiAudioApi(System.getenv("OPENAI_API_KEY"));

	@Test
	void speechTranscriptionAndTranslation() throws IOException {

		byte[] speech = audioApi.createSpeech(SpeechRequest.builder()
			.withModel(TtsModel.TTS_1_HD.getValue())
			.withInput("Hello, my name is Chris and I love Spring A.I.")
			.withVoice(Voice.ONYX)
			.build());

		assertThat(speech).isNotEmpty();

		FileCopyUtils.copy(speech, new File("target/speech.mp3"));

		StructuredResponse translation = audioApi.createTranslation(
				TranslationRequest.builder().withModel(WhisperModel.WHISPER_1.getValue()).withFile(speech).build(),
				StructuredResponse.class);

		assertThat(translation.text()).isEqualTo("Hello, my name is Chris, and I love Spring AI.");

		StructuredResponse transcriptionEnglish = audioApi.createTranscription(
				TranscriptionRequest.builder().withModel(WhisperModel.WHISPER_1.getValue()).withFile(speech).build(),
				StructuredResponse.class);

		assertThat(transcriptionEnglish.text()).isEqualTo("Hello, my name is Chris, and I love Spring AI.");

		StructuredResponse transcriptionDutch = audioApi.createTranscription(
				TranscriptionRequest.builder().withFile(speech).withLanguage("nl").build(), StructuredResponse.class);

		assertThat(transcriptionDutch.text()).isEqualTo("Hallo, mijn naam is Chris en ik hou van Spring AI.");
	}

}
