package org.springframework.ai.autoconfigure.watsonxai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WatsonX.ai connection autoconfiguration properties.
 *
 * @author Pablo Sanchidrian Herrera
 * @author John Jario Moreno Rojas
 * @since 0.8.1
 */
@ConfigurationProperties(WatsonxAIConnectionProperties.CONFIG_PREFIX)
public class WatsonxAIConnectionProperties {

	public static final String CONFIG_PREFIX = "spring.ai.watsonx.ai";

	private String baseUrl = "https://us-south.ml.cloud.ibm.com/";

	private String streamEndpoint = "generation/stream?version=2023-05-29";

	private String textEndpoint = "generation/text?version=2023-05-29";

	private String projectId;

	private String IAMToken;

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public String getStreamEndpoint() {
		return streamEndpoint;
	}

	public void setStreamEndpoint(String streamEndpoint) {
		this.streamEndpoint = streamEndpoint;
	}

	public String getTextEndpoint() {
		return textEndpoint;
	}

	public void setTextEndpoint(String textEndpoint) {
		this.textEndpoint = textEndpoint;
	}

	public String getProjectId() {
		return projectId;
	}

	public void setProjectId(String projectId) {
		this.projectId = projectId;
	}

	public String getIAMToken() {
		return IAMToken;
	}

	public void setIAMToken(String IAMToken) {
		this.IAMToken = IAMToken;
	}

}
