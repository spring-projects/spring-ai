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
import java.util.Optional;

/**
 * Repository interface for persisting and querying {@link BatchExecution} records.
 * <p>
 * Provides an out-of-the-box {@link InMemoryBatchExecutionRepository} for simple
 * use-cases and development. For production, users may implement this interface with a
 * database-backed store (e.g., Spring Data JPA, JDBC) to survive application restarts.
 *
 * @author Yasin Akbas
 * @since 2.0.0
 * @see InMemoryBatchExecutionRepository
 * @see BatchExecution
 */
public interface BatchExecutionRepository {

	/**
	 * Saves a batch execution record. If a record with the same batch ID already exists,
	 * it is replaced.
	 * @param execution the batch execution to save
	 */
	void save(BatchExecution execution);

	/**
	 * Finds a batch execution by its OpenAI batch ID.
	 * @param batchId the OpenAI batch ID
	 * @return the batch execution, or empty if not found
	 */
	Optional<BatchExecution> findById(String batchId);

	/**
	 * Finds all batch executions with the given status.
	 * @param status the status to filter by
	 * @return a list of matching batch executions
	 */
	List<BatchExecution> findByStatus(BatchExecutionStatus status);

	/**
	 * Finds all batch executions that are still in progress and should be checked for
	 * completion. This includes executions in {@link BatchExecutionStatus#SUBMITTED},
	 * {@link BatchExecutionStatus#VALIDATING}, {@link BatchExecutionStatus#IN_PROGRESS},
	 * and {@link BatchExecutionStatus#FINALIZING} states.
	 * @return a list of non-terminal batch executions
	 */
	List<BatchExecution> findPendingExecutions();

	/**
	 * Deletes a batch execution record.
	 * @param batchId the OpenAI batch ID to delete
	 */
	void deleteById(String batchId);

}
