/*
 * Copyright 2026-2026 the original author or authors.
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

package org.springframework.ai.openaisdk.metadata;

import java.time.Duration;

import com.openai.core.http.Headers;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.audio.tts.TextToSpeechResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyRateLimit;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.util.Assert;

/**
 * Audio speech metadata implementation for OpenAI using the OpenAI Java SDK.
 *
 * @author Ilayaperumal Gopinathan
 * @author Ahmed Yousri
 * @since 2.0.0
 */
public class OpenAiSdkAudioSpeechResponseMetadata extends TextToSpeechResponseMetadata {

	public static final OpenAiSdkAudioSpeechResponseMetadata NULL = new OpenAiSdkAudioSpeechResponseMetadata();

	protected static final String AI_METADATA_STRING = "{ @type: %1$s, rateLimit: %2$s }";

	private static final String REQUESTS_LIMIT_HEADER = "x-ratelimit-limit-requests";

	private static final String REQUESTS_REMAINING_HEADER = "x-ratelimit-remaining-requests";

	private static final String REQUESTS_RESET_HEADER = "x-ratelimit-reset-requests";

	private static final String TOKENS_LIMIT_HEADER = "x-ratelimit-limit-tokens";

	private static final String TOKENS_REMAINING_HEADER = "x-ratelimit-remaining-tokens";

	private static final String TOKENS_RESET_HEADER = "x-ratelimit-reset-tokens";

	@Nullable private final RateLimit rateLimit;

	public OpenAiSdkAudioSpeechResponseMetadata() {
		this(null);
	}

	public OpenAiSdkAudioSpeechResponseMetadata(@Nullable RateLimit rateLimit) {
		this.rateLimit = rateLimit;
	}

	public static OpenAiSdkAudioSpeechResponseMetadata from(Headers headers) {
		Assert.notNull(headers, "Headers must not be null");

		Long requestsLimit = getHeaderAsLong(headers, REQUESTS_LIMIT_HEADER);
		Long requestsRemaining = getHeaderAsLong(headers, REQUESTS_REMAINING_HEADER);
		Duration requestsReset = getHeaderAsDuration(headers, REQUESTS_RESET_HEADER);

		Long tokensLimit = getHeaderAsLong(headers, TOKENS_LIMIT_HEADER);
		Long tokensRemaining = getHeaderAsLong(headers, TOKENS_REMAINING_HEADER);
		Duration tokensReset = getHeaderAsDuration(headers, TOKENS_RESET_HEADER);

		RateLimit rateLimit = (requestsLimit != null || tokensLimit != null) ? new OpenAiSdkRateLimit(requestsLimit,
				requestsRemaining, requestsReset, tokensLimit, tokensRemaining, tokensReset) : new EmptyRateLimit();

		return new OpenAiSdkAudioSpeechResponseMetadata(rateLimit);
	}

	private static Long getHeaderAsLong(Headers headers, String headerName) {
		var values = headers.values(headerName);
		if (!values.isEmpty()) {
			try {
				return Long.parseLong(values.get(0).trim());
			}
			catch (NumberFormatException e) {
				return null;
			}
		}
		return null;
	}

	private static Duration getHeaderAsDuration(Headers headers, String headerName) {
		var values = headers.values(headerName);
		if (!values.isEmpty()) {
			try {
				return Duration.ofSeconds(Long.parseLong(values.get(0).trim()));
			}
			catch (Exception e) {
				return null;
			}
		}
		return null;
	}

	@Nullable public RateLimit getRateLimit() {
		RateLimit rateLimit = this.rateLimit;
		return rateLimit != null ? rateLimit : new EmptyRateLimit();
	}

	@Override
	public String toString() {
		return AI_METADATA_STRING.formatted(getClass().getName(), getRateLimit());
	}

}
