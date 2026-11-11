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

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * One final recognition result from a continuous Azure Speech session.
 * <p>
 * Segment text is preserved exactly as the service returned it; the model never trims,
 * normalizes, or re-cases it.
 *
 * @param text the recognized text for this segment
 * @param resultId the service-assigned result identifier
 * @param offset the segment's start, measured from the beginning of the audio
 * @param duration how long the segment spans
 * @param language the detected language, only when language identification ran, otherwise
 * {@code null}
 * @param words the word-level timings, only when word-level timestamps were requested,
 * otherwise empty
 * @param detailedJson the raw detailed result JSON, only when detailed output was
 * requested, otherwise {@code null}
 * @author Spring AI Contributors
 * @since 2.1.0
 */
public record AzureSpeechSegment(String text, String resultId, Duration offset, Duration duration,
		@Nullable String language, List<AzureSpeechWord> words, @Nullable String detailedJson) {

	public AzureSpeechSegment {
		Assert.notNull(text, "text must not be null");
		Assert.notNull(resultId, "resultId must not be null");
		Assert.notNull(offset, "offset must not be null");
		Assert.notNull(duration, "duration must not be null");
		Assert.notNull(words, "words must not be null");
		words = List.copyOf(words);
	}

	/**
	 * Returns the instant immediately after this segment, measured from the beginning of
	 * the audio.
	 */
	public Duration end() {
		return this.offset.plus(this.duration);
	}

}
