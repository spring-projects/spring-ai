package org.springframework.ai.autoconfigure.huggingface;

import org.springframework.boot.context.properties.ConfigurationProperties;

import static org.springframework.ai.autoconfigure.openai.OpenAiProperties.CONFIG_PREFIX;

@ConfigurationProperties(CONFIG_PREFIX)
public class HuggingfaceProperties {

	public static final String CONFIG_PREFIX = "spring.ai.huggingface";

	private String apiKey;

	private String url;

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

}
