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

package org.springframework.ai.azure.speech.transcription;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Live tests for {@link AzureSpeechTranscriptionModel} against an Azure AI Speech
 * resource.
 * <p>
 * Every test is skipped unless both {@code AZURE_SPEECH_KEY} and
 * {@code AZURE_SPEECH_REGION} are set. No further variable is needed: the audio these
 * tests assert on is the committed {@value #SAMPLE} fixture, a public-domain reading
 * whose three stretches of speech are separated by pauses of 0.86 s and 2.66 s, which is
 * what makes "one response per final segment, in order" a claim a live run can actually
 * falsify. {@link MultiUtteranceSampleTests} checks that fixture's format and its
 * pause-separated shape offline. That is a check on the bytes only — it cannot show that
 * the audio is intelligible or that the service will segment it as expected — so a
 * failure here means the model, the service or that expectation, rather than a corrupt or
 * malformed file.
 *
 * @author Spring AI Contributors
 */
@EnabledIfEnvironmentVariable(named = "AZURE_SPEECH_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AZURE_SPEECH_REGION", matches = ".+")
class AzureSpeechTranscriptionModelIT {

	/**
	 * English speech with pauses long enough to end an utterance. See
	 * {@code src/test/resources/speech/README.adoc} for where the audio comes from.
	 */
	static final String SAMPLE = "/speech/multi-utterance-mono-16k.wav";

	private static final Duration TIMEOUT = Duration.ofMinutes(2);

	/**
	 * The fixture holds three stretches of speech separated by pauses of more than 0.6 s,
	 * and the service returns one final result per utterance. Two is asserted rather than
	 * three because Microsoft documents its segmentation silence as a typical default and
	 * warns that adjacent phrases can be combined; two still fails an implementation that
	 * keeps only the first result, or that returns nothing at all.
	 */
	private static final int MINIMUM_EXPECTED_SEGMENTS = 2;

	@Test
	void transcribesEveryUtteranceOfTheCommittedSample() {
		AzureSpeechTranscriptionModel model = model(
				options().outputFormat(AzureSpeechAudioTranscriptionOptions.OutputFormat.DETAILED)
					.wordLevelTimestamps(true)
					.build());

		AudioTranscriptionResponse response = model.call(new AudioTranscriptionPrompt(sample()));

		assertThat(response.getResult().getOutput()).isNotBlank();
		List<AzureSpeechSegment> segments = segmentsOf(response);
		assertThat(segments).hasSizeGreaterThanOrEqualTo(MINIMUM_EXPECTED_SEGMENTS);
		assertThat(segments).allSatisfy(segment -> {
			assertThat(segment.text()).isNotBlank();
			assertThat(segment.duration()).isPositive();
			assertThat(segment.detailedJson()).isNotBlank();
		});
		assertOffsetsStrictlyIncrease(segments.stream().map(AzureSpeechSegment::offset).toList());
		assertThat(segments.get(0).words()).isNotEmpty();
		assertThat(String.join(" ", segments.stream().map(AzureSpeechSegment::text).toList()))
			.isEqualTo(response.getResult().getOutput());

		assertThat((String) response.getMetadata().get(AzureSpeechAudioTranscriptionMetadata.PROVIDER))
			.isEqualTo("azure_speech");
		assertThat((Integer) response.getMetadata().get(AzureSpeechAudioTranscriptionMetadata.SEGMENT_COUNT))
			.isEqualTo(segments.size());
		assertThat((Duration) response.getMetadata().get(AzureSpeechAudioTranscriptionMetadata.AUDIO_OFFSET))
			.isEqualTo(segments.get(0).offset());
		assertThat((Duration) response.getMetadata().get(AzureSpeechAudioTranscriptionMetadata.AUDIO_DURATION))
			.isPositive();
		assertThat((Integer) response.getMetadata().get(AzureSpeechAudioTranscriptionMetadata.NO_MATCH_COUNT))
			.isNotNegative();
	}

	@Test
	void streamsOneResponsePerUtteranceOfTheCommittedSample() {
		AzureSpeechTranscriptionModel model = model(options().build());

		List<AudioTranscriptionResponse> responses = model.stream(new AudioTranscriptionPrompt(sample()))
			.collectList()
			.block(TIMEOUT);

		assertThat(responses).isNotNull().hasSizeGreaterThanOrEqualTo(MINIMUM_EXPECTED_SEGMENTS);
		assertThat(responses).allSatisfy(response -> {
			List<AzureSpeechSegment> segments = segmentsOf(response);
			assertThat(segments).hasSize(1);
			assertThat(segments.get(0).text()).isNotBlank().isEqualTo(response.getResult().getOutput());
		});
		assertOffsetsStrictlyIncrease(
				responses.stream().map(response -> segmentsOf(response).get(0).offset()).toList());
	}

	/**
	 * A smoke test over audio generated in the test sources rather than read from a file:
	 * tones and silence that the service is not expected to recognize as anything. It
	 * asserts only the shape of the response, and exists because it still exercises
	 * credentials, the platform native libraries, the pull stream, continuous
	 * recognition, the deadline and release — the whole live path — against input that no
	 * fixture has to be shipped for.
	 */
	@Test
	void returnsAWellFormedResponseForUnrecognizableAudio() {
		AzureSpeechTranscriptionModel model = model(options().build());

		AudioTranscriptionResponse response = model.call(new AudioTranscriptionPrompt(generatedTones()));

		assertThat(response.getResult().getOutput()).isNotNull();
		List<AzureSpeechSegment> segments = segmentsOf(response);
		assertThat(String.join(" ", segments.stream().map(AzureSpeechSegment::text).toList()))
			.isEqualTo(response.getResult().getOutput());
		assertThat((Integer) response.getMetadata().get(AzureSpeechAudioTranscriptionMetadata.SEGMENT_COUNT))
			.isEqualTo(segments.size());
		assertThat((Integer) response.getMetadata().get(AzureSpeechAudioTranscriptionMetadata.NO_MATCH_COUNT))
			.isNotNegative();
	}

	@Test
	void reportsAuthenticationFailureWithoutRepeatingTheKey() {
		String invalidKey = "0123456789abcdef0123456789abcdef";
		AzureSpeechTranscriptionModel model = AzureSpeechTranscriptionModel.builder()
			.speechConfigFactory(new DefaultAzureSpeechConfigFactory(AzureSpeechConnectionDetails.builder()
				.apiKey(invalidKey)
				.region(System.getenv("AZURE_SPEECH_REGION"))
				.build()))
			.defaultOptions(options().build())
			.build();

		assertThatExceptionOfType(AzureSpeechTranscriptionException.class)
			.isThrownBy(() -> model.call(new AudioTranscriptionPrompt(sample())))
			.satisfies(ex -> {
				assertThat(ex.getMessage()).doesNotContain(invalidKey);
				assertThat(ex.getCancellationReason()).isEqualTo("Error");
				assertThat(ex.getErrorCode()).isNotNull();
			});
	}

	/**
	 * Asserts that each segment starts strictly later than the one before it. Strictly,
	 * not merely in order: the fixture's utterances are separated by pauses, so no two
	 * final results can legitimately share an offset, and requiring that rules out a run
	 * that reaches the expected count by republishing one result twice.
	 */
	private static void assertOffsetsStrictlyIncrease(List<Duration> offsets) {
		for (int i = 1; i < offsets.size(); i++) {
			assertThat(offsets.get(i)).as("offset of segment %s", i).isGreaterThan(offsets.get(i - 1));
		}
	}

	private static AzureSpeechAudioTranscriptionOptions.Builder options() {
		return AzureSpeechAudioTranscriptionOptions.builder().language("en-US").timeout(TIMEOUT);
	}

	private static AzureSpeechTranscriptionModel model(AzureSpeechAudioTranscriptionOptions defaultOptions) {
		return AzureSpeechTranscriptionModel.builder()
			.speechConfigFactory(new DefaultAzureSpeechConfigFactory(AzureSpeechConnectionDetails.builder()
				.apiKey(System.getenv("AZURE_SPEECH_KEY"))
				.region(System.getenv("AZURE_SPEECH_REGION"))
				.build()))
			.defaultOptions(defaultOptions)
			.build();
	}

	private static List<AzureSpeechSegment> segmentsOf(AudioTranscriptionResponse response) {
		assertThat(response.getResult().getMetadata()).isInstanceOf(AzureSpeechAudioTranscriptionMetadata.class);
		return ((AzureSpeechAudioTranscriptionMetadata) response.getResult().getMetadata()).getSegments();
	}

	private static Resource sample() {
		return new ClassPathResource(SAMPLE);
	}

	private static Resource generatedTones() {
		return new ByteArrayResource(TestWav.tonesAndSilence());
	}

}
