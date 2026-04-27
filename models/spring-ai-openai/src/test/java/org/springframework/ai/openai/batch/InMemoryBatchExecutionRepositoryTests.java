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

package org.springframework.ai.openai.batch;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link InMemoryBatchExecutionRepository}.
 *
 * @author Yasin Akbas
 */
class InMemoryBatchExecutionRepositoryTests {

	@Test
	void shouldSaveAndFindById() {
		InMemoryBatchExecutionRepository repository = new InMemoryBatchExecutionRepository();
		BatchExecution execution = new BatchExecution("batch_1", "/v1/chat/completions", BatchExecutionStatus.SUBMITTED,
				10, "file-input-1");

		repository.save(execution);

		assertThat(repository.findById("batch_1")).isPresent();
		assertThat(repository.findById("batch_1").get().getEndpoint()).isEqualTo("/v1/chat/completions");
	}

	@Test
	void shouldReturnEmptyForMissingBatch() {
		InMemoryBatchExecutionRepository repository = new InMemoryBatchExecutionRepository();
		assertThat(repository.findById("nonexistent")).isEmpty();
	}

	@Test
	void shouldFindByStatus() {
		InMemoryBatchExecutionRepository repository = new InMemoryBatchExecutionRepository();
		repository
			.save(new BatchExecution("batch_1", "/v1/chat/completions", BatchExecutionStatus.SUBMITTED, 10, "file-1"));
		repository
			.save(new BatchExecution("batch_2", "/v1/embeddings", BatchExecutionStatus.IN_PROGRESS, 20, "file-2"));
		repository
			.save(new BatchExecution("batch_3", "/v1/chat/completions", BatchExecutionStatus.SUBMITTED, 5, "file-3"));

		List<BatchExecution> submitted = repository.findByStatus(BatchExecutionStatus.SUBMITTED);
		assertThat(submitted).hasSize(2);
		assertThat(submitted).extracting(BatchExecution::getBatchId).containsExactlyInAnyOrder("batch_1", "batch_3");
	}

	@Test
	void shouldFindPendingExecutions() {
		InMemoryBatchExecutionRepository repository = new InMemoryBatchExecutionRepository();
		repository
			.save(new BatchExecution("batch_1", "/v1/chat/completions", BatchExecutionStatus.SUBMITTED, 10, "file-1"));
		repository
			.save(new BatchExecution("batch_2", "/v1/embeddings", BatchExecutionStatus.IN_PROGRESS, 20, "file-2"));

		BatchExecution completed = new BatchExecution("batch_3", "/v1/chat/completions", BatchExecutionStatus.SUBMITTED,
				5, "file-3");
		completed.setStatus(BatchExecutionStatus.RESULTS_PROCESSED);
		repository.save(completed);

		BatchExecution failed = new BatchExecution("batch_4", "/v1/chat/completions", BatchExecutionStatus.SUBMITTED, 3,
				"file-4");
		failed.setStatus(BatchExecutionStatus.FAILED);
		repository.save(failed);

		List<BatchExecution> pending = repository.findPendingExecutions();
		assertThat(pending).hasSize(2);
		assertThat(pending).extracting(BatchExecution::getBatchId).containsExactlyInAnyOrder("batch_1", "batch_2");
	}

	@Test
	void shouldDeleteById() {
		InMemoryBatchExecutionRepository repository = new InMemoryBatchExecutionRepository();
		repository
			.save(new BatchExecution("batch_1", "/v1/chat/completions", BatchExecutionStatus.SUBMITTED, 10, "file-1"));

		repository.deleteById("batch_1");

		assertThat(repository.findById("batch_1")).isEmpty();
	}

	@Test
	void shouldReplaceOnSave() {
		InMemoryBatchExecutionRepository repository = new InMemoryBatchExecutionRepository();
		BatchExecution execution = new BatchExecution("batch_1", "/v1/chat/completions", BatchExecutionStatus.SUBMITTED,
				10, "file-1");
		repository.save(execution);

		execution.setStatus(BatchExecutionStatus.IN_PROGRESS);
		repository.save(execution);

		assertThat(repository.findById("batch_1").get().getStatus()).isEqualTo(BatchExecutionStatus.IN_PROGRESS);
	}

	@Test
	void shouldTrackTerminalStatuses() {
		assertThat(BatchExecutionStatus.RESULTS_PROCESSED.isTerminal()).isTrue();
		assertThat(BatchExecutionStatus.FAILED.isTerminal()).isTrue();
		assertThat(BatchExecutionStatus.EXPIRED.isTerminal()).isTrue();
		assertThat(BatchExecutionStatus.CANCELLED.isTerminal()).isTrue();
		assertThat(BatchExecutionStatus.SUBMITTED.isTerminal()).isFalse();
		assertThat(BatchExecutionStatus.IN_PROGRESS.isTerminal()).isFalse();
		assertThat(BatchExecutionStatus.VALIDATING.isTerminal()).isFalse();
		assertThat(BatchExecutionStatus.FINALIZING.isTerminal()).isFalse();
		assertThat(BatchExecutionStatus.COMPLETED.isTerminal()).isFalse();
	}

}
