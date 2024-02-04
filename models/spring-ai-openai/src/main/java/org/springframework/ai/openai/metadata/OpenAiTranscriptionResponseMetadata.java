/*
 * Copyright 2023 the original author or authors.
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

import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.transcription.metadata.TranscriptionResponseMetadata;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * {@link TranscriptionResponseMetadata} implementation for {@literal OpenAI}.
 *
 * @author MichaelLavelle
 * @see TranscriptionResponseMetadata
 * @see RateLimit
 * @since 0.7.0
 */
public class OpenAiTranscriptionResponseMetadata implements TranscriptionResponseMetadata {

	protected static final String AI_METADATA_STRING = "{ @type: %1$s, rateLimit: %4$s }";

	public static OpenAiTranscriptionResponseMetadata from(OpenAiApi.Transcription result) {
		Assert.notNull(result, "OpenAI Transcription must not be null");
		OpenAiTranscriptionResponseMetadata transcriptionResponseMetadata = new OpenAiTranscriptionResponseMetadata();
		return transcriptionResponseMetadata;
	}

	@Nullable
	private RateLimit rateLimit;

	protected OpenAiTranscriptionResponseMetadata() {
		this(null);
	}

	protected OpenAiTranscriptionResponseMetadata(@Nullable OpenAiRateLimit rateLimit) {
		this.rateLimit = rateLimit;
	}

	@Override
	@Nullable
	public RateLimit getRateLimit() {
		RateLimit rateLimit = this.rateLimit;
		return rateLimit != null ? rateLimit : RateLimit.NULL;
	}

	public OpenAiTranscriptionResponseMetadata withRateLimit(RateLimit rateLimit) {
		this.rateLimit = rateLimit;
		return this;
	}

	@Override
	public String toString() {
		return AI_METADATA_STRING.formatted(getClass().getName(), getRateLimit());
	}

}
