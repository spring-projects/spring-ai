package org.springframework.ai.docker.compose.service.connection.qdrant;

import java.util.Map;

class QdrantEnvironment {

	private final String apiKey;

	QdrantEnvironment(Map<String, String> env) {
		this.apiKey = env.get("QDRANT__SERVICE__API_KEY");
	}

	public String getApiKey() {
		return this.apiKey;
	}

}
