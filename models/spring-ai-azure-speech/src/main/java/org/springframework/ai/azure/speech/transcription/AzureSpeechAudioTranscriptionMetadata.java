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

import java.util.List;

import org.springframework.ai.audio.transcription.AudioTranscriptionMetadata;
import org.springframework.util.Assert;

/**
 * Per-result metadata for an Azure Speech transcription, carrying the ordered final
 * segments the session produced.
 * <p>
 * The constants on this type are the keys the model writes into the
 * {@link org.springframework.ai.audio.transcription.AudioTranscriptionResponseMetadata}.
 * None of them, and none of the segment fields, ever carries the API key, an
 * authorization token, a credential-bearing URL, or the audio resource path.
 *
 * @author Spring AI Contributors
 * @since 2.1.0
 */
public class AzureSpeechAudioTranscriptionMetadata implements AudioTranscriptionMetadata {

	/**
	 * Response metadata key for the provider identifier.
	 */
	public static final String PROVIDER = "azure-speech.provider";

	/**
	 * Response metadata key for the resolved model label.
	 */
	public static final String MODEL = "azure-speech.model";

	/**
	 * Response metadata key for the first segment's offset.
	 */
	public static final String AUDIO_OFFSET = "azure-speech.audio-offset";

	/**
	 * Response metadata key for the span from the first segment's offset to the last
	 * segment's end.
	 */
	public static final String AUDIO_DURATION = "azure-speech.audio-duration";

	/**
	 * Response metadata key for the number of final segments.
	 */
	public static final String SEGMENT_COUNT = "azure-speech.segment-count";

	/**
	 * Response metadata key for the number of {@code NoMatch} results.
	 */
	public static final String NO_MATCH_COUNT = "azure-speech.no-match-count";

	/**
	 * Response metadata key for the Custom Speech endpoint ID, written only when one was
	 * configured.
	 */
	public static final String ENDPOINT_ID = "azure-speech.endpoint-id";

	/**
	 * Response metadata key for the ordered distinct detected languages, written only
	 * when language identification ran.
	 */
	public static final String DETECTED_LANGUAGES = "azure-speech.detected-languages";

	private final List<AzureSpeechSegment> segments;

	public AzureSpeechAudioTranscriptionMetadata(List<AzureSpeechSegment> segments) {
		Assert.notNull(segments, "segments must not be null");
		this.segments = List.copyOf(segments);
	}

	/**
	 * Returns the final segments, in recognition order.
	 */
	public List<AzureSpeechSegment> getSegments() {
		return this.segments;
	}

	@Override
	public String toString() {
		return "AzureSpeechAudioTranscriptionMetadata{segmentCount=" + this.segments.size() + '}';
	}

}
