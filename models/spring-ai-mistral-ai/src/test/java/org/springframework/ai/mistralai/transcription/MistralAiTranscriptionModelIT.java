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

package org.springframework.ai.mistralai.transcription;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.mistralai.MistralAiTestConfiguration;
import org.springframework.ai.mistralai.api.MistralAiAudioApi.TimestampGranularity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link MistralAiTranscriptionModel}.
 *
 * @author Thomas Vitale
 */
@SpringBootTest(classes = MistralAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".+")
class MistralAiTranscriptionModelIT {

	private static final Resource AUDIO = new ClassPathResource("/speech.flac");

	@Autowired
	private MistralAiTranscriptionModel transcriptionModel;

	@Test
	void transcribe() {
		String text = this.transcriptionModel.transcribe(AUDIO);

		assertThat(text).isNotBlank();
	}

	@Test
	void callWithSegmentTimestampsAndDiarization() {
		var options = MistralAiTranscriptionOptions.builder()
			.model("voxtral-mini-transcribe-3")
			.language("en")
			.diarize(true)
			.timestampGranularities(List.of(TimestampGranularity.SEGMENT))
			.build();

		AudioTranscriptionResponse response = this.transcriptionModel
			.call(new AudioTranscriptionPrompt(AUDIO, options));

		assertThat(response.getResult().getOutput()).isNotBlank();
		assertThat(response.getMetadata()).isInstanceOfSatisfying(MistralAiTranscriptionResponseMetadata.class,
				metadata -> {
					assertThat(metadata.getModel()).isNotBlank();
					assertThat(metadata.getSegments()).isNotEmpty()
						.allSatisfy(segment -> assertThat(segment.speakerId()).isNotBlank());
					assertThat(metadata.getUsage().getTotalTokens()).isPositive();
				});
	}

	@Test
	void stream() {
		List<String> chunks = this.transcriptionModel.streamTranscribe(AUDIO)
			.collectList()
			.block(Duration.ofMinutes(1));

		assertThat(chunks).isNotEmpty();
		assertThat(String.join("", chunks)).isNotBlank();
	}

}
