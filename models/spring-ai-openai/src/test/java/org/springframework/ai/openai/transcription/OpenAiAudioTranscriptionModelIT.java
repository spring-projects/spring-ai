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

package org.springframework.ai.openai.transcription;

import com.openai.models.audio.AudioResponseFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.OpenAiTestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link OpenAiAudioTranscriptionModel}.
 *
 * @author Michael Lavelle
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 */
@SpringBootTest(classes = OpenAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiAudioTranscriptionModelIT {

	private final Logger logger = LoggerFactory.getLogger(OpenAiAudioTranscriptionModelIT.class);

	@Autowired
	private OpenAiAudioTranscriptionModel transcriptionModel;

	@Test
	void callTest() {
		AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(new ClassPathResource("/speech.flac"));
		AudioTranscriptionResponse response = this.transcriptionModel.call(prompt);

		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResult().getOutput()).isNotBlank();
		logger.info("Transcription: {}", response.getResult().getOutput());
	}

	@Test
	void transcribeTest() {
		String text = this.transcriptionModel.transcribe(new ClassPathResource("/speech.flac"));

		assertThat(text).isNotBlank();
		logger.info("Transcription: {}", text);
	}

	@Test
	void transcribeWithOptionsTest() {
		OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
			.language("en")
			.temperature(0f)
			.responseFormat(AudioResponseFormat.TEXT)
			.build();

		AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(new ClassPathResource("/speech.flac"), options);
		AudioTranscriptionResponse response = this.transcriptionModel.call(prompt);

		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResult().getOutput()).isNotBlank();
		logger.info("Transcription with options: {}", response.getResult().getOutput());
	}

	@Test
	void transcribeWithVerboseFormatTest() {
		OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
			.responseFormat(AudioResponseFormat.VERBOSE_JSON)
			.build();

		String text = this.transcriptionModel.transcribe(new ClassPathResource("/speech.flac"), options);

		assertThat(text).isNotBlank();
		logger.info("Verbose transcription: {}", text);
	}

	@Test
	void transcribeTestWithOptions() {
		OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
			.language("en")
			.prompt("Ask not this, but ask that")
			.temperature(0f)
			.responseFormat(AudioResponseFormat.TEXT)
			.build();

		String text = this.transcriptionModel.transcribe(new ClassPathResource("/speech.flac"), options);

		assertThat(text).isNotBlank();
		logger.info("Transcription with options: {}", text);
	}

	@Test
	void callTestWithVttFormat() {
		OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
			.language("en")
			.prompt("Ask not this, but ask that")
			.temperature(0f)
			.responseFormat(AudioResponseFormat.VTT)
			.build();

		AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(new ClassPathResource("/speech.flac"), options);
		AudioTranscriptionResponse response = this.transcriptionModel.call(prompt);

		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResult().getOutput()).isNotBlank();
		logger.info("VTT transcription: {}", response.getResult().getOutput());
	}

}
