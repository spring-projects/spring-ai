package org.springframework.ai.docker.compose.service.connection.ollama;

import org.junit.jupiter.api.Test;
import org.springframework.ai.autoconfigure.ollama.OllamaConnectionDetails;
import org.springframework.boot.docker.compose.service.connection.test.AbstractDockerComposeIntegrationTests;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaDockerComposeConnectionDetailsFactoryTests extends AbstractDockerComposeIntegrationTests {

	OllamaDockerComposeConnectionDetailsFactoryTests() {
		super("ollama-compose.yaml", DockerImageName.parse("ollama/ollama"));
	}

	@Test
	void runCreatesConnectionDetails() {
		OllamaConnectionDetails connectionDetails = run(OllamaConnectionDetails.class);
		assertThat(connectionDetails.getBaseUrl()).startsWith("http://");
	}

}
