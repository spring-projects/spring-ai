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

package org.springframework.ai.audio.transcription;

import java.time.Duration;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * A timestamped segment of an audio transcription.
 *
 * @param id the provider-assigned segment identifier, if available
 * @param speaker the identified speaker, if available
 * @param start the start offset of the segment
 * @param end the end offset of the segment
 * @param text the transcribed text of the segment
 * @since 2.0.1
 */
public record AudioTranscriptionSegment(@Nullable String id, @Nullable String speaker, Duration start, Duration end,
		String text) {

	public AudioTranscriptionSegment {
		Assert.notNull(start, "Start must not be null");
		Assert.notNull(end, "End must not be null");
		Assert.notNull(text, "Text must not be null");
	}

}
