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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.EventType;
import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionAttributes;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionRepository;
import org.springframework.ai.session.compaction.CompactionBoundary;
import org.springframework.ai.test.session.AbstractSessionRepositoryContractTests;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link JdbcSessionRepository} against PostgreSQL, covering
 * behaviour that depends on real row locking and cannot be reproduced on H2.
 *
 * @author Christian Tzolov
 */
@Testcontainers
class JdbcSessionRepositoryPostgresqlIT {

	@Container
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

	private static PGSimpleDataSource dataSource;

	private JdbcSessionRepository repository;

	private JdbcTemplate jdbcTemplate;

	@BeforeAll
	static void initSchema() {
		dataSource = new PGSimpleDataSource();
		dataSource.setUrl(postgres.getJdbcUrl());
		dataSource.setUser(postgres.getUsername());
		dataSource.setPassword(postgres.getPassword());
		new ResourceDatabasePopulator(
				new ClassPathResource("org/springframework/ai/session/jdbc/schema-postgresql.sql"))
			.execute(dataSource);
	}

	@BeforeEach
	void setUp() {
		this.jdbcTemplate = new JdbcTemplate(dataSource);
		this.jdbcTemplate.update("DELETE FROM AI_SESSION");
		this.repository = JdbcSessionRepository.builder().dataSource(dataSource).build();
	}

	@Test
	void appendReplayInsideCallerTransactionKeepsItUsable() {
		DialectScenarios.appendReplayInsideCallerTransactionKeepsItUsable(this.repository, this.jdbcTemplate);
	}

	@Test
	void repeatedSummarizationOnlyAppendsAndProjectsView() {
		DialectScenarios.repeatedSummarizationOnlyAppendsAndProjectsView(this.repository);
	}

	@Test
	void timestampsAreStoredAsUtcRegardlessOfJvmTimeZone() {
		DialectScenarios.timestampsAreStoredAsUtcRegardlessOfJvmTimeZone(this.repository, this.jdbcTemplate);
	}

	@Test
	void appendConcurrentWithCompactionIsOrderedAfterRetainedEvents() throws Exception {
		String sessionId = UUID.randomUUID().toString();
		this.repository
			.save(Session.builder().id(sessionId).metadata(SessionAttributes.PRINCIPAL_NAME, "user-pg").build());

		SessionEvent oldUser = event(sessionId, new UserMessage("old question"));
		SessionEvent oldAssistant = event(sessionId, new AssistantMessage("old answer"));
		this.repository.appendEvent(oldUser);
		this.repository.appendEvent(oldAssistant);
		long version = this.repository.getEventVersion(sessionId);

		SessionEvent summary = SessionEvent.builder()
			.sessionId(sessionId)
			.message(new AssistantMessage("summary"))
			.metadata(SessionEvent.METADATA_SYNTHETIC, true)
			.build();
		SessionEvent newest = event(sessionId, new UserMessage("newest question"));

		// The compacting transaction takes the session row lock first, then lets the
		// concurrent append run into it before the compaction rewrites the active window.
		TransactionTemplate outerTx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
		CompletableFuture<Void> append = new CompletableFuture<>();
		Boolean compacted = outerTx.execute(status -> {
			this.jdbcTemplate.queryForList("SELECT id FROM AI_SESSION WHERE id = ? FOR UPDATE", sessionId);
			CompletableFuture.runAsync(() -> this.repository.appendEvent(newest)).whenComplete((r, ex) -> {
				if (ex != null) {
					append.completeExceptionally(ex);
				}
				else {
					append.complete(null);
				}
			});
			awaitBlockedOnLock();
			// A boundary that covers the old question, keeps the old answer and stands
			// the
			// summary in for it; the blocked append lands after the summary.
			String boundaryId = UUID.randomUUID().toString();
			CompactionBoundary boundary = new CompactionBoundary(boundaryId, List.of(), List.of(summary.getId()),
					List.of(oldAssistant.getId()), 1, 0, "test");
			SessionEvent boundaryEvent = SessionEvent.builder()
				.id(boundaryId)
				.sessionId(sessionId)
				.type(EventType.COMPACTION)
				.payload(boundary.toPayload())
				.build();
			return this.repository.appendEvents(sessionId, List.of(boundaryEvent, summary), version);
		});

		assertThat(compacted).isTrue();
		append.get(30, TimeUnit.SECONDS);

		List<String> active = this.repository.findEvents(sessionId, EventFilter.active())
			.stream()
			.map(e -> e.getMessage().getText())
			.toList();
		assertThat(active).containsExactly("summary", "old answer", "newest question");
	}

	private void awaitBlockedOnLock() {
		JdbcTemplate monitor = new JdbcTemplate(dataSource);
		long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
		while (System.nanoTime() < deadline) {
			// pg_locks is read live; pg_stat_activity would be snapshotted for the
			// duration of the enclosing transaction.
			Integer waiting = monitor.queryForObject("SELECT COUNT(*) FROM pg_locks WHERE NOT granted", Integer.class);
			if (waiting != null && waiting > 0) {
				return;
			}
			sleep(20);
		}
		throw new AssertionError("concurrent append never blocked on the session row lock");
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

	private static SessionEvent event(String sessionId, org.springframework.ai.chat.messages.Message message) {
		return SessionEvent.builder().sessionId(sessionId).timestamp(Instant.now()).message(message).build();
	}

	/** The repository contract, run against PostgreSQL. */
	@Nested
	class Contract extends AbstractSessionRepositoryContractTests {

		@Override
		protected SessionRepository createRepository() {
			return JdbcSessionRepository.builder().dataSource(dataSource).build();
		}

	}

}
