package org.springframework.ai.docker.compose.service.connection.chroma;

import org.junit.jupiter.api.Test;
import org.springframework.ai.autoconfigure.vectorstore.chroma.ChromaConnectionDetails;
import org.springframework.boot.docker.compose.service.connection.test.AbstractDockerComposeIntegrationTests;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

class ChromaDockerComposeConnectionDetailsFactoryTests extends AbstractDockerComposeIntegrationTests {

	ChromaDockerComposeConnectionDetailsFactoryTests() {
		super("chroma-compose.yaml", DockerImageName.parse("chromadb/chroma"));
	}

	@Test
	void runCreatesConnectionDetails() {
		ChromaConnectionDetails connectionDetails = run(ChromaConnectionDetails.class);
		assertThat(connectionDetails.getHost()).isNotNull();
		assertThat(connectionDetails.getPort()).isGreaterThan(0);
	}

}