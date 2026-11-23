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

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

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
 * Integration tests for {@link JdbcSessionRepositoryAutoConfiguration} against Microsoft
 * SQL Server: the schema script found for the platform, run by Spring Boot's script
 * initializer on every startup, and the dialect detected from the data source.
 *
 * @author Christian Tzolov
 */
@Testcontainers
class JdbcSessionRepositorySqlServerAutoConfigurationIT {

	// A container rather than a jdbc:tc: URL, which accepts the image's license only
	// through a file on the classpath. One database for all tests, so every context
	// after the first starts against the existing schema.
	@Container
	static final MSSQLServerContainer sqlServer = new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-latest")
		.acceptLicense();

	// continue-on-error is true by default and would hide a statement that fails, on the
	// first run or on a later one; with false such a statement fails the startup.
	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(JdbcSessionRepositoryAutoConfiguration.class,
				JdbcTemplateAutoConfiguration.class, DataSourceAutoConfiguration.class))
		.withPropertyValues("spring.datasource.url=" + sqlServer.getJdbcUrl(),
				"spring.datasource.username=" + sqlServer.getUsername(),
				"spring.datasource.password=" + sqlServer.getPassword(),
				JdbcSessionRepositoryProperties.CONFIG_PREFIX + ".initialize-schema=always",
				JdbcSessionRepositoryProperties.CONFIG_PREFIX + ".continue-on-error=false");

	@Test
	void schemaInitializerResolvesSqlServerScript() {
		this.contextRunner.run(context -> assertThat(context.getBean(JdbcSessionRepositorySchemaInitializer.class))
			.extracting("settings.schemaLocations")
			.asInstanceOf(InstanceOfAssertFactories.LIST)
			.containsOnly("classpath:org/springframework/ai/session/jdbc/schema-sqlserver.sql"));
	}

	@Test
	void schemaInitializationIsRepeatable() {
		// initialize-schema=always re-runs the script on every startup against an
		// existing database; no statement of a second run may fail (e.g. on an
		// existing table).
		this.contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			var initializer = context.getBean(JdbcSessionRepositorySchemaInitializer.class);
			assertThat(initializer.initializeDatabase()).isTrue();
			assertThat(context.getBean(JdbcSessionRepository.class).findById("missing")).isNull();
		});
	}

	@Test
	void embeddedModeLeavesRealDatabaseAlone() {
		this.contextRunner
			.withPropertyValues(JdbcSessionRepositoryProperties.CONFIG_PREFIX + ".initialize-schema=embedded")
			.run(context -> assertThat(
					context.getBean(JdbcSessionRepositorySchemaInitializer.class).initializeDatabase())
				.isFalse());
	}

	@Test
	void repositoryStoresSessionAndEvents() {
		this.contextRunner.run(context -> {
			JdbcSessionRepository repository = context.getBean(JdbcSessionRepository.class);
			String sessionId = UUID.randomUUID().toString();
			String principal = "user-sqlserver-" + sessionId;
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
