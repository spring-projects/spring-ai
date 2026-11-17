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

import org.springframework.util.Assert;

/**
 * One word of a recognized segment, with its position in the audio.
 * <p>
 * Present only when word-level timestamps were requested.
 *
 * @param text the recognized word
 * @param offset the word's start, measured from the beginning of the audio
 * @param duration how long the word spans
 * @author Spring AI Contributors
 * @since 2.1.0
 */
public record AzureSpeechWord(String text, Duration offset, Duration duration) {

	public AzureSpeechWord {
		Assert.notNull(text, "text must not be null");
		Assert.notNull(offset, "offset must not be null");
		Assert.notNull(duration, "duration must not be null");
	}

}
