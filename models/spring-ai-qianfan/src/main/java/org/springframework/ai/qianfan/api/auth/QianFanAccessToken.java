package org.springframework.ai.qianfan.api.auth;

/**
 * Represents an access token for the QianFan API.
 *
 * @author Geng Rong
 * @since 1.0
 */
public class QianFanAccessToken {

	private static final Double FRACTION_OF_TIME_TO_LIVE = 0.8D;

	private final String accessToken;

	private final String refreshToken;

	private final Long expiresIn;

	private final String sessionKey;

	private final String sessionSecret;

	private final String scope;

	private final Long refreshTime;

	public QianFanAccessToken(AccessTokenResponse accessTokenResponse) {
		this.accessToken = accessTokenResponse.accessToken();
		this.refreshToken = accessTokenResponse.refreshToken();
		this.expiresIn = accessTokenResponse.expiresIn();
		this.sessionKey = accessTokenResponse.sessionKey();
		this.sessionSecret = accessTokenResponse.sessionSecret();
		this.scope = accessTokenResponse.scope();
		this.refreshTime = getCurrentTimeInSeconds() + (long) ((double) expiresIn * FRACTION_OF_TIME_TO_LIVE);
	}

	public String getAccessToken() {
		return accessToken;
	}

	public String getRefreshToken() {
		return refreshToken;
	}

	public Long getExpiresIn() {
		return expiresIn;
	}

	public String getSessionKey() {
		return sessionKey;
	}

	public String getSessionSecret() {
		return sessionSecret;
	}

	public Long getRefreshTime() {
		return refreshTime;
	}

	public String getScope() {
		return scope;
	}

	public synchronized boolean needsRefresh() {
		return getCurrentTimeInSeconds() >= this.refreshTime;
	}

	private long getCurrentTimeInSeconds() {
		return System.currentTimeMillis() / 1000L;
	}

}