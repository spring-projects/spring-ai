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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline checks on the committed audio fixture the live tests assert against.
 * <p>
 * {@link AzureSpeechTranscriptionModelIT} claims that the service returns at least two
 * ordered final segments for this file. That claim is only about the model if the file
 * really is 16-bit mono PCM the reader accepts, and really does contain several stretches
 * of signal separated by pauses long enough for the service to end an utterance. Both are
 * properties of the bytes, so both are checked here rather than against the service,
 * where a fixture problem would look like a model defect.
 * <p>
 * Properties of the bytes are all these checks reach. Loud windows separated by quiet
 * ones are not proof of intelligible speech, and the pause length used here comes from
 * Microsoft's documented <em>typical</em> segmentation silence, which the service is free
 * to depart from. Whether this audio transcribes, and into how many segments, is only
 * answered by a live run.
 *
 * @author Spring AI Contributors
 */
class MultiUtteranceSampleTests {

	/**
	 * Samples quieter than this fraction of the loudest 20 ms window count as silence.
	 * High enough to ignore room noise in a real recording, low enough that speech never
	 * falls below it.
	 */
	private static final double SILENCE_FRACTION_OF_PEAK = 0.04;

	private static final double WINDOW_SECONDS = 0.02;

	/**
	 * Roughly half a second of silence is the documented typical point at which Azure
	 * ends an utterance, so a pause has to clear that to be counted here.
	 */
	private static final double UTTERANCE_GAP_SECONDS = 0.6;

	/**
	 * The reader rejects anything that is not single-channel 16-bit PCM at 8 or 16 kHz,
	 * so reading without an exception is what proves those three properties; only the
	 * sample rate and the declared payload length are left to assert.
	 */
	@Test
	void theFixtureIsAFormatTheReaderAccepts() throws IOException {
		try (InputStream in = sample()) {
			WavHeader header = WavHeaderReader.read(in);

			assertThat(header.sampleRate()).isEqualTo(16_000);
			assertThat(header.hasDeclaredDataLength()).isTrue();
			assertThat(header.declaredDataBytes()).isEqualTo(in.readAllBytes().length);
		}
	}

	@Test
	void theFixtureHoldsSeveralUtterancesSeparatedByPauses() throws IOException {
		short[] samples = samples();
		List<double[]> utterances = utterancesIn(samples);

		assertThat(utterances).as("stretches of audio separated by at least %s s of silence", UTTERANCE_GAP_SECONDS)
			.hasSizeGreaterThanOrEqualTo(2);
		assertThat(samples.length / 16_000.0).as("duration in seconds").isBetween(5.0, 60.0);
		assertThat(utterances).allSatisfy(utterance -> assertThat(utterance[1] - utterance[0])
			.as("duration of an utterance starting at %s s", utterance[0])
			.isGreaterThan(0.2));
	}

	/**
	 * Returns the fixture's {@code data} payload as signed 16-bit samples, by reading the
	 * header with the production reader and then draining what it left behind.
	 */
	private static short[] samples() throws IOException {
		try (InputStream in = sample()) {
			WavHeaderReader.read(in);
			byte[] data = in.readAllBytes();
			ShortBuffer shorts = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
			short[] samples = new short[shorts.remaining()];
			shorts.get(samples);
			return samples;
		}
	}

	/**
	 * Returns the {@code [start, end]} second-offsets of each stretch of audio that is
	 * separated from the next by {@link #UTTERANCE_GAP_SECONDS} of silence.
	 */
	private static List<double[]> utterancesIn(short[] samples) {
		int window = (int) (WINDOW_SECONDS * 16_000);
		double[] loudness = new double[samples.length / window];
		for (int i = 0; i < loudness.length; i++) {
			double sum = 0;
			for (int j = i * window; j < (i + 1) * window; j++) {
				sum += (double) samples[j] * samples[j];
			}
			loudness[i] = Math.sqrt(sum / window);
		}
		double peak = 0;
		for (double value : loudness) {
			peak = Math.max(peak, value);
		}
		double threshold = peak * SILENCE_FRACTION_OF_PEAK;

		List<double[]> utterances = new ArrayList<>();
		int gapWindows = (int) Math.ceil(UTTERANCE_GAP_SECONDS / WINDOW_SECONDS);
		int silent = gapWindows;
		for (int i = 0; i < loudness.length; i++) {
			if (loudness[i] < threshold) {
				silent++;
				continue;
			}
			double at = i * WINDOW_SECONDS;
			if (silent >= gapWindows) {
				utterances.add(new double[] { at, at + WINDOW_SECONDS });
			}
			else {
				utterances.get(utterances.size() - 1)[1] = at + WINDOW_SECONDS;
			}
			silent = 0;
		}
		return utterances;
	}

	private static InputStream sample() throws IOException {
		return new ClassPathResource(AzureSpeechTranscriptionModelIT.SAMPLE).getInputStream();
	}

}
