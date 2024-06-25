package org.springframework.ai.wenxin.api.common;

/**
 * @author lvchzh
 * @since 1.0.0
 */
public class WenxinApiException extends RuntimeException {

	public WenxinApiException(String message) {
		super(message);
	}

	public WenxinApiException(String message, Throwable cause) {
		super(message, cause);
	}

}
