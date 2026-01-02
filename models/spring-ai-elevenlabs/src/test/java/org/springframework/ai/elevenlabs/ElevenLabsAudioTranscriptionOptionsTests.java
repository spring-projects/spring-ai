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

package org.springframework.ai.elevenlabs;

import org.junit.jupiter.api.Test;

import org.springframework.ai.elevenlabs.api.ElevenLabsSpeechToTextApi;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ElevenLabsAudioTranscriptionOptions}.
 *
 * @author Alexandros Pappas
 */
class ElevenLabsAudioTranscriptionOptionsTests {

	@Test
	void testBuilderDefaults() {
		ElevenLabsAudioTranscriptionOptions options = ElevenLabsAudioTranscriptionOptions.builder().build();

		assertThat(options.getModelId()).isNull();
		assertThat(options.getLanguageCode()).isNull();
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getDiarize()).isNull();
	}

	@Test
	void testBuilderWithValues() {
		ElevenLabsAudioTranscriptionOptions options = ElevenLabsAudioTranscriptionOptions.builder()
			.modelId("scribe_v1")
			.languageCode("en")
			.temperature(0.5f)
			.diarize(true)
			.numSpeakers(2)
			.timestampsGranularity(ElevenLabsSpeechToTextApi.TimestampsGranularity.WORD)
			.tagAudioEvents(true)
			.build();

		assertThat(options.getModelId()).isEqualTo("scribe_v1");
		assertThat(options.getModel()).isEqualTo("scribe_v1");
		assertThat(options.getLanguageCode()).isEqualTo("en");
		assertThat(options.getTemperature()).isEqualTo(0.5f);
		assertThat(options.getDiarize()).isTrue();
		assertThat(options.getNumSpeakers()).isEqualTo(2);
		assertThat(options.getTimestampsGranularity()).isEqualTo(ElevenLabsSpeechToTextApi.TimestampsGranularity.WORD);
		assertThat(options.getTagAudioEvents()).isTrue();
	}

	@Test
	void testCopy() {
		ElevenLabsAudioTranscriptionOptions original = ElevenLabsAudioTranscriptionOptions.builder()
			.modelId("scribe_v1")
			.languageCode("en")
			.temperature(0.7f)
			.diarize(true)
			.build();

		ElevenLabsAudioTranscriptionOptions copy = original.copy();

		assertThat(copy).isNotSameAs(original);
		assertThat(copy.getModelId()).isEqualTo(original.getModelId());
		assertThat(copy.getLanguageCode()).isEqualTo(original.getLanguageCode());
		assertThat(copy.getTemperature()).isEqualTo(original.getTemperature());
		assertThat(copy.getDiarize()).isEqualTo(original.getDiarize());
	}

	@Test
	void testEqualsAndHashCode() {
		ElevenLabsAudioTranscriptionOptions options1 = ElevenLabsAudioTranscriptionOptions.builder()
			.modelId("scribe_v1")
			.languageCode("en")
			.build();

		ElevenLabsAudioTranscriptionOptions options2 = ElevenLabsAudioTranscriptionOptions.builder()
			.modelId("scribe_v1")
			.languageCode("en")
			.build();

		assertThat(options1).isEqualTo(options2);
		assertThat(options1.hashCode()).isEqualTo(options2.hashCode());
	}

	@Test
	void testWebhookOptions() {
		ElevenLabsAudioTranscriptionOptions options = ElevenLabsAudioTranscriptionOptions.builder()
			.webhook(true)
			.webhookId("my-webhook")
			.build();

		assertThat(options.getWebhook()).isTrue();
		assertThat(options.getWebhookId()).isEqualTo("my-webhook");
	}

	@Test
	void testCloudStorageUrl() {
		ElevenLabsAudioTranscriptionOptions options = ElevenLabsAudioTranscriptionOptions.builder()
			.cloudStorageUrl("https://example.com/audio.mp3")
			.build();

		assertThat(options.getCloudStorageUrl()).isEqualTo("https://example.com/audio.mp3");
	}

}
