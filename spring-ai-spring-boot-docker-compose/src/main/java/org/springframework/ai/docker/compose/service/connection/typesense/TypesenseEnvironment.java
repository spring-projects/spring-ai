package org.springframework.ai.docker.compose.service.connection.typesense;

import java.util.Map;

class TypesenseEnvironment {

	private final String apiKey;

	TypesenseEnvironment(Map<String, String> env) {
		this.apiKey = env.get("TYPESENSE_API_KEY");
	}

	public String getApiKey() {
		return this.apiKey;
	}

}
