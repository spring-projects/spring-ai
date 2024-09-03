package org.springframework.ai.qianfan.api.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the response received when requesting an access token.
 *
 * @author Geng Rong
 * @since 1.0
 */
public record AccessTokenResponse(@JsonProperty("access_token") String accessToken,
		@JsonProperty("refresh_token") String refreshToken, @JsonProperty("expires_in") Long expiresIn,
		@JsonProperty("session_key") String sessionKey, @JsonProperty("session_secret") String sessionSecret,
		@JsonProperty("error") String error, @JsonProperty("error_description") String errorDescription, String scope) {
}
