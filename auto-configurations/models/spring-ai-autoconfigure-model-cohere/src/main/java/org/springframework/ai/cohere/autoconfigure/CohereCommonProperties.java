package org.springframework.ai.cohere.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Common properties for Cohere.
 *
 * @author Ricken Bazolo
 */
@ConfigurationProperties(CohereCommonProperties.CONFIG_PREFIX)
public class CohereCommonProperties extends CohereParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.cohere";

	public static final String DEFAULT_BASE_URL = "https://api.cohere.com";

	public CohereCommonProperties() {
		super.setBaseUrl(DEFAULT_BASE_URL);
	}

}
