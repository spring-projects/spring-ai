package org.springframework.ai.wenxin.api.common;

/**
 * @author lvchzh
 * @date 2024年05月14日 下午2:43
 * @description:
 */
public class WenxinApiException extends RuntimeException {

	public WenxinApiException(String message) {
		super(message);
	}

	public WenxinApiException(String message, Throwable cause) {
		super(message, cause);
	}

}
