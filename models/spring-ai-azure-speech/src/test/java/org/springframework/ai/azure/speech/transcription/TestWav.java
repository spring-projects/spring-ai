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

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import org.jspecify.annotations.Nullable;

/**
 * Builds small in-memory WAV files so header tests never depend on a fixture file.
 *
 * @author Spring AI Contributors
 */
final class TestWav {

	private TestWav() {
	}

	static byte[] pcm(int sampleRate, int channels, int bitsPerSample, int payloadBytes) {
		return build(sampleRate, channels, bitsPerSample, payloadBytes, payloadBytes, 1, 16, new byte[0]);
	}

	static byte[] pcmWithDeclaredLength(int sampleRate, long declaredLength, int payloadBytes) {
		return build(sampleRate, 1, 16, payloadBytes, declaredLength, 1, 16, new byte[0]);
	}

	/**
	 * {@code KSDATAFORMAT_SUBTYPE_PCM}, {@code {00000001-0000-0010-8000-00AA00389B71}}.
	 */
	static final byte[] PCM_SUBFORMAT = subFormatGuid(0x00000001, 0x71);

	/**
	 * Builds an extensible {@code SubFormat} GUID from the two fields a test varies: the
	 * leading 32-bit field, which repeats the format tag, and the trailing byte. The
	 * fields between them are the ones every registered WAV subtype shares.
	 */
	static byte[] subFormatGuid(int leadingField, int trailingByte) {
		byte[] guid = new byte[16];
		ByteBuffer buffer = ByteBuffer.wrap(guid).order(ByteOrder.LITTLE_ENDIAN);
		buffer.putInt(leadingField);
		buffer.putShort((short) 0x0000);
		buffer.putShort((short) 0x0010);
		buffer.put(new byte[] { (byte) 0x80, 0x00, 0x00, (byte) 0xAA, 0x00, 0x38, (byte) 0x9B });
		buffer.put((byte) trailingByte);
		return guid;
	}

	static byte[] extensible(int sampleRate, byte[] subFormat, int payloadBytes) {
		byte[] extension = new byte[24];
		ByteBuffer buffer = ByteBuffer.wrap(extension).order(ByteOrder.LITTLE_ENDIAN);
		buffer.putShort((short) 22);
		buffer.putShort((short) 16);
		buffer.putInt(0x4);
		buffer.put(subFormat);
		return build(sampleRate, 1, 16, payloadBytes, payloadBytes, 0xFFFE, 40, extension);
	}

	/**
	 * Builds mono 16-bit PCM whose {@code fmt } chunk declares {@code fmtChunkSize}
	 * bytes, carrying the standard fields truncated or zero-padded to that size, so a
	 * test can drive the parser's accepted size range.
	 */
	static byte[] withFmtChunkSize(int sampleRate, int fmtChunkSize, int payloadBytes) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteBuffer header = ByteBuffer.allocate(12 + 8).order(ByteOrder.LITTLE_ENDIAN);
		header.put("RIFF".getBytes());
		header.putInt(4 + 8 + fmtChunkSize + 8 + payloadBytes);
		header.put("WAVE".getBytes());
		header.put("fmt ".getBytes());
		header.putInt(fmtChunkSize);
		out.writeBytes(header.array());
		ByteBuffer fmt = ByteBuffer.allocate(Math.max(fmtChunkSize, 16)).order(ByteOrder.LITTLE_ENDIAN);
		fmt.putShort((short) 1);
		fmt.putShort((short) 1);
		fmt.putInt(sampleRate);
		fmt.putInt(sampleRate * 2);
		fmt.putShort((short) 2);
		fmt.putShort((short) 16);
		out.writeBytes(Arrays.copyOf(fmt.array(), fmtChunkSize));
		ByteBuffer data = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
		data.put("data".getBytes());
		data.putInt(payloadBytes);
		out.writeBytes(data.array());
		out.writeBytes(new byte[payloadBytes]);
		return out.toByteArray();
	}

	static byte[] withLeadingChunk(int sampleRate, String chunkId, int chunkPayload, int payloadBytes) {
		byte[] extra = new byte[chunkPayload];
		return buildWithExtraChunk(sampleRate, payloadBytes, chunkId, extra);
	}

	/**
	 * Builds mono 16-bit PCM whose {@code fmt } chunk declares an odd size, so RIFF's
	 * one-byte pad to the next word boundary sits between {@code fmt } and {@code data}.
	 */
	static byte[] oddSizedFmtChunk(int sampleRate, int payloadBytes) {
		int fmtChunkSize = 17;
		int pad = 1;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteBuffer header = ByteBuffer.allocate(12 + 8 + fmtChunkSize + pad + 8).order(ByteOrder.LITTLE_ENDIAN);
		header.put("RIFF".getBytes());
		header.putInt(4 + 8 + fmtChunkSize + pad + 8 + payloadBytes);
		header.put("WAVE".getBytes());
		header.put("fmt ".getBytes());
		header.putInt(fmtChunkSize);
		header.putShort((short) 1);
		header.putShort((short) 1);
		header.putInt(sampleRate);
		header.putInt(sampleRate * 2);
		header.putShort((short) 2);
		header.putShort((short) 16);
		header.put((byte) 0);
		header.put((byte) 0);
		header.put("data".getBytes());
		header.putInt(payloadBytes);
		out.writeBytes(header.array());
		out.writeBytes(new byte[payloadBytes]);
		return out.toByteArray();
	}

	/**
	 * Sample rate the generated tone fixture is built at.
	 */
	static final int TONES_SAMPLE_RATE = 16000;

	/**
	 * Builds the unrecognizable audio {@code AzureSpeechTranscriptionModelIT} uses as a
	 * smoke test: two one-second slots of a quiet 220 Hz tone, separated and surrounded
	 * by silence, as 16 kHz mono 16-bit PCM. It is deliberately not speech — a live
	 * service is expected to recognize nothing in it — so it proves nothing about
	 * segmentation; what it provides is several seconds of real audio frames for one
	 * continuous session to run over, with no fixture to ship. The multi-utterance
	 * assertions use the committed recording instead, as
	 * {@code AzureSpeechTranscriptionModelIT#SAMPLE}.
	 */
	static byte[] tonesAndSilence() {
		ByteArrayOutputStream samples = new ByteArrayOutputStream();
		appendSilence(samples, 300);
		appendTone(samples, 1000);
		appendSilence(samples, 1000);
		appendTone(samples, 1000);
		appendSilence(samples, 300);
		byte[] pcm = samples.toByteArray();
		return build(TONES_SAMPLE_RATE, 1, 16, pcm.length, pcm.length, 1, 16, new byte[0], pcm);
	}

	private static void appendSilence(ByteArrayOutputStream out, int millis) {
		out.writeBytes(new byte[frames(millis) * 2]);
	}

	private static void appendTone(ByteArrayOutputStream out, int millis) {
		int frames = frames(millis);
		ByteBuffer tone = ByteBuffer.allocate(frames * 2).order(ByteOrder.LITTLE_ENDIAN);
		for (int frame = 0; frame < frames; frame++) {
			double angle = 2 * Math.PI * 220.0 * frame / TONES_SAMPLE_RATE;
			tone.putShort((short) (Math.sin(angle) * 3000));
		}
		out.writeBytes(tone.array());
	}

	private static int frames(int millis) {
		return millis * TONES_SAMPLE_RATE / 1000;
	}

	private static byte[] build(int sampleRate, int channels, int bitsPerSample, int payloadBytes, long declaredLength,
			int formatTag, int fmtChunkSize, byte[] fmtExtension) {
		return build(sampleRate, channels, bitsPerSample, payloadBytes, declaredLength, formatTag, fmtChunkSize,
				fmtExtension, null);
	}

	private static byte[] build(int sampleRate, int channels, int bitsPerSample, int payloadBytes, long declaredLength,
			int formatTag, int fmtChunkSize, byte[] fmtExtension, byte @Nullable [] payload) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int blockAlign = channels * bitsPerSample / 8;
		ByteBuffer header = ByteBuffer.allocate(12 + 8 + fmtChunkSize + 8).order(ByteOrder.LITTLE_ENDIAN);
		header.put("RIFF".getBytes());
		header.putInt(4 + 8 + fmtChunkSize + 8 + payloadBytes);
		header.put("WAVE".getBytes());
		header.put("fmt ".getBytes());
		header.putInt(fmtChunkSize);
		header.putShort((short) formatTag);
		header.putShort((short) channels);
		header.putInt(sampleRate);
		header.putInt(sampleRate * blockAlign);
		header.putShort((short) blockAlign);
		header.putShort((short) bitsPerSample);
		header.put(fmtExtension);
		header.put("data".getBytes());
		header.putInt((int) declaredLength);
		out.writeBytes(header.array());
		out.writeBytes((payload != null) ? payload : new byte[payloadBytes]);
		return out.toByteArray();
	}

	private static byte[] buildWithExtraChunk(int sampleRate, int payloadBytes, String chunkId, byte[] extra) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int fmtChunkSize = 16;
		int pad = extra.length % 2;
		ByteBuffer header = ByteBuffer.allocate(12 + 8 + extra.length + pad + 8 + fmtChunkSize + 8)
			.order(ByteOrder.LITTLE_ENDIAN);
		header.put("RIFF".getBytes());
		header.putInt(4 + 8 + extra.length + pad + 8 + fmtChunkSize + 8 + payloadBytes);
		header.put("WAVE".getBytes());
		header.put(chunkId.getBytes());
		header.putInt(extra.length);
		header.put(extra);
		if (pad == 1) {
			header.put((byte) 0);
		}
		header.put("fmt ".getBytes());
		header.putInt(fmtChunkSize);
		header.putShort((short) 1);
		header.putShort((short) 1);
		header.putInt(sampleRate);
		header.putInt(sampleRate * 2);
		header.putShort((short) 2);
		header.putShort((short) 16);
		header.put("data".getBytes());
		header.putInt(payloadBytes);
		out.writeBytes(header.array());
		out.writeBytes(new byte[payloadBytes]);
		return out.toByteArray();
	}

}
