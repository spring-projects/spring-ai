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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import java.util.UUID;

import org.mockito.Answers;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.session.CreateSessionRequest;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.EventType;
import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionAttributes;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.compaction.ChatClientSummarizer;
import org.springframework.ai.session.compaction.WindowCompactionStrategy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Shared assertions for database-specific SQL behaviour, run against every supported
 * database: {@code LIKE}-based filters treat {@code %}, {@code _} and the {@code !}
 * escape character literally, and timestamps are stored as UTC independent of the JVM
 * time zone.
 */
final class DialectScenarios {

	private DialectScenarios() {
	}

	static void timestampsAreStoredAsUtcRegardlessOfJvmTimeZone(JdbcSessionRepository repository,
			JdbcTemplate jdbcTemplate) {
		TimeZone original = TimeZone.getDefault();
		TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
		try {
			Instant instant = Instant.parse("2026-03-08T06:30:00.123456Z");
			String sessionId = UUID.randomUUID().toString();
			repository.save(Session.builder()
				.id(sessionId)
				.metadata(SessionAttributes.PRINCIPAL_NAME, "user-tz")
				.createdAt(instant)
				.expiresAt(instant)
				.build());
			repository.appendEvent(SessionEvent.builder()
				.sessionId(sessionId)
				.timestamp(instant)
				.message(new UserMessage("tz"))
				.build());

			LocalDateTime utcWallClock = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
			assertThat(jdbcTemplate.queryForObject("SELECT created_at FROM AI_SESSION WHERE id = ?",
					LocalDateTime.class, sessionId))
				.isEqualTo(utcWallClock);

			Session found = repository.findById(sessionId);
			assertThat(found.createdAt()).isEqualTo(instant);
			assertThat(found.expiresAt()).isEqualTo(instant);
			assertThat(repository.findEvents(sessionId, EventFilter.all()).get(0).getTimestamp()).isEqualTo(instant);
			// The expiry comparison uses the same UTC wall-clock value
			assertThat(repository.deleteExpiredSessions(instant)).isZero();
			assertThat(repository.deleteExpiredSessions(instant.plusMillis(1))).isEqualTo(1);
		}
		finally {
			TimeZone.setDefault(original);
		}
	}

	private static String newSession(JdbcSessionRepository repository) {
		String sessionId = UUID.randomUUID().toString();
		repository
			.save(Session.builder().id(sessionId).metadata(SessionAttributes.PRINCIPAL_NAME, "user-like").build());
		return sessionId;
	}

	/**
	 * Three recursive summarization passes only append: each pass adds a boundary and its
	 * summary, the earlier log stays byte-for-byte, and the view projected in SQL from
	 * the newest boundary pins the latest stored system message first.
	 */
	static void repeatedSummarizationOnlyAppendsAndProjectsView(JdbcSessionRepository repository) {
		ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
		given(chatClient.prompt().system(anyString()).user(anyString()).call().content()).willReturn("summary 1",
				"summary 2", "summary 3");
		WindowCompactionStrategy strategy = WindowCompactionStrategy.builder()
			.lastEvents(4)
			.summarizer(ChatClientSummarizer.builder(chatClient).build())
			.build();
		SessionService service = DefaultSessionService.builder()
			.sessionRepository(repository)
			.allowSystemMessages(true)
			.build();
		String id = service
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "user-order").build())
			.id();

		appendAll(service, id, "u1", "sys-1", "a1", "u2", "a2", "u3", "a3", "u4", "a4", "u5", "a5");
		service.compact(id, request -> true, strategy);
		List<String> afterPass1 = labels(service.getEvents(id));
		assertThat(afterPass1).containsExactly("u1", "sys-1", "a1", "u2", "a2", "u3", "a3", "u4", "a4", "u5", "a5", "⊥",
				"Σ?", "Σ:summary 1");
		assertThat(labels(service.getEvents(id, EventFilter.active()))).containsExactly("sys-1", "Σ?", "Σ:summary 1",
				"u4", "a4", "u5", "a5");
		appendAll(service, id, "u6", "a6", "u7", "a7");
		service.compact(id, request -> true, strategy);
		List<String> afterPass2 = labels(service.getEvents(id));
		assertThat(afterPass2).startsWith(afterPass1.toArray(String[]::new))
			.endsWith("u6", "a6", "u7", "a7", "⊥", "Σ?", "Σ:summary 2");
		assertThat(labels(service.getEvents(id, EventFilter.active()))).containsExactly("sys-1", "Σ?", "Σ:summary 2",
				"u6", "a6", "u7", "a7");
		appendAll(service, id, "u8", "sys-2", "a8", "u9", "sys-3", "a9");
		service.compact(id, request -> true, strategy);
		List<SessionEvent> log = service.getEvents(id);
		assertThat(labels(log)).startsWith(afterPass2.toArray(String[]::new))
			.endsWith("u8", "sys-2", "a8", "u9", "sys-3", "a9", "⊥", "Σ?", "Σ:summary 3");
		// sys-2 is superseded by sys-3 and covered even though it sits in the kept window
		assertThat(labels(service.getEvents(id, EventFilter.active()))).containsExactly("sys-3", "Σ?", "Σ:summary 3",
				"u8", "a8", "u9", "a9");
	}

	/**
	 * A replayed append inside a caller-managed transaction must not break it: the replay
	 * is detected before inserting, so no duplicate-key error marks the shared
	 * transaction rollback-only (or, on PostgreSQL, aborts it).
	 */
	static void appendReplayInsideCallerTransactionKeepsItUsable(JdbcSessionRepository repository,
			JdbcTemplate jdbcTemplate) {
		String sessionId = newSession(repository);
		SessionEvent event = SessionEvent.builder().sessionId(sessionId).message(new UserMessage("once")).build();
		repository.appendEvent(event);
		long version = repository.getEventVersion(sessionId);
		TransactionTemplate callerTransaction = new TransactionTemplate(
				new DataSourceTransactionManager(Objects.requireNonNull(jdbcTemplate.getDataSource())));

		callerTransaction.executeWithoutResult(status -> {
			repository.appendEvent(event);
			append(repository, sessionId, "after");
		});

		assertThat(texts(repository, sessionId, EventFilter.all())).containsExactly("once", "after");
		// The replay did not count as an appended event
		assertThat(repository.getEventVersion(sessionId)).isEqualTo(version + 1);
	}

	/** Appends messages by label: "u…" user, "a…" assistant, "sys…" system. */
	private static void appendAll(SessionService service, String sessionId, String... labels) {
		for (String label : labels) {
			Message message = label.startsWith("sys") ? new SystemMessage(label)
					: label.startsWith("u") ? new UserMessage(label) : new AssistantMessage(label);
			service.appendMessage(sessionId, message);
		}
	}

	/**
	 * The text, "Σ?" for a synthetic shadow prompt and "Σ:text" for a synthetic summary.
	 */
	private static List<String> labels(List<SessionEvent> events) {
		return events.stream().map(e -> {
			if (e.getType() == EventType.COMPACTION) {
				return "⊥";
			}
			if (!e.isSynthetic()) {
				return e.getMessage().getText();
			}
			return (e.getMessageType() == MessageType.USER) ? "Σ?" : "Σ:" + e.getMessage().getText();
		}).toList();
	}

	private static void append(JdbcSessionRepository repository, String sessionId, String text) {
		repository.appendEvent(SessionEvent.builder().sessionId(sessionId).message(new UserMessage(text)).build());
	}

	private static List<String> texts(JdbcSessionRepository repository, String sessionId, EventFilter filter) {
		return repository.findEvents(sessionId, filter).stream().map(e -> e.getMessage().getText()).toList();
	}

}
