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

import com.mysql.cj.jdbc.MysqlDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import org.springframework.ai.session.SessionRepository;
import org.springframework.ai.test.session.AbstractSessionRepositoryContractTests;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * Integration tests for {@link JdbcSessionRepository} against MySQL, covering SQL that
 * differs from the H2 / PostgreSQL dialect.
 *
 * @author Christian Tzolov
 */
@Testcontainers
class JdbcSessionRepositoryMysqlIT {

	@Container
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.0");

	private static MysqlDataSource dataSource;

	private JdbcSessionRepository repository;

	@BeforeAll
	static void initSchema() {
		dataSource = new MysqlDataSource();
		dataSource.setUrl(mysql.getJdbcUrl());
		dataSource.setUser(mysql.getUsername());
		dataSource.setPassword(mysql.getPassword());
		ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
				new ClassPathResource("org/springframework/ai/session/jdbc/schema-mysql.sql"));
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

	/** The repository contract, run against MySQL. */
	@Nested
	class Contract extends AbstractSessionRepositoryContractTests {

		@Override
		protected SessionRepository createRepository() {
			return JdbcSessionRepository.builder().dataSource(dataSource).build();
		}

	}

}
