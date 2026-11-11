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

import java.util.List;
import java.util.UUID;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionAttributes;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionRepository;
import org.springframework.ai.test.session.AbstractSessionRepositoryContractTests;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Integration tests for {@link JdbcSessionRepository} against Microsoft SQL Server,
 * covering SQL that differs from the other dialects: the {@code MERGE} upsert, the
 * {@code OFFSET ... FETCH} row limit and the case-sensitive key collation.
 *
 * @author Durga Dasepalli
 * @author Christian Tzolov
 */
@Testcontainers
class JdbcSessionRepositorySqlServerIT {

	@Container
	static final MSSQLServerContainer sqlServer = new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-latest")
		.acceptLicense();

	private static SQLServerDataSource dataSource;

	private JdbcSessionRepository repository;

	@BeforeAll
	static void initSchema() {
		dataSource = new SQLServerDataSource();
		dataSource.setURL(sqlServer.getJdbcUrl());
		dataSource.setUser(sqlServer.getUsername());
		dataSource.setPassword(sqlServer.getPassword());
		ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
				new ClassPathResource("org/springframework/ai/session/jdbc/schema-sqlserver.sql"));
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
	void dialectIsDetected() {
		assertThat(JdbcSessionRepositoryDialect.from(dataSource))
			.isInstanceOf(SqlServerJdbcSessionRepositoryDialect.class);
	}

	@Test
	void saveUpdatesExistingSessionAndKeepsCreatedAt() {
		String sessionId = UUID.randomUUID().toString();
		Session created = this.repository
			.save(Session.builder().id(sessionId).metadata(SessionAttributes.PRINCIPAL_NAME, "user-a").build());

		Session updated = this.repository
			.save(Session.builder().id(sessionId).metadata(SessionAttributes.PRINCIPAL_NAME, "user-b").build());

		assertThat(updated.createdAt()).isEqualTo(created.createdAt());
		assertThat(updated.metadata()).containsEntry(SessionAttributes.PRINCIPAL_NAME, "user-b");
		assertThat(updated.expiresAt()).isNull();
	}

	@Test
	void sessionIdsAreCaseSensitive() {
		String sessionId = "Session-" + UUID.randomUUID();
		this.repository
			.save(Session.builder().id(sessionId).metadata(SessionAttributes.PRINCIPAL_NAME, "user-case").build());

		assertThat(this.repository.findById(sessionId.toLowerCase())).isNull();
		assertThat(this.repository.saveIfAbsent(Session.builder()
			.id(sessionId.toLowerCase())
			.metadata(SessionAttributes.PRINCIPAL_NAME, "user-case")
			.build())).isTrue();
	}

	@Test
	void saveDoesNotUpdateSessionWhoseIdDiffersOnlyInCase() {
		// The MERGE matches on the id: with a case-insensitive comparison the second
		// save would update the first session instead of inserting a new one.
		String sessionId = "Session-" + UUID.randomUUID();
		this.repository.save(Session.builder().id(sessionId).metadata("owner", "first").build());

		Session second = this.repository
			.save(Session.builder().id(sessionId.toLowerCase()).metadata("owner", "second").build());

		assertThat(second.id()).isEqualTo(sessionId.toLowerCase());
		Session first = this.repository.findById(sessionId);
		assertThat(first).isNotNull();
		assertThat(first.id()).isEqualTo(sessionId);
		assertThat(first.metadata()).containsEntry("owner", "first");
		assertThat(new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM AI_SESSION", Integer.class))
			.isEqualTo(2);
	}

	@Test
	void saveRejectsOverLongSessionIdInsteadOfTruncatingIt() {
		// No indexed attribute, so that only the upsert itself can reject the id.
		String sessionId = "s".repeat(255);
		this.repository.save(Session.builder().id(sessionId).metadata("owner", "first").build());

		assertThatExceptionOfType(DataAccessException.class).isThrownBy(
				() -> this.repository.save(Session.builder().id(sessionId + "x").metadata("owner", "second").build()))
			.withMessageContaining("would be truncated");

		Session existing = this.repository.findById(sessionId);
		assertThat(existing).isNotNull();
		assertThat(existing.metadata()).containsEntry("owner", "first");
		assertThat(new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM AI_SESSION", Integer.class))
			.isEqualTo(1);
	}

	@Test
	void saveIfAbsentRejectsOverLongSessionIdInsteadOfTruncatingIt() {
		// Truncated to the column width, the id would be the existing one and the insert
		// would report that the session exists.
		String sessionId = "s".repeat(255);
		this.repository.save(Session.builder().id(sessionId).metadata("owner", "first").build());

		assertThatExceptionOfType(DataAccessException.class)
			.isThrownBy(() -> this.repository
				.saveIfAbsent(Session.builder().id(sessionId + "x").metadata("owner", "second").build()))
			.withMessageContaining("would be truncated");

		assertThat(new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM AI_SESSION", Integer.class))
			.isEqualTo(1);
	}

	@Test
	void sessionIdLongerThanAnyBoundedStringIsRejectedInsteadOfTruncated() {
		// The driver sends a string of more than 4000 characters as NVARCHAR(MAX). Cut to
		// a bounded type, this id would be the existing one followed by spaces, which
		// SQL Server compares equal to it.
		String sessionId = "Session-" + UUID.randomUUID();
		this.repository.save(Session.builder().id(sessionId).metadata("owner", "first").build());
		String overLongId = sessionId + " ".repeat(4000 - sessionId.length()) + "x";

		assertThatExceptionOfType(DataAccessException.class)
			.isThrownBy(
					() -> this.repository.save(Session.builder().id(overLongId).metadata("owner", "second").build()))
			.withMessageContaining("would be truncated");
		assertThatExceptionOfType(DataAccessException.class)
			.isThrownBy(() -> this.repository
				.saveIfAbsent(Session.builder().id(overLongId).metadata("owner", "second").build()))
			.withMessageContaining("would be truncated");

		Session existing = this.repository.findById(sessionId);
		assertThat(existing).isNotNull();
		assertThat(existing.metadata()).containsEntry("owner", "first");
		assertThat(new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM AI_SESSION", Integer.class))
			.isEqualTo(1);
	}

	@Test
	void saveIfAbsentOfExistingIdKeepsCallerTransactionUnderXactAbort() {
		// With XACT_ABORT ON a duplicate-key error rolls the whole transaction back on
		// the server, silently dropping what the caller did before it.
		String existingId = UUID.randomUUID().toString();
		this.repository.save(Session.builder().id(existingId).metadata("owner", "first").build());
		String otherId = UUID.randomUUID().toString();
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		TransactionTemplate callerTransaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

		callerTransaction.executeWithoutResult(status -> {
			jdbcTemplate.execute("SET XACT_ABORT ON");
			this.repository.save(Session.builder().id(otherId).metadata("owner", "other").build());
			assertThat(
					this.repository.saveIfAbsent(Session.builder().id(existingId).metadata("owner", "second").build()))
				.isFalse();
		});

		assertThat(this.repository.findById(otherId)).isNotNull();
		Session existing = this.repository.findById(existingId);
		assertThat(existing).isNotNull();
		assertThat(existing.metadata()).containsEntry("owner", "first");
	}

	@Test
	void lastWindowIsLimitedWithOffsetFetch() {
		String sessionId = UUID.randomUUID().toString();
		this.repository
			.save(Session.builder().id(sessionId).metadata(SessionAttributes.PRINCIPAL_NAME, "user-window").build());
		for (String text : List.of("one", "two", "three", "four")) {
			this.repository
				.appendEvent(SessionEvent.builder().sessionId(sessionId).message(new UserMessage(text)).build());
		}

		List<String> lastTwo = this.repository.findEvents(sessionId, EventFilter.lastN(2))
			.stream()
			.map(e -> e.getMessage().getText())
			.toList();

		assertThat(lastTwo).containsExactly("three", "four");
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

	/** The repository contract, run against SQL Server. */
	@Nested
	class Contract extends AbstractSessionRepositoryContractTests {

		@Override
		protected SessionRepository createRepository() {
			return JdbcSessionRepository.builder().dataSource(dataSource).build();
		}

	}

}
