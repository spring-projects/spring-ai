package org.springframework.ai.autoconfigure.dashscope;

/**
 * @author Nottyjay Ji
 */
public class DashscopeParentProperties {

	private String apiKey;

	private String baseUrl;

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
