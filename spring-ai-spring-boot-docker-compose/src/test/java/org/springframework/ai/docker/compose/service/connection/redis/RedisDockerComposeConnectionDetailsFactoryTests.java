package org.springframework.ai.docker.compose.service.connection.redis;

import org.junit.jupiter.api.Test;
import org.springframework.ai.autoconfigure.vectorstore.redis.RedisConnectionDetails;
import org.springframework.boot.docker.compose.service.connection.test.AbstractDockerComposeIntegrationTests;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

class RedisDockerComposeConnectionDetailsFactoryTests extends AbstractDockerComposeIntegrationTests {

	RedisDockerComposeConnectionDetailsFactoryTests() {
		super("redis-compose.yaml", DockerImageName.parse("redis/redis-stack-server"));
	}

	@Test
	void runCreatesConnectionDetails() {
		RedisConnectionDetails connectionDetails = run(RedisConnectionDetails.class);
		assertThat(connectionDetails.getUri()).startsWith("redis://");
	}

}
