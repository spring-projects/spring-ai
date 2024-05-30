package org.springframework.ai.wenxin.metadata.support;

/**
 * @author lvchzh
 * @date 2024年05月14日 下午4:59
 * @description:
 */
public enum WenxinApiResponseHeaders {

	// @formatter:off
	REQUESTS_LIMIT_HEADER("X-Ratelimit-Limit-Requests", "Total number of requests allowed within timeframe."),
	TOKENS_LIMIT_HEADER("X-Ratelimit-Limit-Tokens", "Remaining number of tokens available in timeframe."),
	REQUESTS_REMAINING_HEADER("X-Ratelimit-Remaining-Requests", "Remaining number of requests available in timeframe."),
	TOKENS_REMAINING_HEADER("X-Ratelimit-Remaining-Tokens", "Duration of time until the number of tokens reset.");
	// @formatter:on

	private String headerName;

	private String description;

	WenxinApiResponseHeaders(String headerName, String description) {
		this.headerName = headerName;
		this.description = description;
	}

	public String getName() {
		return this.headerName;
	}

	public String getDescription() {
		return this.description;
	}

}
