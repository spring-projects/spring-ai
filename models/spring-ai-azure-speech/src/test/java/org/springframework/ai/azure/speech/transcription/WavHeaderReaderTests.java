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

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link WavHeaderReader}.
 *
 * @author Spring AI Contributors
 */
class WavHeaderReaderTests {

	@Test
	void acceptsMonoSixteenBitPcmAtBothSupportedRates() throws IOException {
		assertThat(read(TestWav.pcm(16000, 1, 16, 64)).sampleRate()).isEqualTo(16000);
		assertThat(read(TestWav.pcm(8000, 1, 16, 64)).sampleRate()).isEqualTo(8000);
	}

	@Test
	void acceptsTheGeneratedToneFixtureTheLiveSmokeTestSends() throws IOException {
		byte[] fixture = TestWav.tonesAndSilence();

		WavHeader header = read(fixture);

		assertThat(header.sampleRate()).isEqualTo(TestWav.TONES_SAMPLE_RATE);
		// 3.6 seconds of 16 kHz mono 16-bit frames, and a header that declares all of
		// them, so a live run cannot fail on a fixture this builder got wrong.
		assertThat(header.declaredDataBytes()).isEqualTo(3600L * TestWav.TONES_SAMPLE_RATE * 2 / 1000);
		assertThat(fixture).hasSize((int) header.declaredDataBytes() + 44);
	}

	@Test
	void acceptsExtensiblePcm() throws IOException {
		WavHeader header = read(TestWav.extensible(16000, TestWav.PCM_SUBFORMAT, 32));

		assertThat(header.sampleRate()).isEqualTo(16000);
		assertThat(header.declaredDataBytes()).isEqualTo(32L);
	}

	@Test
	void acceptsAnOddSizedFmtChunkBySkippingItsRiffPadByte() throws IOException {
		// A 17-byte fmt chunk fits entirely in the read buffer, so nothing is left to
		// skip, yet RIFF still pads it; missing that byte misaligns the data header.
		WavHeader header = read(TestWav.oddSizedFmtChunk(16000, 64));

		assertThat(header.sampleRate()).isEqualTo(16000);
		assertThat(header.declaredDataBytes()).isEqualTo(64L);
	}

	@Test
	void rejectsExtensibleWithANonPcmSubformat() {
		// The IEEE-float subtype, {00000003-0000-0010-8000-00AA00389B71}.
		byte[] ieeeFloat = TestWav.subFormatGuid(0x00000003, 0x71);

		assertThatIllegalArgumentException().isThrownBy(() -> read(TestWav.extensible(16000, ieeeFloat, 32)))
			.withMessageContaining("extensible subformat 00000003-0000-0010-8000-00aa00389b71");
	}

	@Test
	void rejectsAnExtensibleSubtypeThatOnlySharesThePcmFormatTag() {
		// Only the leading field matches PCM; the remaining 12 bytes of the GUID make
		// this a different subtype, whose payload the service cannot decode.
		byte[] vendorSubtype = TestWav.subFormatGuid(0x00000001, 0x72);

		assertThatIllegalArgumentException().isThrownBy(() -> read(TestWav.extensible(16000, vendorSubtype, 32)))
			.withMessageContaining("extensible subformat 00000001-0000-0010-8000-00aa00389b72");
	}

	@Test
	void rejectsAFormatChunkOutsideTheParsedSizeRange() {
		assertThatIllegalArgumentException().isThrownBy(() -> read(TestWav.withFmtChunkSize(16000, 8, 32)))
			.withMessageContaining("the fmt chunk is 8 bytes, expected 16 to 40");
		assertThatIllegalArgumentException().isThrownBy(() -> read(TestWav.withFmtChunkSize(16000, 1000, 32)))
			.withMessageContaining("the fmt chunk is 1000 bytes, expected 16 to 40");
	}

	@Test
	void acceptsAFormatChunkAtBothEndsOfTheParsedSizeRange() throws IOException {
		assertThat(read(TestWav.withFmtChunkSize(16000, 16, 32)).sampleRate()).isEqualTo(16000);
		assertThat(read(TestWav.withFmtChunkSize(16000, 40, 32)).sampleRate()).isEqualTo(16000);
	}

	@Test
	void rejectsStereoEightBitAndUnsupportedRates() {
		assertThatIllegalArgumentException().isThrownBy(() -> read(TestWav.pcm(16000, 2, 16, 32)))
			.withMessageContaining("2 channels");
		assertThatIllegalArgumentException().isThrownBy(() -> read(TestWav.pcm(16000, 1, 8, 32)))
			.withMessageContaining("8 bits per sample");
		assertThatIllegalArgumentException().isThrownBy(() -> read(TestWav.pcm(44100, 1, 16, 32)))
			.withMessageContaining("44100 Hz");
	}

	@Test
	void rejectsNonRiffContent() {
		assertThatIllegalArgumentException().isThrownBy(() -> read("not a wav file at all".getBytes()))
			.withMessageContaining("not a RIFF/WAVE stream");
	}

	@Test
	void rejectsATruncatedHeader() {
		byte[] truncated = Arrays.copyOf(TestWav.pcm(16000, 1, 16, 32), 20);

		assertThatExceptionOfType(EOFException.class).isThrownBy(() -> read(truncated))
			.withMessageContaining("truncated");
	}

	@Test
	void rejectsAStreamWithNoDataChunk() {
		byte[] wav = TestWav.pcm(16000, 1, 16, 0);
		byte[] withoutData = Arrays.copyOf(wav, wav.length - 8);

		assertThatExceptionOfType(EOFException.class).isThrownBy(() -> read(withoutData));
	}

	@Test
	void walksPastAnOddLengthChunkBeforeTheFormatChunk() throws IOException {
		WavHeader header = read(TestWav.withLeadingChunk(16000, "LIST", 7, 48));

		assertThat(header.sampleRate()).isEqualTo(16000);
		assertThat(header.declaredDataBytes()).isEqualTo(48L);
	}

	@Test
	void reportsAnUnknownLengthForTheStreamingSentinelAndForZero() throws IOException {
		assertThat(read(TestWav.pcmWithDeclaredLength(16000, 0xFFFFFFFFL, 32)).hasDeclaredDataLength()).isFalse();
		assertThat(read(TestWav.pcmWithDeclaredLength(16000, 0L, 32)).hasDeclaredDataLength()).isFalse();
	}

	@Test
	void leavesTheStreamPositionedAtTheFirstPayloadByte() throws IOException {
		byte[] wav = TestWav.pcm(16000, 1, 16, 10);
		for (int i = 0; i < 10; i++) {
			wav[wav.length - 10 + i] = (byte) (i + 1);
		}
		InputStream in = new ByteArrayInputStream(wav);

		WavHeaderReader.read(in);

		assertThat(in.readAllBytes()).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
	}

	@Test
	void stopsScanningAfterTheBoundedHeaderLimit() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> read(TestWav.withLeadingChunk(16000, "JUNK", WavHeaderReader.MAX_HEADER_BYTES + 16, 8)))
			.withMessageContaining("no data chunk within the first");
	}

	@Test
	void noMessageNamesTheAudioSource() {
		String message = messageOf(() -> read(TestWav.pcm(44100, 2, 24, 32)));

		assertThat(message).doesNotContain("/").doesNotContain("file:").contains("Azure Speech requires");
	}

	private static WavHeader read(byte[] wav) throws IOException {
		return WavHeaderReader.read(new ByteArrayInputStream(wav));
	}

	private static String messageOf(ThrowingCall call) {
		try {
			call.run();
			throw new AssertionError("expected a rejection");
		}
		catch (Exception ex) {
			return String.valueOf(ex.getMessage());
		}
	}

	@FunctionalInterface
	private interface ThrowingCall {

		void run() throws Exception;

	}

}
