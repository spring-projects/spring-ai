package org.springframework.ai.minimax.api.common;

/**
 * @author Geng Rong
 */
public class MiniMaxApiException extends RuntimeException {

	public MiniMaxApiException(String message) {
		super(message);
	}

	public MiniMaxApiException(String message, Throwable cause) {
		super(message, cause);
	}

}
