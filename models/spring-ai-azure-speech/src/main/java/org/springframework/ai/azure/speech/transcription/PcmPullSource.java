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

/**
 * Serves the WAV file's PCM payload to the SDK, counting what it delivers and failing the
 * operation rather than overrunning the configured size guard.
 * <p>
 * Both the declared check made from the header and the counting done here measure the
 * same thing — PCM payload bytes — so the two can never disagree about what they are
 * measuring, only about when the number is known.
 * <p>
 * Once a failure has been reported the source returns end-of-stream on every subsequent
 * read: the operation is already decided, and the SDK only needs to be told the audio is
 * over.
 *
 * @author Spring AI Contributors
 */
final class PcmPullSource implements PcmAudioSource {

	private final InputStream source;

	private final RecognitionRun run;

	private final Runnable closeAction;

	private final long maxBytes;

	/**
	 * Bytes still to deliver according to the header, or
	 * {@link WavHeader#UNKNOWN_DATA_LENGTH} when the header did not state a length.
	 */
	private long declaredRemaining;

	private long delivered;

	private volatile boolean failed;

	PcmPullSource(InputStream source, WavHeader header, long maxBytes, RecognitionRun run, Runnable closeAction) {
		this.source = source;
		this.run = run;
		this.closeAction = closeAction;
		this.maxBytes = maxBytes;
		this.declaredRemaining = header.declaredDataBytes();
	}

	@Override
	public int read(byte[] buffer) {
		if (this.failed) {
			return 0;
		}
		int want = buffer.length;
		if (this.declaredRemaining != WavHeader.UNKNOWN_DATA_LENGTH) {
			if (this.declaredRemaining <= 0) {
				return 0;
			}
			want = (int) Math.min(want, this.declaredRemaining);
		}
		int read;
		try {
			read = this.source.read(buffer, 0, want);
		}
		catch (IOException | RuntimeException ex) {
			return fail(AzureSpeechTranscriptionException.audioReadFailure("Failed to read the audio payload",
					ex.getClass().getName()));
		}
		if (read <= 0) {
			return 0;
		}
		if (this.delivered + read > this.maxBytes) {
			return fail(AzureSpeechTranscriptionException.audioTooLarge(this.maxBytes));
		}
		this.delivered += read;
		if (this.declaredRemaining != WavHeader.UNKNOWN_DATA_LENGTH) {
			this.declaredRemaining -= read;
		}
		return read;
	}

	private int fail(AzureSpeechTranscriptionException error) {
		this.failed = true;
		// Signalling end-of-stream to the SDK is all this thread does about it; the run
		// reserves any stop it needs here and submits it from another thread.
		this.run.onPullFailure(error);
		return 0;
	}

	/**
	 * Routes the SDK's own disposal of the pull stream to the audio source's single
	 * idempotent close, so the stream is never closed twice and never missed.
	 */
	@Override
	public void close() {
		this.closeAction.run();
	}

	long deliveredBytes() {
		return this.delivered;
	}

}
