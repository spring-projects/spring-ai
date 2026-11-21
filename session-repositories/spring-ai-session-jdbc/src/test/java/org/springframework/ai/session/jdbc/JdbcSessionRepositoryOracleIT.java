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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionAttributes;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionRepository;
import org.springframework.ai.session.compaction.WindowCompactionStrategy;
import org.springframework.ai.test.session.AbstractSessionRepositoryContractTests;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Integration tests for {@link JdbcSessionRepository} against Oracle Database, covering
 * SQL that differs from the other dialects: the {@code MERGE} upsert, the
 * {@code FETCH FIRST} row limit and the {@code CLOB} columns.
 *
 * @author Eunji Park
 */
@Testcontainers
class JdbcSessionRepositoryOracleIT {

	@Container
	static final OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:slim-faststart");

	/** Oracle allows at most 1000 items in an {@code IN} list. */
	private static final int MORE_THAN_IN_LIST_ITEMS = 1500;

	private static DriverManagerDataSource dataSource;

	private JdbcSessionRepository repository;

	@BeforeAll
	static void initSchema() {
		dataSource = new DriverManagerDataSource(oracle.getJdbcUrl(), oracle.getUsername(), oracle.getPassword());
		ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
				new ClassPathResource("org/springframework/ai/session/jdbc/schema-oracle.sql"));
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
			.isInstanceOf(OracleJdbcSessionRepositoryDialect.class);
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
	void upsertStoresMetadataLargerThanVarchar2() {
		// Larger than the 32767-byte limit of a VARCHAR2 bind, so the MERGE must carry it
		// into the CLOB column
		String large = "x".repeat(100_000);
		String sessionId = UUID.randomUUID().toString();

		this.repository.save(Session.builder().id(sessionId).metadata("large", large).build());
		Session updated = this.repository.save(Session.builder().id(sessionId).metadata("large", large + "y").build());

		assertThat(updated.metadata()).containsEntry("large", large + "y");
	}

	@Test
	void eventsStoreMessagesLargerThanVarchar2() {
		String large = "x".repeat(100_000);
		String sessionId = UUID.randomUUID().toString();
		this.repository.save(Session.builder().id(sessionId).build());

		// One event alone, then a batch
		this.repository
			.appendEvent(SessionEvent.builder().sessionId(sessionId).message(new UserMessage(large + "1")).build());
		long version = this.repository.getEventVersion(sessionId);
		this.repository.appendEvents(sessionId,
				List.of(SessionEvent.builder().sessionId(sessionId).message(new UserMessage(large + "2")).build()),
				version);

		assertThat(this.repository.findEvents(sessionId, EventFilter.all())).extracting(e -> e.getMessage().getText())
			.containsExactly(large + "1", large + "2");
	}

	@Test
	void concurrentSaveOfNewSessionUpdatesInsteadOfFailing() throws Exception {
		String sessionId = UUID.randomUUID().toString();
		CompletableFuture<Session> second = new CompletableFuture<>();
		new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(status -> {
			// The first save inserts the row and holds it uncommitted
			this.repository.save(Session.builder().id(sessionId).metadata("writer", "first").build());
			// The second save does not see the row, takes the insert branch and waits on
			// the primary key until the first commits
			CompletableFuture.runAsync(() -> {
				try {
					second.complete(
							this.repository.save(Session.builder().id(sessionId).metadata("writer", "second").build()));
				}
				catch (RuntimeException ex) {
					second.completeExceptionally(ex);
				}
			});
			assertThat(second).failsWithin(2, TimeUnit.SECONDS)
				.withThrowableThat()
				.isInstanceOf(TimeoutException.class);
		});

		assertThat(second.get(30, TimeUnit.SECONDS).metadata()).containsEntry("writer", "second");
	}

	@Test
	void lastWindowIsLimitedWithFetchFirst() {
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
	void saveDoesNotUpdateSessionWhoseIdDiffersOnlyInCase() {
		String sessionId = "Session-" + UUID.randomUUID();
		this.repository.save(Session.builder().id(sessionId).metadata("owner", "first").build());

		this.repository.save(Session.builder().id(sessionId.toLowerCase()).metadata("owner", "second").build());

		Session first = this.repository.findById(sessionId);
		assertThat(first).isNotNull();
		assertThat(first.metadata()).containsEntry("owner", "first");
		assertThat(countSessions()).isEqualTo(2);
	}

	@Test
	void sessionIdsDifferingByTrailingSpacesAreDistinct() {
		// VARCHAR2 compares without padding, unlike SQL Server, MySQL and MariaDB
		String sessionId = "Session-" + UUID.randomUUID();
		this.repository.save(Session.builder().id(sessionId).metadata("owner", "first").build());

		this.repository.save(Session.builder().id(sessionId + " ").metadata("owner", "second").build());

		assertThat(this.repository.findById(sessionId)).isNotNull()
			.satisfies(session -> assertThat(session.metadata()).containsEntry("owner", "first"));
		assertThat(countSessions()).isEqualTo(2);
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
		assertThat(countSessions()).isEqualTo(1);
	}

	@Test
	void saveIfAbsentRejectsOverLongSessionIdInsteadOfTruncatingIt() {
		String sessionId = "s".repeat(255);
		this.repository.save(Session.builder().id(sessionId).metadata("owner", "first").build());

		assertThatExceptionOfType(DataAccessException.class).isThrownBy(() -> this.repository
			.saveIfAbsent(Session.builder().id(sessionId + "x").metadata("owner", "second").build()));

		assertThat(countSessions()).isEqualTo(1);
	}

	@Test
	void saveIfAbsentOfExistingIdKeepsCallerTransaction() {
		// Oracle rolls back only the failed statement, so the plain INSERT of the default
		// insert-if-absent leaves what the caller did before it in place.
		String existingId = UUID.randomUUID().toString();
		this.repository.save(Session.builder().id(existingId).metadata("owner", "first").build());
		String otherId = UUID.randomUUID().toString();

		new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(status -> {
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
	void appendEventsAcceptsBatchOfMoreEventsThanInListItems() {
		// The ids of the batch are looked up before the insert.
		String sessionId = UUID.randomUUID().toString();
		this.repository
			.save(Session.builder().id(sessionId).metadata(SessionAttributes.PRINCIPAL_NAME, "user-batch").build());
		List<SessionEvent> batch = userEvents(sessionId, MORE_THAN_IN_LIST_ITEMS);

		assertThat(this.repository.appendEvents(sessionId, batch, 0)).isTrue();

		assertThat(this.repository.findEvents(sessionId, EventFilter.all())).extracting(SessionEvent::getId)
			.containsExactlyElementsOf(batch.stream().map(SessionEvent::getId).toList());
	}

	@Test
	void activeViewReadsBoundaryNamingMoreIdsThanInListItems() {
		// The events a compaction boundary names are read by id.
		String sessionId = UUID.randomUUID().toString();
		this.repository
			.save(Session.builder().id(sessionId).metadata(SessionAttributes.PRINCIPAL_NAME, "user-view").build());
		this.repository.appendEvents(sessionId, userEvents(sessionId, MORE_THAN_IN_LIST_ITEMS + 100), 0);
		DefaultSessionService service = DefaultSessionService.builder().sessionRepository(this.repository).build();
		service.compact(sessionId, request -> true,
				WindowCompactionStrategy.builder().lastEvents(MORE_THAN_IN_LIST_ITEMS).build());
		this.repository.appendEvent(
				SessionEvent.builder().sessionId(sessionId).message(new UserMessage("after compaction")).build());

		List<SessionEvent> active = this.repository.findEvents(sessionId, EventFilter.active());

		List<SessionEvent> log = this.repository.findEvents(sessionId, EventFilter.all());
		assertThat(active).hasSizeGreaterThan(MORE_THAN_IN_LIST_ITEMS)
			.extracting(SessionEvent::getId)
			.containsExactlyElementsOf(EventFilter.active().apply(log).stream().map(SessionEvent::getId).toList());
		assertThat(active.get(active.size() - 1).getMessage().getText()).isEqualTo("after compaction");
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

	private static int countSessions() {
		Integer count = new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM AI_SESSION", Integer.class);
		return count != null ? count : 0;
	}

	private static List<SessionEvent> userEvents(String sessionId, int count) {
		List<SessionEvent> events = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			events.add(SessionEvent.builder().sessionId(sessionId).message(new UserMessage("message " + i)).build());
		}
		return events;
	}

	/** The repository contract, run against Oracle Database. */
	@Nested
	class Contract extends AbstractSessionRepositoryContractTests {

		@Override
		protected SessionRepository createRepository() {
			return JdbcSessionRepository.builder().dataSource(dataSource).build();
		}

	}

}
