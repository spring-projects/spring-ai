package org.springframework.ai.vectorstore;

import static org.springframework.ai.vectorstore.AzureSearchClientProperties.CONFIG_PREFIX;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties Azure Cognitive Search
 *
 * @author Greg Meyer
 *
 */
@Configuration
@ConfigurationProperties(CONFIG_PREFIX)
public class AzureSearchClientProperties {

	public static final String CONFIG_PREFIX = "spring.ai.azure.cognitive-search";

	public static final String DEFAULT_CONTENT_FIELD = "contentVector";

	private String endpoint;

	private String apiKey;

	private String index;

	private String contentField = DEFAULT_CONTENT_FIELD;

	public String getEndpoint() {
		return endpoint;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getIndex() {
		return index;
	}

	public void setIndex(String index) {
		this.index = index;
	}

	public String getContentField() {
		return contentField;
	}

	public void setContentField(String contentField) {
		this.contentField = contentField;
	}

}
