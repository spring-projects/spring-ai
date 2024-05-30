package org.springframework.ai.wenxin.metadata;

import org.springframework.ai.chat.metadata.RateLimit;

import java.time.Duration;

/**
 * @author lvchzh
 * @date 2024年05月14日 下午5:04
 * @description:
 */
public class WenxinRateLimit implements RateLimit {

	// @formatter:off
	private static final String RATE_LIMIT_STRING = "{ @type: %1$s, requestsLimit: %2$s, requestsRemaining: %3$s, tokensLimit: %4$s, tokensRemaining: %5$s }";

	private final Long requestsLimit;

	private final Long requestsRemaining;

	private final Long tokensLimit;

	private final Long tokensRemaining;

	public WenxinRateLimit(Long requestsLimit, Long requestsRemaining, Long tokensLimit, Long tokensRemaining) {
		this.requestsLimit = requestsLimit;
		this.requestsRemaining = requestsRemaining;
		this.tokensLimit = tokensLimit;
		this.tokensRemaining = tokensRemaining;
	}

	@Override
	public Long getRequestsLimit() {
		return this.requestsLimit;
	}

	@Override
	public Long getRequestsRemaining() {
		return this.requestsRemaining;
	}

	@Override
	public Duration getRequestsReset() {
		throw new UnsupportedOperationException("unimplemented method 'getRequestsReset'");
	}

	@Override
	public Long getTokensLimit() {
		return this.tokensLimit;
	}

	@Override
	public Long getTokensRemaining() {
		return this.tokensRemaining;
	}

	@Override
	public Duration getTokensReset() {
		throw new UnsupportedOperationException("unimplemented method 'getTokensReset'");
	}

	@Override
	public String toString() {
		return RATE_LIMIT_STRING.formatted(getClass().getName(), getRequestsLimit(), getRequestsRemaining(),
				getTokensLimit(), getTokensRemaining());
	}
	// @formatter:on

}
