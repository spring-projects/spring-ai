package org.springframework.ai.wenxin.api.common;

/**
 * @author lvchzh
 * @date 2024年05月14日 下午2:40
 * @description:
 */
public class WenxinApiClientErrorException extends RuntimeException {

	public WenxinApiClientErrorException(String message) {
		super(message);
	}

	public WenxinApiClientErrorException(String message, Throwable cause) {
		super(message, cause);
	}

}
