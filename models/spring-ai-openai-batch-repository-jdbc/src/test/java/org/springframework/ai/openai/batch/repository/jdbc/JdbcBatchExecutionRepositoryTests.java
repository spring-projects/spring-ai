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

package org.springframework.ai.openai.batch.repository.jdbc;

import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.openai.batch.BatchExecution;
import org.springframework.ai.openai.batch.BatchExecutionStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JdbcBatchExecutionRepository} using H2 in-memory database.
 *
 * @author Yasin Akbas
 * @since 2.0.0
 */
class JdbcBatchExecutionRepositoryTests {

	private JdbcBatchExecutionRepository repository;

	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		DataSource dataSource = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
			.generateUniqueName(true)
			.addScript("org/springframework/ai/openai/batch/repository/jdbc/schema-h2.sql")
			.build();
		this.jdbcTemplate = new JdbcTemplate(dataSource);
		this.repository = JdbcBatchExecutionRepository.builder().dataSource(dataSource).build();
	}

	@Test
	void saveAndFindById() {
		BatchExecution execution = new BatchExecution("batch-1", "/v1/chat/completions", BatchExecutionStatus.SUBMITTED,
				10, "file-abc123");

		this.repository.save(execution);

		Optional<BatchExecution> found = this.repository.findById("batch-1");
		assertThat(found).isPresent();
		assertThat(found.get().getBatchId()).isEqualTo("batch-1");
		assertThat(found.get().getEndpoint()).isEqualTo("/v1/chat/completions");
		assertThat(found.get().getStatus()).isEqualTo(BatchExecutionStatus.SUBMITTED);
		assertThat(found.get().getRequestCount()).isEqualTo(10);
		assertThat(found.get().getInputFileId()).isEqualTo("file-abc123");
		assertThat(found.get().getCreatedAt()).isNotNull();
		assertThat(found.get().getUpdatedAt()).isNotNull();
	}

	@Test
	void upsertUpdateExistingRecord() {
		BatchExecution execution = new BatchExecution("batch-1", "/v1/chat/completions", BatchExecutionStatus.SUBMITTED,
				10, "file-abc123");
		this.repository.save(execution);

		execution.setStatus(BatchExecutionStatus.IN_PROGRESS);
		this.repository.save(execution);

		Optional<BatchExecution> found = this.repository.findById("batch-1");
		assertThat(found).isPresent();
		assertThat(found.get().getStatus()).isEqualTo(BatchExecutionStatus.IN_PROGRESS);

		// Verify only one record exists
		Integer count = this.jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM SPRING_AI_BATCH_EXECUTION WHERE batch_id = ?", Integer.class, "batch-1");
		assertThat(count).isEqualTo(1);
	}

	@Test
	void findByStatus() {
		this.repository
			.save(new BatchExecution("batch-1", "/v1/chat/completions", BatchExecutionStatus.SUBMITTED, 5, "file-1"));
		this.repository
			.save(new BatchExecution("batch-2", "/v1/chat/completions", BatchExecutionStatus.IN_PROGRESS, 3, "file-2"));
		this.repository
			.save(new BatchExecution("batch-3", "/v1/chat/completions", BatchExecutionStatus.SUBMITTED, 7, "file-3"));

		List<BatchExecution> submitted = this.repository.findByStatus(BatchExecutionStatus.SUBMITTED);
		assertThat(submitted).hasSize(2);
		assertThat(submitted).extracting(BatchExecution::getBatchId).containsExactlyInAnyOrder("batch-1", "batch-3");

		List<BatchExecution> inProgress = this.repository.findByStatus(BatchExecutionStatus.IN_PROGRESS);
		assertThat(inProgress).hasSize(1);
		assertThat(inProgress.get(0).getBatchId()).isEqualTo("batch-2");
	}

	@Test
	void findPendingExecutions() {
		// Non-terminal statuses
		this.repository
			.save(new BatchExecution("batch-1", "/v1/chat/completions", BatchExecutionStatus.SUBMITTED, 5, "file-1"));
		this.repository
			.save(new BatchExecution("batch-2", "/v1/chat/completions", BatchExecutionStatus.IN_PROGRESS, 3, "file-2"));
		this.repository
			.save(new BatchExecution("batch-3", "/v1/chat/completions", BatchExecutionStatus.VALIDATING, 2, "file-3"));
		this.repository
			.save(new BatchExecution("batch-4", "/v1/chat/completions", BatchExecutionStatus.COMPLETED, 4, "file-4"));

		// Terminal statuses
		this.repository.save(new BatchExecution("batch-5", "/v1/chat/completions",
				BatchExecutionStatus.RESULTS_PROCESSED, 6, "file-5"));
		this.repository
			.save(new BatchExecution("batch-6", "/v1/chat/completions", BatchExecutionStatus.FAILED, 1, "file-6"));
		this.repository
			.save(new BatchExecution("batch-7", "/v1/chat/completions", BatchExecutionStatus.EXPIRED, 8, "file-7"));
		this.repository
			.save(new BatchExecution("batch-8", "/v1/chat/completions", BatchExecutionStatus.CANCELLED, 9, "file-8"));

		List<BatchExecution> pending = this.repository.findPendingExecutions();
		assertThat(pending).hasSize(4);
		assertThat(pending).extracting(BatchExecution::getBatchId)
			.containsExactlyInAnyOrder("batch-1", "batch-2", "batch-3", "batch-4");
	}

	@Test
	void deleteById() {
		this.repository
			.save(new BatchExecution("batch-1", "/v1/chat/completions", BatchExecutionStatus.SUBMITTED, 5, "file-1"));

		assertThat(this.repository.findById("batch-1")).isPresent();

		this.repository.deleteById("batch-1");

		assertThat(this.repository.findById("batch-1")).isEmpty();
	}

	@Test
	void findByIdReturnsEmptyForMissing() {
		Optional<BatchExecution> found = this.repository.findById("nonexistent");
		assertThat(found).isEmpty();
	}

}
