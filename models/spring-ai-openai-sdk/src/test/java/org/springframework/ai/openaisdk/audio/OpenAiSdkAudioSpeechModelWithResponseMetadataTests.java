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

package org.springframework.ai.openaisdk.audio;

import java.time.Duration;
import java.util.List;

import com.openai.core.http.Headers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.openaisdk.metadata.OpenAiSdkAudioSpeechResponseMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for OpenAiSdkAudioSpeechResponseMetadata with rate limit header extraction.
 *
 * @author Ilayaperumal Gopinathan
 * @author Ahmed Yousri
 * @author Jonghoon Park
 */
@ExtendWith(MockitoExtension.class)
class OpenAiSdkAudioSpeechModelWithResponseMetadataTests {

	@Test
	void metadataExtractsRateLimitHeadersCorrectly() {
		// Mock headers with rate limit information
		Headers mockHeaders = mock(Headers.class);

		// Set up header values matching the REST implementation test
		when(mockHeaders.values("x-ratelimit-limit-requests")).thenReturn(List.of("4000"));
		when(mockHeaders.values("x-ratelimit-remaining-requests")).thenReturn(List.of("999"));
		when(mockHeaders.values("x-ratelimit-reset-requests")).thenReturn(List.of("231329")); // 2d16h15m29s
																								// in
																								// seconds
		when(mockHeaders.values("x-ratelimit-limit-tokens")).thenReturn(List.of("725000"));
		when(mockHeaders.values("x-ratelimit-remaining-tokens")).thenReturn(List.of("112358"));
		when(mockHeaders.values("x-ratelimit-reset-tokens")).thenReturn(List.of("100855")); // 27h55s451ms
																							// in
																							// seconds

		// Create metadata from headers
		OpenAiSdkAudioSpeechResponseMetadata speechResponseMetadata = OpenAiSdkAudioSpeechResponseMetadata
			.from(mockHeaders);

		// Verify metadata is created
		assertThat(speechResponseMetadata).isNotNull();

		// Verify rate limit information
		var rateLimit = speechResponseMetadata.getRateLimit();
		assertThat(rateLimit).isNotNull();

		Long requestsLimit = rateLimit.getRequestsLimit();
		Long tokensLimit = rateLimit.getTokensLimit();
		Long tokensRemaining = rateLimit.getTokensRemaining();
		Long requestsRemaining = rateLimit.getRequestsRemaining();
		Duration requestsReset = rateLimit.getRequestsReset();
		Duration tokensReset = rateLimit.getTokensReset();

		// Verify all values match expected
		assertThat(requestsLimit).isEqualTo(4000L);
		assertThat(tokensLimit).isEqualTo(725000L);
		assertThat(tokensRemaining).isEqualTo(112358L);
		assertThat(requestsRemaining).isEqualTo(999L);
		assertThat(requestsReset).isEqualTo(Duration.ofSeconds(231329)); // 2d16h15m29s
		assertThat(tokensReset).isEqualTo(Duration.ofSeconds(100855)); // 27h55s
	}

	@Test
	void metadataHandlesPartialRateLimitHeaders() {
		// Mock headers with only request rate limits
		Headers mockHeaders = mock(Headers.class);

		when(mockHeaders.values("x-ratelimit-limit-requests")).thenReturn(List.of("1000"));
		when(mockHeaders.values("x-ratelimit-remaining-requests")).thenReturn(List.of("500"));
		when(mockHeaders.values("x-ratelimit-reset-requests")).thenReturn(List.of("60"));
		when(mockHeaders.values("x-ratelimit-limit-tokens")).thenReturn(List.of());
		when(mockHeaders.values("x-ratelimit-remaining-tokens")).thenReturn(List.of());
		when(mockHeaders.values("x-ratelimit-reset-tokens")).thenReturn(List.of());

		OpenAiSdkAudioSpeechResponseMetadata metadata = OpenAiSdkAudioSpeechResponseMetadata.from(mockHeaders);

		var rateLimit = metadata.getRateLimit();
		assertThat(rateLimit.getRequestsLimit()).isEqualTo(1000L);
		assertThat(rateLimit.getRequestsRemaining()).isEqualTo(500L);
		assertThat(rateLimit.getRequestsReset()).isEqualTo(Duration.ofSeconds(60));
		// When token headers are not present, should return null (not 0)
		assertThat(rateLimit.getTokensLimit()).isNull();
		assertThat(rateLimit.getTokensRemaining()).isNull();
		assertThat(rateLimit.getTokensReset()).isNull();
	}

	@Test
	void metadataHandlesEmptyHeaders() {
		// Mock headers with no rate limit information
		Headers mockHeaders = mock(Headers.class);

		when(mockHeaders.values("x-ratelimit-limit-requests")).thenReturn(List.of());
		when(mockHeaders.values("x-ratelimit-remaining-requests")).thenReturn(List.of());
		when(mockHeaders.values("x-ratelimit-reset-requests")).thenReturn(List.of());
		when(mockHeaders.values("x-ratelimit-limit-tokens")).thenReturn(List.of());
		when(mockHeaders.values("x-ratelimit-remaining-tokens")).thenReturn(List.of());
		when(mockHeaders.values("x-ratelimit-reset-tokens")).thenReturn(List.of());

		OpenAiSdkAudioSpeechResponseMetadata metadata = OpenAiSdkAudioSpeechResponseMetadata.from(mockHeaders);

		// Should return EmptyRateLimit when no headers present (returns 0L not null)
		var rateLimit = metadata.getRateLimit();
		assertThat(rateLimit).isNotNull();
		assertThat(rateLimit.getRequestsLimit()).isEqualTo(0L);
		assertThat(rateLimit.getTokensLimit()).isEqualTo(0L);
	}

	@Test
	void metadataHandlesInvalidHeaderValues() {
		// Mock headers with invalid values
		Headers mockHeaders = mock(Headers.class);

		when(mockHeaders.values("x-ratelimit-limit-requests")).thenReturn(List.of("invalid"));
		when(mockHeaders.values("x-ratelimit-remaining-requests")).thenReturn(List.of("not-a-number"));
		when(mockHeaders.values("x-ratelimit-reset-requests")).thenReturn(List.of("bad-duration"));
		when(mockHeaders.values("x-ratelimit-limit-tokens")).thenReturn(List.of());
		when(mockHeaders.values("x-ratelimit-remaining-tokens")).thenReturn(List.of());
		when(mockHeaders.values("x-ratelimit-reset-tokens")).thenReturn(List.of());

		OpenAiSdkAudioSpeechResponseMetadata metadata = OpenAiSdkAudioSpeechResponseMetadata.from(mockHeaders);

		// Should gracefully handle invalid values by returning EmptyRateLimit (0L not
		// null)
		var rateLimit = metadata.getRateLimit();
		assertThat(rateLimit).isNotNull();
		assertThat(rateLimit.getRequestsLimit()).isEqualTo(0L);
		assertThat(rateLimit.getRequestsRemaining()).isEqualTo(0L);
		assertThat(rateLimit.getRequestsReset()).isEqualTo(Duration.ZERO);
	}

}
