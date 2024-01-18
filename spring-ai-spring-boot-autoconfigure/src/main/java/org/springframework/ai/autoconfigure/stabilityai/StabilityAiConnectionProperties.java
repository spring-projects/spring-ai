package org.springframework.ai.autoconfigure.stabilityai;

import org.springframework.ai.stabilityai.api.StabilityAiApi;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(StabilityAiConnectionProperties.CONFIG_PREFIX)
public class StabilityAiConnectionProperties {

	public static final String CONFIG_PREFIX = "spring.ai.stability.ai";

	private String apiKey;

	private String baseUrl = StabilityAiApi.DEFAULT_BASE_URL;

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

}
