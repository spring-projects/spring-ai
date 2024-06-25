package org.springframework.ai.wenxin.api.common;

/**
 * @author lvchzh
 * @since 1.0.0
 */
public class WenxinApiClientErrorException extends RuntimeException {

	public WenxinApiClientErrorException(String message) {
		super(message);
	}

	public WenxinApiClientErrorException(String message, Throwable cause) {
		super(message, cause);
	}

}
