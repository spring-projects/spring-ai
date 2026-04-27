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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.util.Assert;

/**
 * An in-memory implementation of {@link BatchExecutionRepository} backed by a
 * {@link ConcurrentHashMap}.
 * <p>
 * Suitable for development, testing, and single-instance deployments where batch
 * execution tracking does not need to survive application restarts. For production
 * use-cases with multiple instances or restart resilience, provide a database-backed
 * implementation.
 *
 * @author Yasin Akbas
 * @since 2.0.0
 */
public final class InMemoryBatchExecutionRepository implements BatchExecutionRepository {

	private final Map<String, BatchExecution> store = new ConcurrentHashMap<>();

	@Override
	public void save(BatchExecution execution) {
		Assert.notNull(execution, "execution must not be null");
		this.store.put(execution.getBatchId(), execution);
	}

	@Override
	public Optional<BatchExecution> findById(String batchId) {
		Assert.hasText(batchId, "batchId must not be blank");
		return Optional.ofNullable(this.store.get(batchId));
	}

	@Override
	public List<BatchExecution> findByStatus(BatchExecutionStatus status) {
		Assert.notNull(status, "status must not be null");
		return this.store.values().stream().filter(e -> e.getStatus() == status).toList();
	}

	@Override
	public List<BatchExecution> findPendingExecutions() {
		return this.store.values().stream().filter(e -> !e.getStatus().isTerminal()).toList();
	}

	@Override
	public void deleteById(String batchId) {
		Assert.hasText(batchId, "batchId must not be blank");
		this.store.remove(batchId);
	}

}
