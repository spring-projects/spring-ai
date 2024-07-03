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
