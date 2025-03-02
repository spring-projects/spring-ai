package org.springframework.ai.autoconfigure.elevenlabs;

import org.springframework.ai.elevenlabs.api.ElevenLabsApi;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the ElevenLabs API connection.
 *
 * @author Alexandros Pappas
 */
@ConfigurationProperties(ElevenLabsConnectionProperties.CONFIG_PREFIX)
public class ElevenLabsConnectionProperties {

	public static final String CONFIG_PREFIX = "spring.ai.elevenlabs";

	/**
	 * ElevenLabs API access key.
	 */
	private String apiKey;

	/**
	 * ElevenLabs API base URL.
	 */
	private String baseUrl = ElevenLabsApi.DEFAULT_BASE_URL;

	public String getApiKey() {
		return this.apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getBaseUrl() {
		return this.baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

}
