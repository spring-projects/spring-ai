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

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.HttpStatusCode;
import org.springframework.util.StringUtils;

/**
 * Turns raw error response bodies from OpenAI-compatible APIs into user-friendly
 * messages. Used by {@code RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER} so that providers
 * sharing the OpenAI error envelope (DeepSeek, Mistral AI, MiniMax, etc.) surface a
 * status-prefixed message with an actionable hint instead of a raw JSON blob.
 *
 * @author stohirov
 * @since 1.1.0
 */
public final class ApiErrorMessageImprover {

	private static final Logger logger = LoggerFactory.getLogger(ApiErrorMessageImprover.class);

	private ApiErrorMessageImprover() {
	}

	/**
	 * Attempts to parse and improve an API error message. Falls back to
	 * {@code "<status> - <body>"} when parsing fails or the body is not the expected JSON
	 * envelope.
	 * @param rawError the raw error response body
	 * @param statusCode the HTTP status code
	 * @return an improved, user-friendly error message
	 */
	public static String improveErrorMessage(String rawError, HttpStatusCode statusCode) {
		if (!StringUtils.hasText(rawError)) {
			return createFallbackMessage("Empty error response", statusCode);
		}

		try {
			String trimmedError = rawError.trim();
			if (trimmedError.startsWith("{") && trimmedError.endsWith("}")) {
				ApiErrorResponse errorResponse = JsonMapper.shared().readValue(trimmedError, ApiErrorResponse.class);
				if (errorResponse.getError() != null) {
					return createImprovedMessage(errorResponse.getError(), statusCode);
				}
			}
		}
		catch (Exception ex) {
			logger.debug("Failed to parse API error response as JSON: {}", ex.getMessage());
		}

		return createFallbackMessage(rawError, statusCode);
	}

	private static String createImprovedMessage(ApiErrorResponse.ErrorDetail errorDetail, HttpStatusCode statusCode) {
		StringBuilder message = new StringBuilder();
		message.append(getUserFriendlyPrefix(statusCode));

		if (StringUtils.hasText(errorDetail.getMessage())) {
			message.append(errorDetail.getMessage());
		}
		else {
			message.append("An error occurred with the API");
		}

		String helpfulHint = getHelpfulHint(errorDetail.getCode());
		if (StringUtils.hasText(helpfulHint)) {
			message.append(" (").append(helpfulHint).append(")");
		}

		return message.toString();
	}

	private static String createFallbackMessage(String rawError, HttpStatusCode statusCode) {
		return String.format("%s - %s", statusCode.value(), rawError);
	}

	private static String getUserFriendlyPrefix(HttpStatusCode statusCode) {
		int status = statusCode.value();
		if (status == 401) {
			return "Authentication Error: ";
		}
		else if (status == 403) {
			return "Permission Error: ";
		}
		else if (status == 429) {
			return "Rate Limit Error: ";
		}
		else if (status == 500) {
			return "Server Error: ";
		}
		else if (status == 503) {
			return "Service Unavailable: ";
		}
		else if (statusCode.is4xxClientError()) {
			return "Client Error: ";
		}
		else {
			return "API Error: ";
		}
	}

	private static @Nullable String getHelpfulHint(@Nullable String errorCode) {
		if (!StringUtils.hasText(errorCode)) {
			return null;
		}

		return switch (errorCode) {
			case "invalid_api_key" -> "Please verify your API key";
			case "insufficient_quota" -> "Please check your plan and billing details";
			case "rate_limit_exceeded" -> "Please wait before making more requests";
			case "model_not_found" -> "Please verify the model name is correct";
			case "context_length_exceeded" -> "Please reduce the length of your input";
			default -> null;
		};
	}

}
