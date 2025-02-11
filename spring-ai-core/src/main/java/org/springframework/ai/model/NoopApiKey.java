package org.springframework.ai.model;

/**
 * This implementation of ApiKey indicates that no API key should be used, e.g. no HTTP
 * headers should be set.
 */
public class NoopApiKey implements ApiKey {

	@Override
	public String getValue() {
		return "";
	}

}
