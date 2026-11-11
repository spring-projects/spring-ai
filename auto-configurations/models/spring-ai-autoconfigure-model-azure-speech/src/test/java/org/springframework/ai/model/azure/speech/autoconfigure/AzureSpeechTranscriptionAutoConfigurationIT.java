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

package org.springframework.ai.model.azure.speech.autoconfigure;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.azure.speech.transcription.AzureSpeechAudioTranscriptionMetadata;
import org.springframework.ai.azure.speech.transcription.AzureSpeechTranscriptionModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live tests for {@link AzureSpeechTranscriptionAutoConfiguration}, verifying that the
 * auto-configured model reaches a real Azure AI Speech resource with nothing but
 * properties.
 * <p>
 * Skipped unless both {@code AZURE_SPEECH_KEY} and {@code AZURE_SPEECH_REGION} are set.
 * As in {@code AzureSpeechTranscriptionModelIT}, the audio is generated here: it carries
 * no recognizable speech, so the transcript is expected to be empty, and what the test
 * proves is that the auto-configured factory, credentials, options and native libraries
 * carry one continuous session through to its end of stream.
 *
 * @author Spring AI Contributors
 */
@EnabledIfEnvironmentVariable(named = "AZURE_SPEECH_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AZURE_SPEECH_REGION", matches = ".+")
class AzureSpeechTranscriptionAutoConfigurationIT {

	private static final int SAMPLE_RATE = 16000;

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(AzureSpeechTranscriptionAutoConfiguration.class))
		.withPropertyValues("spring.ai.model.audio.transcription=azure-speech",
				"spring.ai.azure.speech.api-key=" + System.getenv("AZURE_SPEECH_KEY"),
				"spring.ai.azure.speech.region=" + System.getenv("AZURE_SPEECH_REGION"),
				"spring.ai.azure.speech.transcription.language=en-US",
				"spring.ai.azure.speech.transcription.timeout=2m");

	@Test
	void autoConfiguredModelTranscribesGeneratedAudio() {
		this.contextRunner.run(context -> {
			AzureSpeechTranscriptionModel model = context.getBean(AzureSpeechTranscriptionModel.class);

			AudioTranscriptionResponse response = model.call(new AudioTranscriptionPrompt(generatedAudio()));

			assertThat(response.getResult().getOutput()).isNotNull();
			assertThat((String) response.getMetadata().get(AzureSpeechAudioTranscriptionMetadata.PROVIDER))
				.isEqualTo("azure_speech");
		});
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "AZURE_SPEECH_SAMPLE_WAV", matches = ".+")
	void autoConfiguredModelTranscribesSuppliedSpeechSample() {
		this.contextRunner.run(context -> {
			AzureSpeechTranscriptionModel model = context.getBean(AzureSpeechTranscriptionModel.class);

			AudioTranscriptionResponse response = model
				.call(new AudioTranscriptionPrompt(new FileSystemResource(System.getenv("AZURE_SPEECH_SAMPLE_WAV"))));

			assertThat(response.getResult().getOutput()).isNotBlank();
		});
	}

	/**
	 * Builds two one-second utterance slots separated and surrounded by silence, as 16
	 * kHz mono 16-bit PCM. The slots carry a quiet tone rather than speech; see
	 * {@code AzureSpeechTranscriptionModelIT} for why no recording is committed.
	 */
	private static Resource generatedAudio() {
		ByteArrayOutputStream samples = new ByteArrayOutputStream();
		appendSilence(samples, Duration.ofMillis(300));
		appendTone(samples, Duration.ofSeconds(1));
		appendSilence(samples, Duration.ofSeconds(1));
		appendTone(samples, Duration.ofSeconds(1));
		appendSilence(samples, Duration.ofMillis(300));
		return new ByteArrayResource(wav(samples.toByteArray()));
	}

	private static void appendSilence(ByteArrayOutputStream out, Duration length) {
		out.writeBytes(new byte[frames(length) * 2]);
	}

	private static void appendTone(ByteArrayOutputStream out, Duration length) {
		int frames = frames(length);
		ByteBuffer tone = ByteBuffer.allocate(frames * 2).order(ByteOrder.LITTLE_ENDIAN);
		for (int frame = 0; frame < frames; frame++) {
			tone.putShort((short) (Math.sin(2 * Math.PI * 220.0 * frame / SAMPLE_RATE) * 3000));
		}
		out.writeBytes(tone.array());
	}

	private static int frames(Duration length) {
		return (int) (length.toMillis() * SAMPLE_RATE / 1000);
	}

	private static byte[] wav(byte[] pcm) {
		ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
		header.put("RIFF".getBytes(StandardCharsets.US_ASCII));
		header.putInt(36 + pcm.length);
		header.put("WAVE".getBytes(StandardCharsets.US_ASCII));
		header.put("fmt ".getBytes(StandardCharsets.US_ASCII));
		header.putInt(16);
		header.putShort((short) 1);
		header.putShort((short) 1);
		header.putInt(SAMPLE_RATE);
		header.putInt(SAMPLE_RATE * 2);
		header.putShort((short) 2);
		header.putShort((short) 16);
		header.put("data".getBytes(StandardCharsets.US_ASCII));
		header.putInt(pcm.length);
		ByteArrayOutputStream out = new ByteArrayOutputStream(44 + pcm.length);
		out.writeBytes(header.array());
		out.writeBytes(pcm);
		return out.toByteArray();
	}

}
