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
 * Integration tests for {@link JdbcSessionRepositoryAutoConfiguration} against MariaDB,
 * whose platform maps to the MySQL schema script.
 *
 * @author Christian Tzolov
 */
class JdbcSessionRepositoryMariaDbAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(JdbcSessionRepositoryAutoConfiguration.class,
				JdbcTemplateAutoConfiguration.class, DataSourceAutoConfiguration.class))
		.withPropertyValues("spring.datasource.url=jdbc:tc:mariadb:11.4:///sessiontest",
				JdbcSessionRepositoryProperties.CONFIG_PREFIX + ".initialize-schema=always");

	@Test
	void schemaInitializerResolvesMysqlScript() {
		this.contextRunner.run(context -> assertThat(context.getBean(JdbcSessionRepositorySchemaInitializer.class))
			.extracting("settings.schemaLocations")
			.asInstanceOf(InstanceOfAssertFactories.LIST)
			.containsOnly("classpath:org/springframework/ai/session/jdbc/schema-mysql.sql"));
	}

	@Test
	void repositoryStoresSessionAndEvents() {
		this.contextRunner.run(context -> {
			JdbcSessionRepository repository = context.getBean(JdbcSessionRepository.class);
			String sessionId = UUID.randomUUID().toString();
			repository.save(
					Session.builder().id(sessionId).metadata(SessionAttributes.PRINCIPAL_NAME, "user-mariadb").build());
			repository
				.appendEvent(SessionEvent.builder().sessionId(sessionId).message(new UserMessage("Hello")).build());

			assertThat(repository.findByIndex(SessionAttributes.PRINCIPAL_NAME, "user-mariadb")).extracting(Session::id)
				.containsExactly(sessionId);
			assertThat(repository.findEvents(sessionId, EventFilter.all())).singleElement()
				.satisfies(event -> assertThat(event.getMessage().getText()).isEqualTo("Hello"));
		});
	}

}
