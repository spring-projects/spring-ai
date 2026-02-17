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

package org.springframework.ai.google.genai.tts;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.google.genai.tts.api.GeminiTtsApi;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiTtsOptionsTests {

	@Test
	void testSingleSpeakerOptions() {
		GeminiTtsOptions options = GeminiTtsOptions.builder()
			.model("gemini-2.5-flash-preview-tts")
			.voice("Kore")
			.build();

		assertThat(options.getModel()).isEqualTo("gemini-2.5-flash-preview-tts");
		assertThat(options.getVoice()).isEqualTo("Kore");
		assertThat(options.getSpeakerVoiceConfigs()).isNull();
		assertThat(options.getFormat()).isEqualTo("pcm");
	}

	@Test
	void testMultiSpeakerOptions() {
		GeminiTtsApi.SpeakerVoiceConfig joe = new GeminiTtsApi.SpeakerVoiceConfig("Joe",
				new GeminiTtsApi.VoiceConfig(new GeminiTtsApi.PrebuiltVoiceConfig("Kore")));
		GeminiTtsApi.SpeakerVoiceConfig jane = new GeminiTtsApi.SpeakerVoiceConfig("Jane",
				new GeminiTtsApi.VoiceConfig(new GeminiTtsApi.PrebuiltVoiceConfig("Puck")));

		GeminiTtsOptions options = GeminiTtsOptions.builder()
			.model("gemini-2.5-flash-preview-tts")
			.speakerVoiceConfigs(List.of(joe, jane))
			.build();

		assertThat(options.getModel()).isEqualTo("gemini-2.5-flash-preview-tts");
		assertThat(options.getVoice()).isNull();
		assertThat(options.getSpeakerVoiceConfigs()).hasSize(2);
		assertThat(options.getSpeakerVoiceConfigs().get(0).speaker()).isEqualTo("Joe");
		assertThat(options.getSpeakerVoiceConfigs().get(0).voiceConfig().prebuiltVoiceConfig().voiceName())
			.isEqualTo("Kore");
	}

	@Test
	void testCopy() {
		GeminiTtsOptions original = GeminiTtsOptions.builder()
			.model("gemini-2.5-flash-preview-tts")
			.voice("Kore")
			.build();

		GeminiTtsOptions copy = original.copy();

		assertThat(copy).isNotSameAs(original);
		assertThat(copy.getModel()).isEqualTo(original.getModel());
		assertThat(copy.getVoice()).isEqualTo(original.getVoice());
	}

	@Test
	void testBuilder() {
		GeminiTtsOptions options = GeminiTtsOptions.builder()
			.model("gemini-2.5-pro-preview-tts")
			.voice("Puck")
			.speed(1.2)
			.build();

		assertThat(options.getModel()).isEqualTo("gemini-2.5-pro-preview-tts");
		assertThat(options.getVoice()).isEqualTo("Puck");
		assertThat(options.getSpeed()).isEqualTo(1.2);
	}

}
