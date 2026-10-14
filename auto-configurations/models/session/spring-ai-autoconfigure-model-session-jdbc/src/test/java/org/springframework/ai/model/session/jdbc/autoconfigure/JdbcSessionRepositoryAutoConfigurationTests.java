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

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import org.springframework.ai.model.session.autoconfigure.SessionServiceAutoConfiguration;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.SessionRepository;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.jdbc.JdbcSessionRepository;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.jdbc.init.DatabaseInitializationProperties;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JdbcSessionRepositoryAutoConfiguration}.
 *
 * @author Christian Tzolov
 */
class JdbcSessionRepositoryAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.datasource.url=jdbc:h2:mem:sessionautoconfig;DB_CLOSE_DELAY=-1")
		.withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
				JdbcSessionRepositoryAutoConfiguration.class, SessionServiceAutoConfiguration.class));

	@Test
	void jdbcSessionRepositoryBeanIsCreated() {
		this.contextRunner.run(context -> assertThat(context).hasSingleBean(JdbcSessionRepository.class));
	}

	@Test
	void sessionServiceBeanIsCreated() {
		this.contextRunner.run(context -> assertThat(context).hasSingleBean(SessionService.class));
	}

	@Test
	void schemaInitializerBeanIsCreated() {
		this.contextRunner
			.run(context -> assertThat(context).hasSingleBean(JdbcSessionRepositorySchemaInitializer.class));
	}

	@Test
	void schemaInitializerNotCreatedWhenDisabled() {
		this.contextRunner
			.withPropertyValues(JdbcSessionRepositoryProperties.CONFIG_PREFIX + ".initialize-schema=never")
			.run(context -> assertThat(context).doesNotHaveBean(JdbcSessionRepositorySchemaInitializer.class));
	}

	@Test
	void customRepositoryBeanIsRespected() {
		this.contextRunner
			.withBean(JdbcSessionRepository.class,
					() -> JdbcSessionRepository.builder()
						.dataSource(new org.springframework.jdbc.datasource.DriverManagerDataSource(
								"jdbc:h2:mem:custom;DB_CLOSE_DELAY=-1"))
						.build())
			.run(context -> assertThat(context).hasSingleBean(JdbcSessionRepository.class));
	}

	@Test
	void backsOffWhenUserDeclaresSessionRepositoryOfAnyType() {
		// The documented override declares the bean as SessionRepository, not
		// JdbcSessionRepository — the auto-configuration must still back off so the
		// context does not end up with two repositories.
		this.contextRunner.withBean(SessionRepository.class, () -> InMemorySessionRepository.builder().build())
			.run(context -> {
				assertThat(context).doesNotHaveBean(JdbcSessionRepository.class);
				assertThat(context).hasSingleBean(SessionRepository.class);
				assertThat(context).hasSingleBean(SessionService.class);
			});
	}

	@Test
	void backsOffWithoutDataSource() {
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(JdbcSessionRepositoryAutoConfiguration.class,
					SessionServiceAutoConfiguration.class))
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).doesNotHaveBean(JdbcSessionRepository.class);
				assertThat(context).doesNotHaveBean(SessionService.class);
			});
	}

	@Test
	void backsOffWithoutSpringBootJdbc() {
		// spring-boot-jdbc is optional: an application that uses this module without the
		// starter (and supplies its own DataSource) must not fail at startup.
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(JdbcSessionRepositoryAutoConfiguration.class))
			.withClassLoader(new FilteredClassLoader(DatabaseInitializationProperties.class))
			.withBean(DataSource.class, () -> new DriverManagerDataSource("jdbc:h2:mem:nobootjdbc;DB_CLOSE_DELAY=-1"))
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).doesNotHaveBean(JdbcSessionRepository.class);
			});
	}

	@Test
	void indexedAttributesDefaultToLibrarysKeysAndAreBoundFromProperties() {
		this.contextRunner
			.run(context -> assertThat(context.getBean(JdbcSessionRepository.class).getIndexedAttributes())
				.containsExactlyInAnyOrderElementsOf(JdbcSessionRepository.DEFAULT_INDEXED_ATTRIBUTES));
		this.contextRunner
			.withPropertyValues(JdbcSessionRepositoryProperties.CONFIG_PREFIX + ".indexed-attributes=tenant,team")
			.run(context -> assertThat(context.getBean(JdbcSessionRepository.class).getIndexedAttributes())
				.contains("tenant", "team")
				.containsAll(JdbcSessionRepository.DEFAULT_INDEXED_ATTRIBUTES));
	}

	@Test
	void defaultProperties() {
		var props = new JdbcSessionRepositoryProperties();
		assertThat(props.getIndexedAttributes()).isEmpty();
		assertThat(props.getInitializeSchema()).isEqualTo(DatabaseInitializationMode.EMBEDDED);
		assertThat(props.getDefaultSchemaLocation())
			.isEqualTo("classpath:org/springframework/ai/session/jdbc/schema-@@platform@@.sql");
	}

}
