/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.openai.metadata.audio;

import org.springframework.ai.audio.transcription.AudioTranscriptionResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyRateLimit;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.model.MutableResponseMetadata;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.metadata.OpenAiRateLimit;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Audio transcription metadata implementation for {@literal OpenAI}.
 *
 * @author Michael Lavelle
 * @author Piotr Olaszewski
 * @see RateLimit
 * @since 0.8.1
 */
public class OpenAiAudioTranscriptionResponseMetadata extends AudioTranscriptionResponseMetadata {

	protected static final String AI_METADATA_STRING = "{ @type: %1$s, rateLimit: %4$s }";

	public static final OpenAiAudioTranscriptionResponseMetadata NULL = new OpenAiAudioTranscriptionResponseMetadata() {
	};

	public static OpenAiAudioTranscriptionResponseMetadata from(OpenAiAudioApi.StructuredResponse result) {
		Assert.notNull(result, "OpenAI Transcription must not be null");
		return new OpenAiAudioTranscriptionResponseMetadata();
	}

	public static OpenAiAudioTranscriptionResponseMetadata from(String result) {
		Assert.notNull(result, "OpenAI Transcription must not be null");
		return new OpenAiAudioTranscriptionResponseMetadata();
	}

	@Nullable
	private RateLimit rateLimit;

	protected OpenAiAudioTranscriptionResponseMetadata() {
		this(null);
	}

	protected OpenAiAudioTranscriptionResponseMetadata(@Nullable OpenAiRateLimit rateLimit) {
		this.rateLimit = rateLimit;
	}

	@Nullable
	public RateLimit getRateLimit() {
		RateLimit rateLimit = this.rateLimit;
		return rateLimit != null ? rateLimit : new EmptyRateLimit();
	}

	public OpenAiAudioTranscriptionResponseMetadata withRateLimit(RateLimit rateLimit) {
		this.rateLimit = rateLimit;
		return this;
	}

	@Override
	public String toString() {
		return AI_METADATA_STRING.formatted(getClass().getName(), getRateLimit());
	}

}
