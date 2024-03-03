package org.springframework.ai.openai.api.common;

/**
 * Non HTTP Error related exceptions
 */
public class OpenAiApiException extends RuntimeException {

	public OpenAiApiException(String message) {
		super(message);
	}

	public OpenAiApiException(String message, Throwable cause) {
		super(message, cause);
	}

}