package org.springframework.ai.docker.compose.service.connection.qdrant;

import org.junit.jupiter.api.Test;
import org.springframework.ai.autoconfigure.vectorstore.qdrant.QdrantConnectionDetails;
import org.springframework.boot.docker.compose.service.connection.test.AbstractDockerComposeIntegrationTests;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

class QdrantDockerComposeConnectionDetailsFactoryTests extends AbstractDockerComposeIntegrationTests {

	QdrantDockerComposeConnectionDetailsFactoryTests() {
		super("qdrant-compose.yaml", DockerImageName.parse("qdrant/qdrant"));
	}

	@Test
	void runCreatesConnectionDetails() {
		QdrantConnectionDetails connectionDetails = run(QdrantConnectionDetails.class);
		assertThat(connectionDetails.getHost()).isNotNull();
		assertThat(connectionDetails.getPort()).isGreaterThan(0);
	}

}
