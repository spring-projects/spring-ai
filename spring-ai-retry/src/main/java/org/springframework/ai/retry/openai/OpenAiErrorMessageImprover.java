/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.retry.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.StringUtils;

/**
 * Utility class for improving OpenAI API error messages by parsing structured error
 * responses and providing user-friendly messages.
 *
 * @author snowykte0426
 * @since 1.1.0
 */
public final class OpenAiErrorMessageImprover {

	private static final Logger logger = LoggerFactory.getLogger(OpenAiErrorMessageImprover.class);

	private static final ObjectMapper objectMapper = new ObjectMapper();

	private OpenAiErrorMessageImprover() {
		// Utility class
	}

	/**
	 * Attempts to parse and improve an OpenAI error message. If parsing fails, returns a
	 * fallback message with the original error.
	 * @param rawError The raw error response from OpenAI API
	 * @param statusCode The HTTP status code
	 * @return An improved, user-friendly error message
	 */
	public static String improveErrorMessage(String rawError, HttpStatusCode statusCode) {
		if (!StringUtils.hasText(rawError)) {
			return createFallbackMessage("Empty error response", statusCode);
		}

		try {
			// Check if the response looks like JSON
			String trimmedError = rawError.trim();
			if (trimmedError.startsWith("{") && trimmedError.endsWith("}")) {
				OpenAiErrorResponse errorResponse = objectMapper.readValue(trimmedError, OpenAiErrorResponse.class);
				if (errorResponse.getError() != null) {
					return createImprovedMessage(errorResponse.getError(), statusCode);
				}
			}
		}
		catch (Exception e) {
			logger.debug("Failed to parse OpenAI error response as JSON: {}", e.getMessage());
		}

		// Fallback to original behavior if parsing fails
		return createFallbackMessage(rawError, statusCode);
	}

	/**
	 * Creates an improved error message based on the parsed OpenAI error response.
	 * @param errorDetail The parsed error detail from OpenAI
	 * @param statusCode The HTTP status code
	 * @return A user-friendly error message
	 */
	private static String createImprovedMessage(OpenAiErrorResponse.ErrorDetail errorDetail,
			HttpStatusCode statusCode) {
		StringBuilder message = new StringBuilder();

		// Add a user-friendly prefix based on status code
		message.append(getUserFriendlyPrefix(statusCode));

		// Add the main error message
		if (StringUtils.hasText(errorDetail.getMessage())) {
			message.append(errorDetail.getMessage());
		}
		else {
			message.append("An error occurred with the OpenAI API");
		}

		// Add helpful context based on error code
		String helpfulHint = getHelpfulHint(errorDetail.getCode(), statusCode);
		if (StringUtils.hasText(helpfulHint)) {
			message.append(" (").append(helpfulHint).append(")");
		}

		return message.toString();
	}

	/**
	 * Creates a fallback error message when parsing fails.
	 * @param rawError The original error response
	 * @param statusCode The HTTP status code
	 * @return A formatted error message
	 */
	private static String createFallbackMessage(String rawError, HttpStatusCode statusCode) {
		return String.format("%s - %s", statusCode.value(), rawError);
	}

	/**
	 * Gets a user-friendly prefix based on the HTTP status code.
	 * @param statusCode The HTTP status code
	 * @return A user-friendly prefix
	 */
	private static String getUserFriendlyPrefix(HttpStatusCode statusCode) {
		int status = statusCode.value();
		if (status == 401) {
			return "OpenAI Authentication Error: ";
		}
		else if (status == 403) {
			return "OpenAI Permission Error: ";
		}
		else if (status == 429) {
			return "OpenAI Rate Limit Error: ";
		}
		else if (status == 500) {
			return "OpenAI Server Error: ";
		}
		else if (status == 503) {
			return "OpenAI Service Unavailable: ";
		}
		else if (statusCode.is4xxClientError()) {
			return "OpenAI Client Error: ";
		}
		else {
			return "OpenAI API Error: ";
		}
	}

	/**
	 * Provides helpful hints based on the error code.
	 * @param errorCode The OpenAI error code
	 * @param statusCode The HTTP status code
	 * @return A helpful hint for the user
	 */
	private static String getHelpfulHint(String errorCode, HttpStatusCode statusCode) {
		if (!StringUtils.hasText(errorCode)) {
			return null;
		}

		if ("invalid_api_key".equals(errorCode)) {
			return "Please check your API key at https://platform.openai.com/account/api-keys";
		}
		else if ("insufficient_quota".equals(errorCode)) {
			return "Please check your plan and billing details at https://platform.openai.com/account/billing";
		}
		else if ("rate_limit_exceeded".equals(errorCode)) {
			return "Please wait before making more requests";
		}
		else if ("model_not_found".equals(errorCode)) {
			return "Please verify the model name is correct";
		}
		else if ("context_length_exceeded".equals(errorCode)) {
			return "Please reduce the length of your input";
		}
		else {
			return null;
		}
	}

}
