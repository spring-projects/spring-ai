/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.chat.memory.repository.cassandra;

import java.util.List;
import java.util.UUID;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.DefaultProtocolVersion;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import org.junit.jupiter.api.Test;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Schema initialisation must not depend on a per-request keyspace, which the driver
 * permits only on native protocol V5. Astra DB caps at V4, so the session here is pinned
 * to V4 to cover that case.
 *
 * Use `mvn failsafe:integration-test -Dit.test=CassandraChatMemoryRepositoryProtocolV4IT`
 *
 * @author Mick Semb Wever
 */
@Testcontainers
class CassandraChatMemoryRepositoryProtocolV4IT {

	@Container
	static CassandraContainer cassandraContainer = new CassandraContainer(CassandraImage.DEFAULT_IMAGE);

	@Test
	void schemaInitialisationAndRoundTripOnProtocolV4() {
		try (CqlSession session = protocolV4Session()) {

			assertThat(session.getContext().getProtocolVersion()).isEqualTo(DefaultProtocolVersion.V4);

			var conf = CassandraChatMemoryRepositoryConfig.builder()
				.withCqlSession(session)
				.withKeyspaceName("test_" + CassandraChatMemoryRepositoryConfig.DEFAULT_KEYSPACE_NAME + "_v4")
				.build();

			conf.dropKeyspace();

			CassandraChatMemoryRepository memory = CassandraChatMemoryRepository.create(conf);
			conf.checkSchemaValid();

			var conversationId = UUID.randomUUID().toString();
			var messages = List.<Message>of(new UserMessage("Message from user"),
					new AssistantMessage("Message from assistant"));

			memory.saveAll(conversationId, messages);

			assertThat(memory.findByConversationId(conversationId)).extracting(Message::getText)
				.containsExactly("Message from user", "Message from assistant");
		}
	}

	private static CqlSession protocolV4Session() {
		DriverConfigLoader configLoader = DriverConfigLoader.programmaticBuilder()
			.withString(DefaultDriverOption.PROTOCOL_VERSION, DefaultProtocolVersion.V4.name())
			.build();

		return new CqlSessionBuilder().addContactPoint(cassandraContainer.getContactPoint())
			.withLocalDatacenter(cassandraContainer.getLocalDatacenter())
			.withConfigLoader(configLoader)
			.build();
	}

}
