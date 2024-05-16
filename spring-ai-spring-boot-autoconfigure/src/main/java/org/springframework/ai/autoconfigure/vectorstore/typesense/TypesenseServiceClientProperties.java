package org.springframework.ai.autoconfigure.vectorstore.typesense;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Pablo Sanchidrian Herrera
 */
@ConfigurationProperties(TypesenseServiceClientProperties.CONFIG_PREFIX)
public class TypesenseServiceClientProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.typesense.client";

	private String protocol = "http";

	private String host = "localhost";

	private String port = "8108";

	/**
	 * Typesense API key. This is the default api key when the user follows the Typesense
	 * quick start guide.
	 */
	private String apiKey = "xyz";

	public String getProtocol() {
		return protocol;
	}

	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getPort() {
		return port;
	}

	public void setPort(String port) {
		this.port = port;
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

}
