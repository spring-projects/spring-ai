package org.springframework.ai.docker.compose.service.connection.weaviate;

import org.junit.jupiter.api.Test;
import org.springframework.ai.autoconfigure.vectorstore.weaviate.WeaviateConnectionDetails;
import org.springframework.boot.docker.compose.service.connection.test.AbstractDockerComposeIntegrationTests;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

class WeaviateDockerComposeConnectionDetailsFactoryTests extends AbstractDockerComposeIntegrationTests {

	WeaviateDockerComposeConnectionDetailsFactoryTests() {
		super("weaviate-compose.yaml", DockerImageName.parse("semitechnologies/weaviate"));
	}

	@Test
	void runCreatesConnectionDetails() {
		WeaviateConnectionDetails connectionDetails = run(WeaviateConnectionDetails.class);
		assertThat(connectionDetails.getHost()).isNotNull();
	}

}
