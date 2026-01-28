/*
 * Copyright 2023-2026 the original author or authors.
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

package org.springframework.ai.anthropic.metadata.support;

/**
 * Enumeration of HTTP response headers for the {@literal Anthropic} API rate limiting.
 *
 * @author Jake Son
 * @since 2.0.0
 * @see <a href="https://docs.anthropic.com/en/api/rate-limits#response-headers">Anthropic
 * Rate Limits</a>
 */
public enum AnthropicApiResponseHeaders {

	// Request rate limit headers
	REQUESTS_LIMIT_HEADER("anthropic-ratelimit-requests-limit"),
	REQUESTS_REMAINING_HEADER("anthropic-ratelimit-requests-remaining"),
	REQUESTS_RESET_HEADER("anthropic-ratelimit-requests-reset"),

	// Token rate limit headers
	TOKENS_LIMIT_HEADER("anthropic-ratelimit-tokens-limit"),
	TOKENS_REMAINING_HEADER("anthropic-ratelimit-tokens-remaining"),
	TOKENS_RESET_HEADER("anthropic-ratelimit-tokens-reset"),

	// Input token rate limit headers
	INPUT_TOKENS_LIMIT_HEADER("anthropic-ratelimit-input-tokens-limit"),
	INPUT_TOKENS_REMAINING_HEADER("anthropic-ratelimit-input-tokens-remaining"),
	INPUT_TOKENS_RESET_HEADER("anthropic-ratelimit-input-tokens-reset"),

	// Output token rate limit headers
	OUTPUT_TOKENS_LIMIT_HEADER("anthropic-ratelimit-output-tokens-limit"),
	OUTPUT_TOKENS_REMAINING_HEADER("anthropic-ratelimit-output-tokens-remaining"),
	OUTPUT_TOKENS_RESET_HEADER("anthropic-ratelimit-output-tokens-reset");

	private final String name;

	AnthropicApiResponseHeaders(String name) {
		this.name = name;
	}

	public String getName() {
		return this.name;
	}

}
