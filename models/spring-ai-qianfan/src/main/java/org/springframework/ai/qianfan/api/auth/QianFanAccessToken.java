/*
 * Copyright 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
		this.refreshTime = getCurrentTimeInSeconds() + (long) ((double) this.expiresIn * FRACTION_OF_TIME_TO_LIVE);
	}

	public String getAccessToken() {
		return this.accessToken;
	}

	public String getRefreshToken() {
		return this.refreshToken;
	}

	public Long getExpiresIn() {
		return this.expiresIn;
	}

	public String getSessionKey() {
		return this.sessionKey;
	}

	public String getSessionSecret() {
		return this.sessionSecret;
	}

	public Long getRefreshTime() {
		return this.refreshTime;
	}

	public String getScope() {
		return this.scope;
	}

	public synchronized boolean needsRefresh() {
		return getCurrentTimeInSeconds() >= this.refreshTime;
	}

	private long getCurrentTimeInSeconds() {
		return System.currentTimeMillis() / 1000L;
	}

}
