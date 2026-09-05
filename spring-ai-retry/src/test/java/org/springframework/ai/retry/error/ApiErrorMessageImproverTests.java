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

package org.springframework.ai.retry.error;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ApiErrorMessageImprover}.
 *
 * @author snowykte0426
 */
class ApiErrorMessageImproverTests {

	@Test
	void improvesAuthenticationErrorWithInvalidApiKeyHint() {
		String rawError = """
				{"error":{"message":"Incorrect API key provided: sk-proj-***F2yw.","type":"invalid_request_error","param":null,"code":"invalid_api_key"}}""";

		String improved = ApiErrorMessageImprover.improveErrorMessage(rawError, HttpStatus.UNAUTHORIZED);

		assertThat(improved).startsWith("Authentication Error: ")
			.contains("Incorrect API key provided")
			.endsWith("(Please verify your API key)");
	}

	@Test
	void improvesRateLimitErrorWithHint() {
		String rawError = """
				{"error":{"message":"Rate limit reached","type":"rate_limit_error","code":"rate_limit_exceeded"}}""";

		String improved = ApiErrorMessageImprover.improveErrorMessage(rawError, HttpStatus.TOO_MANY_REQUESTS);

		assertThat(improved)
			.isEqualTo("Rate Limit Error: Rate limit reached (Please wait before making more requests)");
	}

	@Test
	void improvesInsufficientQuotaWithBillingHint() {
		String rawError = """
				{"error":{"message":"You exceeded your current quota","type":"insufficient_quota","code":"insufficient_quota"}}""";

		String improved = ApiErrorMessageImprover.improveErrorMessage(rawError, HttpStatus.TOO_MANY_REQUESTS);

		assertThat(improved).isEqualTo(
				"Rate Limit Error: You exceeded your current quota (Please check your plan and billing details)");
	}

	@Test
	void improvesModelNotFoundError() {
		String rawError = """
				{"error":{"message":"The model 'foo' does not exist","type":"invalid_request_error","code":"model_not_found"}}""";

		String improved = ApiErrorMessageImprover.improveErrorMessage(rawError, HttpStatus.NOT_FOUND);

		assertThat(improved)
			.isEqualTo("Client Error: The model 'foo' does not exist (Please verify the model name is correct)");
	}

	@Test
	void improvesContextLengthExceededError() {
		String rawError = """
				{"error":{"message":"max context length","type":"invalid_request_error","code":"context_length_exceeded"}}""";

		String improved = ApiErrorMessageImprover.improveErrorMessage(rawError, HttpStatus.BAD_REQUEST);

		assertThat(improved).isEqualTo("Client Error: max context length (Please reduce the length of your input)");
	}

	@Test
	void omitsHintWhenErrorCodeUnknown() {
		String rawError = """
				{"error":{"message":"Something happened","type":"server_error","code":"unknown_thing"}}""";

		String improved = ApiErrorMessageImprover.improveErrorMessage(rawError, HttpStatus.INTERNAL_SERVER_ERROR);

		assertThat(improved).isEqualTo("Server Error: Something happened");
	}

	@Test
	void usesServiceUnavailablePrefixFor503() {
		String rawError = """
				{"error":{"message":"Service down","type":"server_error"}}""";

		String improved = ApiErrorMessageImprover.improveErrorMessage(rawError, HttpStatus.SERVICE_UNAVAILABLE);

		assertThat(improved).isEqualTo("Service Unavailable: Service down");
	}

	@Test
	void usesPermissionPrefixFor403() {
		String rawError = """
				{"error":{"message":"Forbidden","type":"permission_error"}}""";

		String improved = ApiErrorMessageImprover.improveErrorMessage(rawError, HttpStatus.FORBIDDEN);

		assertThat(improved).isEqualTo("Permission Error: Forbidden");
	}

	@Test
	void usesGenericApiErrorForUnclassifiedStatus() {
		String rawError = """
				{"error":{"message":"Weird"}}""";

		String improved = ApiErrorMessageImprover.improveErrorMessage(rawError, HttpStatus.MOVED_PERMANENTLY);

		assertThat(improved).isEqualTo("API Error: Weird");
	}

	@Test
	void fallsBackToRawWhenNotJson() {
		String rawError = "<html>Bad Gateway</html>";

		String improved = ApiErrorMessageImprover.improveErrorMessage(rawError, HttpStatus.BAD_GATEWAY);

		assertThat(improved).isEqualTo("502 - <html>Bad Gateway</html>");
	}

	@Test
	void fallsBackToRawWhenJsonHasNoErrorField() {
		String rawError = "{\"foo\":\"bar\"}";

		String improved = ApiErrorMessageImprover.improveErrorMessage(rawError, HttpStatus.BAD_REQUEST);

		assertThat(improved).isEqualTo("400 - {\"foo\":\"bar\"}");
	}

	@Test
	void fallsBackWhenJsonIsMalformed() {
		String rawError = "{not json}";

		String improved = ApiErrorMessageImprover.improveErrorMessage(rawError, HttpStatus.BAD_REQUEST);

		assertThat(improved).isEqualTo("400 - {not json}");
	}

	@Test
	void emptyErrorProducesFallbackMessage() {
		String improved = ApiErrorMessageImprover.improveErrorMessage("", HttpStatus.INTERNAL_SERVER_ERROR);

		assertThat(improved).isEqualTo("500 - Empty error response");
	}

	@Test
	void usesGenericMessageWhenErrorMessageMissing() {
		String rawError = """
				{"error":{"type":"server_error","code":"invalid_api_key"}}""";

		String improved = ApiErrorMessageImprover.improveErrorMessage(rawError, HttpStatus.UNAUTHORIZED);

		assertThat(improved)
			.isEqualTo("Authentication Error: An error occurred with the AI provider API (Please verify your API key)");
	}

}
