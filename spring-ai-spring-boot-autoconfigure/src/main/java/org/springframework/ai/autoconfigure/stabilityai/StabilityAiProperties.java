package org.springframework.ai.autoconfigure.stabilityai;

import org.springframework.ai.stabilityai.api.StabilityAiApi;
import org.springframework.ai.stabilityai.api.StabilityAiImageOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(StabilityAiProperties.CONFIG_PREFIX)
public class StabilityAiProperties {

	public static final String CONFIG_PREFIX = "spring.ai.stabilityai";

	private String apiKey;

	private String baseUrl = StabilityAiApi.DEFAULT_BASE_URL;

	@NestedConfigurationProperty
	private StabilityAiImageOptions options;

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

	public StabilityAiImageOptions getOptions() {
		return options;
	}

	public void setOptions(StabilityAiImageOptions options) {
		this.options = options;
	}

}
