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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Parses just enough of a WAV header to confirm the audio matches the PCM stream contract
 * the Azure Speech SDK documents, and to learn where the samples start.
 * <p>
 * The scan is bounded: at most {@link #MAX_HEADER_BYTES} are consumed before the
 * {@code data} chunk, the {@code fmt } chunk is read into a fixed 40-byte buffer, and no
 * allocation is ever made proportional to a chunk size the file declares. On return the
 * stream is positioned at the first PCM payload byte.
 * <p>
 * No message produced here contains the resource description, filename, or path: the
 * caller's audio location is not this class's to disclose.
 *
 * @author Spring AI Contributors
 */
final class WavHeaderReader {

	/**
	 * Upper bound on the bytes consumed before the {@code data} chunk's payload.
	 */
	static final int MAX_HEADER_BYTES = 64 * 1024;

	private static final int FORMAT_PCM = 0x0001;

	private static final int FORMAT_EXTENSIBLE = 0xFFFE;

	private static final int REQUIRED_CHANNELS = 1;

	private static final int REQUIRED_BITS_PER_SAMPLE = 16;

	private static final long STREAMING_DATA_LENGTH = 0xFFFFFFFFL;

	private static final int FMT_BUFFER_SIZE = 40;

	private static final int SUBFORMAT_OFFSET = 24;

	private static final int GUID_BYTES = 16;

	/**
	 * {@code KSDATAFORMAT_SUBTYPE_PCM}, {@code {00000001-0000-0010-8000-00AA00389B71}},
	 * laid out as the extensible {@code fmt } chunk stores it: the first three fields
	 * little-endian, then the final eight bytes in order.
	 */
	private static final byte[] PCM_SUBFORMAT = { 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x10, 0x00, (byte) 0x80, 0x00,
			0x00, (byte) 0xAA, 0x00, 0x38, (byte) 0x9B, 0x71 };

	private static final String REQUIREMENT = "Azure Speech requires 16-bit mono PCM WAV at 8 or 16 kHz";

	private WavHeaderReader() {
	}

	/**
	 * Reads the header from {@code in}, leaving it positioned at the first PCM payload
	 * byte.
	 * @param in the audio stream, consumed from its current position
	 * @return the sample rate and declared payload length
	 * @throws IllegalArgumentException if the content is not a WAV file the SDK accepts
	 * @throws IOException if the stream cannot be read
	 */
	static WavHeader read(InputStream in) throws IOException {
		Counter consumed = new Counter();
		byte[] riff = readFully(in, 12, consumed);
		if (!matches(riff, 0, "RIFF") || !matches(riff, 8, "WAVE")) {
			throw new AzureSpeechAudioFormatException(
					"Unsupported audio format: not a RIFF/WAVE stream; " + REQUIREMENT);
		}

		Integer sampleRate = null;
		while (true) {
			byte[] header = readFully(in, 8, consumed);
			String chunkId = new String(header, 0, 4, java.nio.charset.StandardCharsets.US_ASCII);
			long chunkSize = readUnsignedInt(header, 4);

			if ("fmt ".equals(chunkId)) {
				sampleRate = readFormatChunk(in, chunkSize, consumed);
				continue;
			}
			if ("data".equals(chunkId)) {
				if (sampleRate == null) {
					throw new AzureSpeechAudioFormatException(
							"Unsupported audio format: the data chunk precedes the fmt chunk; " + REQUIREMENT);
				}
				long declared = (chunkSize == 0L || chunkSize == STREAMING_DATA_LENGTH) ? WavHeader.UNKNOWN_DATA_LENGTH
						: chunkSize;
				return new WavHeader(sampleRate, declared);
			}
			skipChunk(in, chunkSize, consumed);
		}
	}

	private static Integer readFormatChunk(InputStream in, long chunkSize, Counter consumed) throws IOException {
		if (chunkSize < 16 || chunkSize > FMT_BUFFER_SIZE) {
			throw new AzureSpeechAudioFormatException("Unsupported audio format: the fmt chunk is " + chunkSize
					+ " bytes, expected 16 to " + FMT_BUFFER_SIZE + "; " + REQUIREMENT);
		}
		int toRead = (int) Math.min(chunkSize, FMT_BUFFER_SIZE);
		byte[] fmt = readFully(in, toRead, consumed);
		// The pad comes from the declared chunk size, not from what is left after the
		// buffered read: a short odd chunk leaves nothing to skip but is still padded.
		skipExactly(in, (chunkSize - toRead) + (chunkSize % 2), consumed);

		int formatTag = readUnsignedShort(fmt, 0);
		int channels = readUnsignedShort(fmt, 2);
		long sampleRate = readUnsignedInt(fmt, 4);
		int bitsPerSample = readUnsignedShort(fmt, 14);

		if (formatTag == FORMAT_EXTENSIBLE) {
			requireExtensiblePcm(fmt, chunkSize, toRead);
		}
		else if (formatTag != FORMAT_PCM) {
			throw new AzureSpeechAudioFormatException(
					"Unsupported audio format: format tag " + formatTag + " is not uncompressed PCM; " + REQUIREMENT);
		}
		if (channels != REQUIRED_CHANNELS) {
			throw new AzureSpeechAudioFormatException(
					"Unsupported audio format: " + channels + " channels; " + REQUIREMENT);
		}
		if (bitsPerSample != REQUIRED_BITS_PER_SAMPLE) {
			throw new AzureSpeechAudioFormatException(
					"Unsupported audio format: " + bitsPerSample + " bits per sample; " + REQUIREMENT);
		}
		if (sampleRate != 8000L && sampleRate != 16000L) {
			throw new AzureSpeechAudioFormatException(
					"Unsupported audio format: " + sampleRate + " Hz sample rate; " + REQUIREMENT);
		}
		return (int) sampleRate;
	}

	/**
	 * Requires the whole 16-byte {@code SubFormat} GUID to be the PCM subtype, not merely
	 * its leading format-tag field: a vendor subtype may repeat that field and still
	 * describe samples the service cannot decode.
	 */
	private static void requireExtensiblePcm(byte[] fmt, long chunkSize, int available) {
		if (chunkSize < FMT_BUFFER_SIZE || available < FMT_BUFFER_SIZE) {
			throw new AzureSpeechAudioFormatException("Unsupported audio format: an extensible fmt chunk of "
					+ chunkSize + " bytes carries no subformat; " + REQUIREMENT);
		}
		if (!Arrays.equals(fmt, SUBFORMAT_OFFSET, SUBFORMAT_OFFSET + GUID_BYTES, PCM_SUBFORMAT, 0, GUID_BYTES)) {
			throw new AzureSpeechAudioFormatException("Unsupported audio format: extensible subformat "
					+ formatSubFormat(fmt) + " is not uncompressed PCM; " + REQUIREMENT);
		}
	}

	/**
	 * Renders the {@code SubFormat} field in the canonical GUID text, so a rejection
	 * names the subtype the file actually declared.
	 */
	private static String formatSubFormat(byte[] fmt) {
		StringBuilder rendered = new StringBuilder(36);
		rendered.append(String.format("%08x-%04x-%04x-", readUnsignedInt(fmt, SUBFORMAT_OFFSET),
				readUnsignedShort(fmt, SUBFORMAT_OFFSET + 4), readUnsignedShort(fmt, SUBFORMAT_OFFSET + 6)));
		for (int i = 8; i < GUID_BYTES; i++) {
			if (i == 10) {
				rendered.append('-');
			}
			rendered.append(String.format("%02x", fmt[SUBFORMAT_OFFSET + i] & 0xFF));
		}
		return rendered.toString();
	}

	private static void skipChunk(InputStream in, long chunkSize, Counter consumed) throws IOException {
		// RIFF pads every odd-length chunk to an even boundary.
		skipExactly(in, chunkSize + (chunkSize % 2), consumed);
	}

	private static void skipExactly(InputStream in, long count, Counter consumed) throws IOException {
		long remaining = count;
		byte[] scratch = new byte[Math.min(8192, (int) Math.max(1, Math.min(remaining, 8192)))];
		while (remaining > 0) {
			int want = (int) Math.min(scratch.length, remaining);
			int read = in.read(scratch, 0, want);
			if (read < 0) {
				throw new AzureSpeechAudioFormatException(
						"Unsupported audio format: the header ends before the data chunk; " + REQUIREMENT);
			}
			remaining -= read;
			consumed.add(read);
		}
	}

	private static byte[] readFully(InputStream in, int length, Counter consumed) throws IOException {
		byte[] buffer = new byte[length];
		int offset = 0;
		while (offset < length) {
			int read = in.read(buffer, offset, length - offset);
			if (read < 0) {
				throw new EOFException("Unsupported audio format: the WAV header is truncated; " + REQUIREMENT);
			}
			offset += read;
		}
		consumed.add(length);
		return buffer;
	}

	private static boolean matches(byte[] buffer, int offset, String ascii) {
		for (int i = 0; i < ascii.length(); i++) {
			if (buffer[offset + i] != (byte) ascii.charAt(i)) {
				return false;
			}
		}
		return true;
	}

	private static int readUnsignedShort(byte[] buffer, int offset) {
		return (buffer[offset] & 0xFF) | ((buffer[offset + 1] & 0xFF) << 8);
	}

	private static long readUnsignedInt(byte[] buffer, int offset) {
		return (buffer[offset] & 0xFFL) | ((buffer[offset + 1] & 0xFFL) << 8) | ((buffer[offset + 2] & 0xFFL) << 16)
				| ((buffer[offset + 3] & 0xFFL) << 24);
	}

	/**
	 * Enforces the bounded-scan limit across every read made before the data payload.
	 */
	private static final class Counter {

		private long total;

		void add(long bytes) {
			this.total += bytes;
			if (this.total > MAX_HEADER_BYTES) {
				throw new AzureSpeechAudioFormatException("Unsupported audio format: no data chunk within the first "
						+ MAX_HEADER_BYTES + " bytes; " + REQUIREMENT);
			}
		}

	}

}
