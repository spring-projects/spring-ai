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

import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * QianFanAuthenticator is a class that authenticates and requests access token for the
 * QianFan API.
 *
 * @author Geng Rong
 * @since 1.0
 */
public class QianFanAuthenticator {

	private static final String DEFAULT_AUTH_URL = "https://aip.baidubce.com";

	private static final String OPERATION_PATH = "/oauth/2.0/token?client_id={clientId}&client_secret={clientSecret}&grant_type=client_credentials";

	private final RestClient restClient;

	private final String apiKey;

	private final String secretKey;

	public QianFanAuthenticator(String authUrl, String apiKey, String secretKey) {
		this.apiKey = apiKey;
		this.secretKey = secretKey;
		this.restClient = RestClient.builder().baseUrl(authUrl).build();
	}

	public static Builder builder() {
		return new Builder();
	}

	public QianFanAccessToken requestToken() {
		ResponseEntity<AccessTokenResponse> tokenResponseEntity = this.restClient.get()
			.uri(OPERATION_PATH, this.apiKey, this.secretKey)
			.retrieve()
			.toEntity(AccessTokenResponse.class);
		AccessTokenResponse tokenResponse = tokenResponseEntity.getBody();

		if (tokenResponse == null) {
			throw new IllegalArgumentException("Failed to get access token, response is null");
		}

		if (tokenResponse.error() != null) {
			throw new IllegalArgumentException("Failed to get access token, error: " + tokenResponse.error()
					+ ", error_description: " + tokenResponse.errorDescription());
		}
		return new QianFanAccessToken(tokenResponse);
	}

	public static class Builder {

		private String apiKey;

		private String secretKey;

		public Builder apiKey(String apiKey) {
			this.apiKey = apiKey;
			return this;
		}

		public Builder secretKey(String secretKey) {
			this.secretKey = secretKey;
			return this;
		}

		public QianFanAuthenticator build() {
			return new QianFanAuthenticator(DEFAULT_AUTH_URL, this.apiKey, this.secretKey);
		}

	}

}
