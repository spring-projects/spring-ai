package org.springframework.ai.openai.api.common;

/**
 * Thrown on 4xx client errors, such as 401 - Incorrect API key provided, 401 - You must
 * be a member of an organization to use the API, 429 - Rate limit reached for requests,
 * 429 - You exceeded your current quota , please check your plan and billing details.
 */
public class OpenAiApiClientErrorException extends RuntimeException {

	public OpenAiApiClientErrorException(String message) {
		super(message);
	}

	public OpenAiApiClientErrorException(String message, Throwable cause) {
		super(message, cause);
	}

}