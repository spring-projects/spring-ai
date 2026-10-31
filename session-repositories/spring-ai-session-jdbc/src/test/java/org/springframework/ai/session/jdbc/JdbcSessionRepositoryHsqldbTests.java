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

package org.springframework.ai.session.jdbc;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.ai.session.SessionRepository;
import org.springframework.ai.test.session.AbstractSessionRepositoryContractTests;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JdbcSessionRepository} against an in-process HSQLDB database, which
 * runs the HSQLDB dialect and schema.
 *
 * @author Rojan Francis
 */
class JdbcSessionRepositoryHsqldbTests {

	private static DriverManagerDataSource dataSource;

	private JdbcSessionRepository repository;

	@BeforeAll
	static void initSchema() {
		dataSource = new DriverManagerDataSource("jdbc:hsqldb:mem:sessiontest", "SA", "");
		ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
				new ClassPathResource("org/springframework/ai/session/jdbc/schema-hsqldb.sql"));
		populator.execute(dataSource);
		// The script must be safe to re-run (initialize-schema=always).
		populator.execute(dataSource);
	}

	@BeforeEach
	void setUp() {
		new JdbcTemplate(dataSource).update("DELETE FROM AI_SESSION");
		this.repository = JdbcSessionRepository.builder().dataSource(dataSource).build();
	}

	@Test
	void dialectIsDetectedFromTheDatabase() {
		assertThat(JdbcSessionRepositoryDialect.from(dataSource))
			.isInstanceOf(HsqldbJdbcSessionRepositoryDialect.class);
	}

	@Test
	void timestampsAreStoredAsUtcRegardlessOfJvmTimeZone() {
		DialectScenarios.timestampsAreStoredAsUtcRegardlessOfJvmTimeZone(this.repository, new JdbcTemplate(dataSource));
	}

	@Test
	void appendReplayInsideCallerTransactionKeepsItUsable() {
		DialectScenarios.appendReplayInsideCallerTransactionKeepsItUsable(this.repository,
				new JdbcTemplate(dataSource));
	}

	@Test
	void repeatedSummarizationOnlyAppendsAndProjectsView() {
		DialectScenarios.repeatedSummarizationOnlyAppendsAndProjectsView(this.repository);
	}

	/** The repository contract, run against HSQLDB. */
	@Nested
	class Contract extends AbstractSessionRepositoryContractTests {

		@Override
		protected SessionRepository createRepository() {
			return JdbcSessionRepository.builder().dataSource(dataSource).build();
		}

	}

}
