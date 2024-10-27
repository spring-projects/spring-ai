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
 * QianFan abstract authentication API.
 *
 * @author Geng Rong
 * @since 1.0
 */
public abstract class AuthApi {

	private final QianFanAuthenticator authenticator;

	private QianFanAccessToken token;

	/**
	 * Create a new chat completion api with default base URL.
	 * @param apiKey QianFan api key.
	 * @param secretKey QianFan secret key.
	 */
	protected AuthApi(String apiKey, String secretKey) {
		this.authenticator = QianFanAuthenticator.builder().apiKey(apiKey).secretKey(secretKey).build();
	}

	protected String getAccessToken() {
		if (this.token == null || this.token.needsRefresh()) {
			this.token = this.authenticator.requestToken();
		}
		return this.token.getAccessToken();
	}

}
