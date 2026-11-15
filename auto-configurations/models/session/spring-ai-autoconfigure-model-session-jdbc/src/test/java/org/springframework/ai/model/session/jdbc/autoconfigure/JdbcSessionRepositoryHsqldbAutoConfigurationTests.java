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

import java.util.UUID;

import javax.sql.DataSource;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionAttributes;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.jdbc.HsqldbJdbcSessionRepositoryDialect;
import org.springframework.ai.session.jdbc.JdbcSessionRepository;
import org.springframework.ai.session.jdbc.JdbcSessionRepositoryDialect;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JdbcSessionRepositoryAutoConfiguration} against an in-process HSQLDB
 * database: the schema script found for the platform, run on startup, and the dialect
 * detected from the data source.
 *
 * @author Durga Dasepalli
 */
class JdbcSessionRepositoryHsqldbAutoConfigurationTests {

	// One database for all tests, so every context after the first starts against the
	// existing schema. continue-on-error=false makes a failing statement fail the
	// startup instead of being hidden.
	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(JdbcSessionRepositoryAutoConfiguration.class,
				JdbcTemplateAutoConfiguration.class, DataSourceAutoConfiguration.class))
		.withPropertyValues("spring.datasource.url=jdbc:hsqldb:mem:session-autoconfig", "spring.datasource.username=sa",
				JdbcSessionRepositoryProperties.CONFIG_PREFIX + ".continue-on-error=false");

	@Test
	void schemaInitializerResolvesHsqldbScript() {
		this.contextRunner.run(context -> assertThat(context.getBean(JdbcSessionRepositorySchemaInitializer.class))
			.extracting("settings.schemaLocations")
			.asInstanceOf(InstanceOfAssertFactories.LIST)
			.containsOnly("classpath:org/springframework/ai/session/jdbc/schema-hsqldb.sql"));
	}

	@Test
	void embeddedDatabaseIsInitializedByDefaultAndRepeatedly() {
		// HSQLDB in memory is an embedded database, so the default initialize-schema mode
		// applies the script; a second run against the existing schema must not fail.
		this.contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			var initializer = context.getBean(JdbcSessionRepositorySchemaInitializer.class);
			assertThat(initializer.initializeDatabase()).isTrue();
		});
	}

	@Test
	void dialectIsDetected() {
		this.contextRunner
			.run(context -> assertThat(JdbcSessionRepositoryDialect.from(context.getBean(DataSource.class)))
				.isInstanceOf(HsqldbJdbcSessionRepositoryDialect.class));
	}

	@Test
	void repositoryStoresSessionAndEvents() {
		this.contextRunner.run(context -> {
			JdbcSessionRepository repository = context.getBean(JdbcSessionRepository.class);
			String sessionId = UUID.randomUUID().toString();
			String principal = "user-hsqldb-" + sessionId;
			repository
				.save(Session.builder().id(sessionId).metadata(SessionAttributes.PRINCIPAL_NAME, principal).build());
			repository
				.appendEvent(SessionEvent.builder().sessionId(sessionId).message(new UserMessage("Hello")).build());

			assertThat(repository.findByMetadata(SessionAttributes.PRINCIPAL_NAME, principal)).extracting(Session::id)
				.containsExactly(sessionId);
			assertThat(repository.findEvents(sessionId, EventFilter.all())).singleElement()
				.satisfies(event -> assertThat(event.getMessage().getText()).isEqualTo("Hello"));

			repository.delete(sessionId);

			assertThat(repository.findById(sessionId)).isNull();
			assertThat(repository.findEvents(sessionId, EventFilter.all())).isEmpty();
		});
	}

}
