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

package org.springframework.ai.openai.metadata;

import com.openai.core.http.Headers;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.audio.tts.TextToSpeechResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyRateLimit;
import org.springframework.ai.chat.metadata.RateLimit;

/**
 * Audio speech metadata implementation for OpenAI using the OpenAI Java SDK.
 *
 * @author Ahmed Yousri
 * @author Ilayaperumal Gopinathan
 */
public class OpenAiAudioSpeechResponseMetadata extends TextToSpeechResponseMetadata {

	public static final OpenAiAudioSpeechResponseMetadata NULL = new OpenAiAudioSpeechResponseMetadata();

	protected static final String AI_METADATA_STRING = "{ @type: %1$s, rateLimit: %2$s }";

	private final @Nullable RateLimit rateLimit;

	public OpenAiAudioSpeechResponseMetadata() {
		this(null);
	}

	public OpenAiAudioSpeechResponseMetadata(@Nullable RateLimit rateLimit) {
		this.rateLimit = rateLimit;
	}

	public static OpenAiAudioSpeechResponseMetadata from(Headers headers) {
		return new OpenAiAudioSpeechResponseMetadata(OpenAiRateLimit.from(headers));
	}

	public @Nullable RateLimit getRateLimit() {
		RateLimit rateLimit = this.rateLimit;
		return rateLimit != null ? rateLimit : new EmptyRateLimit();
	}

	@Override
	public String toString() {
		return AI_METADATA_STRING.formatted(getClass().getName(), getRateLimit());
	}

}
