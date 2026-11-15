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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests for {@link JdbcSessionRepository} against an in-process HSQLDB database, covering
 * the {@code MERGE} upsert of its dialect and the shared dialect scenarios.
 *
 * @author Durga Dasepalli
 */
class JdbcSessionRepositoryHsqldbTests {

	private static final String SCHEMA = "org/springframework/ai/session/jdbc/schema-hsqldb.sql";

	private static EmbeddedDatabase dataSource;

	private JdbcSessionRepository repository;

	@BeforeAll
	static void initSchema() {
		dataSource = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.HSQL)
			.generateUniqueName(true)
			.addScript(SCHEMA)
			.build();
		// The script must be safe to re-run (initialize-schema=always).
		new ResourceDatabasePopulator(new ClassPathResource(SCHEMA)).execute(dataSource);
	}

	@AfterAll
	static void shutdown() {
		dataSource.shutdown();
	}

	@BeforeEach
	void setUp() {
		new JdbcTemplate(dataSource).update("DELETE FROM AI_SESSION");
		this.repository = JdbcSessionRepository.builder().dataSource(dataSource).build();
	}

	@Test
	void dialectIsDetected() {
		assertThat(JdbcSessionRepositoryDialect.from(dataSource))
			.isInstanceOf(HsqldbJdbcSessionRepositoryDialect.class);
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
	void saveRejectsOverLongSessionIdInsteadOfTruncatingIt() {
		// No indexed attribute, so that only the upsert itself can reject the id.
		String sessionId = "s".repeat(255);
		this.repository.save(Session.builder().id(sessionId).metadata("owner", "first").build());

		assertThatExceptionOfType(DataAccessException.class).isThrownBy(
				() -> this.repository.save(Session.builder().id(sessionId + "x").metadata("owner", "second").build()));

		Session existing = this.repository.findById(sessionId);
		assertThat(existing).isNotNull();
		assertThat(existing.metadata()).containsEntry("owner", "first");
		assertThat(new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM AI_SESSION", Integer.class))
			.isEqualTo(1);
	}

	@Test
	void saveIfAbsentRejectsOverLongSessionIdInsteadOfTruncatingIt() {
		String sessionId = "s".repeat(255);
		this.repository.save(Session.builder().id(sessionId).metadata("owner", "first").build());

		assertThatExceptionOfType(DataAccessException.class).isThrownBy(() -> this.repository
			.saveIfAbsent(Session.builder().id(sessionId + "x").metadata("owner", "second").build()));

		assertThat(new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM AI_SESSION", Integer.class))
			.isEqualTo(1);
	}

	@Test
	void lastWindowIsLimited() {
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

	/** The repository contract, run against HSQLDB. */
	@Nested
	class Contract extends AbstractSessionRepositoryContractTests {

		@Override
		protected SessionRepository createRepository() {
			return JdbcSessionRepository.builder().dataSource(dataSource).build();
		}

	}

}
