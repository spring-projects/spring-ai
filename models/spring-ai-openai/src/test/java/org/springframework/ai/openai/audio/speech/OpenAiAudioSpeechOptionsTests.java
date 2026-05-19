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

package org.springframework.ai.openai.audio.speech;

import org.junit.jupiter.api.Test;

import org.springframework.ai.audio.tts.TextToSpeechOptions;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiAudioSpeechOptionsTests {

	@Test
	void genericAudioSpeechOptionsAreMerged() {
		TextToSpeechOptions source = TextToSpeechOptions.builder()
			.model("generic-model")
			.voice("generic-voice")
			.format("mp3")
			.speed(1.5)
			.build();

		OpenAiAudioSpeechOptions merged = OpenAiAudioSpeechOptions.builder().merge(source).build();

		assertThat(merged.getModel()).isEqualTo("generic-model");
		assertThat(merged.getVoice()).isEqualTo("generic-voice");
		assertThat(merged.getResponseFormat()).isEqualTo("mp3");
		assertThat(merged.getSpeed()).isEqualTo(1.5);
	}

}
