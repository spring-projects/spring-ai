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

package org.springframework.ai.model.session.jdbc.autoconfigure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionAttributes;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.jdbc.JdbcSessionRepository;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link JdbcSessionRepositoryAutoConfiguration} against MySQL via
 * Testcontainers JDBC URL.
 *
 * @author Christian Tzolov
 */
class JdbcSessionRepositoryMysqlAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(JdbcSessionRepositoryAutoConfiguration.class,
				JdbcTemplateAutoConfiguration.class, DataSourceAutoConfiguration.class))
		.withPropertyValues("spring.datasource.url=jdbc:tc:mysql:8.0:///sessiontest",
				JdbcSessionRepositoryProperties.CONFIG_PREFIX + ".initialize-schema=always");

	@Test
	void schemaInitializerIsCreated() {
		this.contextRunner
			.run(context -> assertThat(context).hasSingleBean(JdbcSessionRepositorySchemaInitializer.class));
	}

	@Test
	void schemaInitializationIsRepeatable() {
		// initialize-schema=always re-runs the script on every startup against an
		// existing database; a second run must not fail (e.g. on duplicate indexes).
		this.contextRunner.run(context -> {
			var initializer = context.getBean(JdbcSessionRepositorySchemaInitializer.class);
			assertThat(initializer.initializeDatabase()).isTrue();
			assertThat(context.getBean(JdbcSessionRepository.class).findById("missing")).isNull();
		});
	}

	@Test
	void schemaInitializerResolvesPlatformScript() {
		this.contextRunner.run(context -> assertThat(context.getBean(JdbcSessionRepositorySchemaInitializer.class))
			.extracting("settings.schemaLocations")
			.asInstanceOf(InstanceOfAssertFactories.LIST)
			.containsOnly("classpath:org/springframework/ai/session/jdbc/schema-mysql.sql"));
	}

	@Test
	void embeddedModeLeavesRealDatabaseAlone() {
		this.contextRunner
			.withPropertyValues(JdbcSessionRepositoryProperties.CONFIG_PREFIX + ".initialize-schema=embedded")
			.run(context -> {
				assertThat(context).hasSingleBean(JdbcSessionRepositorySchemaInitializer.class);
				assertThat(context.getBean(JdbcSessionRepositorySchemaInitializer.class).initializeDatabase())
					.isFalse();
			});
	}

	@Test
	void schemaInitializerNotCreatedWhenDisabled() {
		this.contextRunner
			.withPropertyValues(JdbcSessionRepositoryProperties.CONFIG_PREFIX + ".initialize-schema=never")
			.run(context -> assertThat(context).doesNotHaveBean(JdbcSessionRepositorySchemaInitializer.class));
	}

	@Test
	void repositoryBeanIsCreated() {
		this.contextRunner.run(context -> assertThat(context).hasSingleBean(JdbcSessionRepository.class));
	}

	@Test
	void saveAndFindSession() {
		this.contextRunner.run(context -> {
			var repo = context.getBean(JdbcSessionRepository.class);
			String sessionId = UUID.randomUUID().toString();
			Session session = Session.builder()
				.id(sessionId)
				.metadata(SessionAttributes.PRINCIPAL_NAME, "user-mysql")
				.build();

			repo.save(session);

			assertThat(repo.findById(sessionId)).isNotNull()
				.satisfies(s -> assertThat(SessionAttributes.principalName(s)).isEqualTo("user-mysql"));
		});
	}

	@Test
	void appendAndFindEvents() {
		this.contextRunner.run(context -> {
			var repo = context.getBean(JdbcSessionRepository.class);
			String sessionId = UUID.randomUUID().toString();
			repo.save(Session.builder()
				.id(sessionId)
				.metadata(SessionAttributes.PRINCIPAL_NAME, "user-mysql-events")
				.build());

			SessionEvent event1 = SessionEvent.builder()
				.id(UUID.randomUUID().toString())
				.sessionId(sessionId)
				.timestamp(Instant.ofEpochSecond(1_700_000_000L))
				.message(new UserMessage("Hello from MySQL"))
				.build();
			SessionEvent event2 = SessionEvent.builder()
				.id(UUID.randomUUID().toString())
				.sessionId(sessionId)
				.timestamp(Instant.ofEpochSecond(1_700_000_001L))
				.message(new UserMessage("Second message"))
				.build();

			repo.appendEvent(event1);
			repo.appendEvent(event2);

			List<SessionEvent> events = repo.findEvents(sessionId, EventFilter.builder().build());
			assertThat(events).hasSize(2);
			assertThat(events.get(0).getMessage().getText()).isEqualTo("Hello from MySQL");
			assertThat(events.get(1).getMessage().getText()).isEqualTo("Second message");
		});
	}

	@Test
	void deleteSessionCascadesToEvents() {
		this.contextRunner.run(context -> {
			var repo = context.getBean(JdbcSessionRepository.class);
			String sessionId = UUID.randomUUID().toString();
			repo.save(Session.builder()
				.id(sessionId)
				.metadata(SessionAttributes.PRINCIPAL_NAME, "user-mysql-delete")
				.build());

			repo.appendEvent(SessionEvent.builder()
				.id(UUID.randomUUID().toString())
				.sessionId(sessionId)
				.timestamp(Instant.now())
				.message(new UserMessage("to be deleted"))
				.build());

			repo.delete(sessionId);

			assertThat(repo.findById(sessionId)).isNull();
		});
	}

	@Test
	void findByIndex() {
		this.contextRunner.run(context -> {
			var repo = context.getBean(JdbcSessionRepository.class);
			String userId = "user-mysql-multi-" + UUID.randomUUID();
			repo.save(Session.builder()
				.id(UUID.randomUUID().toString())
				.metadata(SessionAttributes.PRINCIPAL_NAME, userId)
				.build());
			repo.save(Session.builder()
				.id(UUID.randomUUID().toString())
				.metadata(SessionAttributes.PRINCIPAL_NAME, userId)
				.build());

			assertThat(repo.findByIndex(SessionAttributes.PRINCIPAL_NAME, userId)).hasSize(2);
		});
	}

}
