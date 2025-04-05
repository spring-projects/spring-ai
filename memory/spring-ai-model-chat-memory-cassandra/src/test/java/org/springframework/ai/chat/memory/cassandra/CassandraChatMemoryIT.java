/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.chat.memory.cassandra;

import java.time.Duration;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

/**
 * Use `mvn failsafe:integration-test -Dit.test=CassandraChatMemoryIT`
 *
 * @author Mick Semb Wever
 * @author Thomas Vitale
 * @since 1.0.0
 */
@Testcontainers
class CassandraChatMemoryIT {

	@Container
	static CassandraContainer<?> cassandraContainer = new CassandraContainer<>(CassandraImage.DEFAULT_IMAGE);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(CassandraChatMemoryIT.TestApplication.class);

	@Test
	void ensureBeanGetsCreated() {
		this.contextRunner.run(context -> {
			CassandraChatMemory memory = context.getBean(CassandraChatMemory.class);
			Assertions.assertNotNull(memory);
			memory.conf.checkSchemaValid();
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	public static class TestApplication {

		@Bean
		public CassandraChatMemory memory(CqlSession cqlSession) {

			var conf = CassandraChatMemoryConfig.builder()
				.withCqlSession(cqlSession)
				.withKeyspaceName("test_" + CassandraChatMemoryConfig.DEFAULT_KEYSPACE_NAME)
				.withAssistantColumnName("a")
				.withUserColumnName("u")
				.withTimeToLive(Duration.ofMinutes(1))
				.build();

			conf.dropKeyspace();
			return CassandraChatMemory.create(conf);
		}

		@Bean
		public CqlSession cqlSession() {
			return new CqlSessionBuilder()
				// comment next two lines out to connect to a local C* cluster
				.addContactPoint(cassandraContainer.getContactPoint())
				.withLocalDatacenter(cassandraContainer.getLocalDatacenter())
				.build();
		}

	}

}
