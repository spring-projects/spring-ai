/*
 * Copyright 2024 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.autoconfigure.chat.memory.cassandra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.CassandraChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.cassandra.CassandraAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.datastax.driver.core.utils.UUIDs;

/**
 * @author Mick Semb Wever
 * @since 1.0.0
 */
@Testcontainers
class CassandraChatMemoryAutoConfigurationIT {

	static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("cassandra");

	@Container
	static CassandraContainer cassandraContainer = new CassandraContainer(DEFAULT_IMAGE_NAME.withTag("5.0"));

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(
				AutoConfigurations.of(CassandraChatMemoryAutoConfiguration.class, CassandraAutoConfiguration.class))
		.withPropertyValues("spring.ai.chat.memory.cassandra.keyspace=test_autoconfigure");

	@Test
	void addAndGet() {
		contextRunner.withPropertyValues("spring.cassandra.contactPoints=" + getContactPointHost())
			.withPropertyValues("spring.cassandra.port=" + getContactPointPort())
			.withPropertyValues("spring.cassandra.localDatacenter=" + cassandraContainer.getLocalDatacenter())

			.run(context -> {
				CassandraChatMemory memory = context.getBean(CassandraChatMemory.class);

				String sessionId = UUIDs.timeBased().toString();
				assertThat(memory.get(sessionId, Integer.MAX_VALUE)).isEmpty();

				memory.add(sessionId, new UserMessage("test question"));

				assertThat(memory.get(sessionId, Integer.MAX_VALUE)).hasSize(1);
				assertThat(memory.get(sessionId, Integer.MAX_VALUE).get(0).getMessageType())
					.isEqualTo(MessageType.USER);
				assertThat(memory.get(sessionId, Integer.MAX_VALUE).get(0).getContent()).isEqualTo("test question");

				memory.clear(sessionId);
				assertThat(memory.get(sessionId, Integer.MAX_VALUE)).isEmpty();

				memory.add(sessionId, List.of(new UserMessage("test question"), new AssistantMessage("test answer")));

				assertThat(memory.get(sessionId, Integer.MAX_VALUE)).hasSize(2);
				assertThat(memory.get(sessionId, Integer.MAX_VALUE).get(1).getMessageType())
					.isEqualTo(MessageType.USER);
				assertThat(memory.get(sessionId, Integer.MAX_VALUE).get(1).getContent()).isEqualTo("test question");
				assertThat(memory.get(sessionId, Integer.MAX_VALUE).get(0).getMessageType())
					.isEqualTo(MessageType.ASSISTANT);
				assertThat(memory.get(sessionId, Integer.MAX_VALUE).get(0).getContent()).isEqualTo("test answer");

			});
	}

	private String getContactPointHost() {
		return cassandraContainer.getContactPoint().getHostString();
	}

	private String getContactPointPort() {
		return String.valueOf(cassandraContainer.getContactPoint().getPort());
	}

}
