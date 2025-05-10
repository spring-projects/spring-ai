/*
 * Copyright 2023-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.model.chat.memory.repository.cassandra.autoconfigure;

import java.time.Duration;
import java.util.List;

import com.datastax.driver.core.utils.UUIDs;
import org.junit.jupiter.api.Test;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.ai.chat.memory.cassandra.CassandraChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.cassandra.CassandraAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Mick Semb Wever
 * @author Jihoon Kim
 * @since 1.0.0
 */
@Testcontainers
class CassandraChatMemoryRepositoryAutoConfigurationIT {

	static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("cassandra");

	@Container
	static CassandraContainer cassandraContainer = new CassandraContainer(DEFAULT_IMAGE_NAME.withTag("5.0"));

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(CassandraChatMemoryRepositoryAutoConfiguration.class,
				CassandraAutoConfiguration.class))
		.withPropertyValues("spring.ai.chat.memory.cassandra.keyspace=test_autoconfigure");

	@Test
	void addAndGet() {
		this.contextRunner.withPropertyValues("spring.cassandra.contactPoints=" + getContactPointHost())
			.withPropertyValues("spring.cassandra.port=" + getContactPointPort())
			.withPropertyValues("spring.cassandra.localDatacenter=" + cassandraContainer.getLocalDatacenter())
			.withPropertyValues("spring.ai.chat.memory.repository.cassandra.time-to-live=" + getTimeToLive())
			.run(context -> {
				CassandraChatMemoryRepository memory = context.getBean(CassandraChatMemoryRepository.class);

				String sessionId = UUIDs.timeBased().toString();
				assertThat(memory.findByConversationId(sessionId)).isEmpty();

				memory.saveAll(sessionId, List.of(new UserMessage("test question")));

				assertThat(memory.findByConversationId(sessionId)).hasSize(1);
				assertThat(memory.findByConversationId(sessionId).get(0).getMessageType()).isEqualTo(MessageType.USER);
				assertThat(memory.findByConversationId(sessionId).get(0).getText()).isEqualTo("test question");

				memory.deleteByConversationId(sessionId);
				assertThat(memory.findByConversationId(sessionId)).isEmpty();

				memory.saveAll(sessionId,
						List.of(new UserMessage("test question"), new AssistantMessage("test answer")));

				assertThat(memory.findByConversationId(sessionId)).hasSize(2);
				assertThat(memory.findByConversationId(sessionId).get(1).getMessageType())
					.isEqualTo(MessageType.ASSISTANT);
				assertThat(memory.findByConversationId(sessionId).get(1).getText()).isEqualTo("test answer");
				assertThat(memory.findByConversationId(sessionId).get(0).getMessageType()).isEqualTo(MessageType.USER);
				assertThat(memory.findByConversationId(sessionId).get(0).getText()).isEqualTo("test question");

				CassandraChatMemoryRepositoryProperties properties = context
					.getBean(CassandraChatMemoryRepositoryProperties.class);
				assertThat(properties.getTimeToLive()).isEqualTo(getTimeToLive());
			});
	}

	@Test
	void compareTimeToLive_ISO8601Format() {
		this.contextRunner.withPropertyValues("spring.cassandra.contactPoints=" + getContactPointHost())
			.withPropertyValues("spring.cassandra.port=" + getContactPointPort())
			.withPropertyValues("spring.cassandra.localDatacenter=" + cassandraContainer.getLocalDatacenter())
			.withPropertyValues("spring.ai.chat.memory.repository.cassandra.time-to-live=" + getTimeToLiveString())
			.run(context -> {
				CassandraChatMemoryRepositoryProperties properties = context
					.getBean(CassandraChatMemoryRepositoryProperties.class);
				assertThat(properties.getTimeToLive()).isEqualTo(Duration.parse(getTimeToLiveString()));
			});
	}

	private String getContactPointHost() {
		return cassandraContainer.getContactPoint().getHostString();
	}

	private String getContactPointPort() {
		return String.valueOf(cassandraContainer.getContactPoint().getPort());
	}

	private Duration getTimeToLive() {
		return Duration.ofSeconds(12000);
	}

	private String getTimeToLiveString() {
		return "PT1M";
	}

}
