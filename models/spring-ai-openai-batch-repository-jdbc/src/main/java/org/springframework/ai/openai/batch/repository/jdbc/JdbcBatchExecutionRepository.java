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

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.openai.batch.BatchExecution;
import org.springframework.ai.openai.batch.BatchExecutionRepository;
import org.springframework.ai.openai.batch.BatchExecutionStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

/**
 * An implementation of {@link BatchExecutionRepository} for JDBC.
 *
 * @author Yasin Akbas
 * @since 2.0.0
 */
public final class JdbcBatchExecutionRepository implements BatchExecutionRepository {

	private final JdbcTemplate jdbcTemplate;

	private final TransactionTemplate transactionTemplate;

	private final JdbcBatchExecutionRepositoryDialect dialect;

	private static final BatchExecutionRowMapper ROW_MAPPER = new BatchExecutionRowMapper();

	private JdbcBatchExecutionRepository(JdbcTemplate jdbcTemplate, JdbcBatchExecutionRepositoryDialect dialect,
			@Nullable PlatformTransactionManager txManager) {
		Assert.notNull(jdbcTemplate, "jdbcTemplate cannot be null");
		Assert.notNull(dialect, "dialect cannot be null");
		this.jdbcTemplate = jdbcTemplate;
		this.dialect = dialect;
		if (txManager == null) {
			Assert.state(jdbcTemplate.getDataSource() != null, "jdbcTemplate dataSource cannot be null");
			txManager = new DataSourceTransactionManager(jdbcTemplate.getDataSource());
		}
		this.transactionTemplate = new TransactionTemplate(txManager);
	}

	@Override
	public void save(BatchExecution execution) {
		Assert.notNull(execution, "execution cannot be null");
		this.transactionTemplate.execute(status -> {
			this.jdbcTemplate.update(this.dialect.getUpsertSql(), execution.getBatchId(), execution.getEndpoint(),
					execution.getStatus().name(), execution.getRequestCount(), execution.getInputFileId(),
					Timestamp.from(execution.getCreatedAt()), Timestamp.from(execution.getUpdatedAt()));
			return null;
		});
	}

	@Override
	public Optional<BatchExecution> findById(String batchId) {
		Assert.hasText(batchId, "batchId cannot be null or empty");
		List<BatchExecution> results = this.jdbcTemplate.query(this.dialect.getSelectByIdSql(), ROW_MAPPER, batchId);
		return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
	}

	@Override
	public List<BatchExecution> findByStatus(BatchExecutionStatus status) {
		Assert.notNull(status, "status cannot be null");
		return this.jdbcTemplate.query(this.dialect.getSelectByStatusSql(), ROW_MAPPER, status.name());
	}

	@Override
	public List<BatchExecution> findPendingExecutions() {
		return this.jdbcTemplate.query(this.dialect.getSelectPendingExecutionsSql(), ROW_MAPPER);
	}

	@Override
	public void deleteById(String batchId) {
		Assert.hasText(batchId, "batchId cannot be null or empty");
		this.jdbcTemplate.update(this.dialect.getDeleteByIdSql(), batchId);
	}

	public static Builder builder() {
		return new Builder();
	}

	private static class BatchExecutionRowMapper implements RowMapper<BatchExecution> {

		@Override
		public BatchExecution mapRow(ResultSet rs, int rowNum) throws SQLException {
			String batchId = rs.getString("batch_id");
			String endpoint = rs.getString("endpoint");
			BatchExecutionStatus status = BatchExecutionStatus.valueOf(rs.getString("status"));
			int requestCount = rs.getInt("request_count");
			String inputFileId = rs.getString("input_file_id");
			Timestamp createdAtTs = rs.getTimestamp("created_at");
			Timestamp updatedAtTs = rs.getTimestamp("updated_at");

			BatchExecution execution = new BatchExecution(batchId, endpoint, status, requestCount, inputFileId);

			// Use reflection to set the persisted timestamps since BatchExecution
			// sets createdAt/updatedAt to Instant.now() in its constructor.
			if (createdAtTs != null) {
				setFieldValue(execution, "createdAt", createdAtTs.toInstant());
			}
			if (updatedAtTs != null) {
				setFieldValue(execution, "updatedAt", updatedAtTs.toInstant());
			}

			return execution;
		}

		private static void setFieldValue(BatchExecution execution, String fieldName, Instant value) {
			Field field = ReflectionUtils.findField(BatchExecution.class, fieldName);
			if (field != null) {
				ReflectionUtils.makeAccessible(field);
				ReflectionUtils.setField(field, execution, value);
			}
		}

	}

	public static final class Builder {

		private @Nullable JdbcTemplate jdbcTemplate;

		private @Nullable JdbcBatchExecutionRepositoryDialect dialect;

		private @Nullable DataSource dataSource;

		private @Nullable PlatformTransactionManager platformTransactionManager;

		private static final Logger logger = LoggerFactory.getLogger(Builder.class);

		private Builder() {
		}

		public Builder jdbcTemplate(JdbcTemplate jdbcTemplate) {
			this.jdbcTemplate = jdbcTemplate;
			return this;
		}

		public Builder dialect(JdbcBatchExecutionRepositoryDialect dialect) {
			this.dialect = dialect;
			return this;
		}

		public Builder dataSource(DataSource dataSource) {
			this.dataSource = dataSource;
			return this;
		}

		public Builder transactionManager(PlatformTransactionManager txManager) {
			this.platformTransactionManager = txManager;
			return this;
		}

		public JdbcBatchExecutionRepository build() {
			DataSource effectiveDataSource = resolveDataSource();
			JdbcBatchExecutionRepositoryDialect effectiveDialect = resolveDialect(effectiveDataSource);
			return new JdbcBatchExecutionRepository(resolveJdbcTemplate(), effectiveDialect,
					this.platformTransactionManager);
		}

		private JdbcTemplate resolveJdbcTemplate() {
			if (this.jdbcTemplate != null) {
				return this.jdbcTemplate;
			}
			if (this.dataSource != null) {
				return new JdbcTemplate(this.dataSource);
			}
			throw new IllegalArgumentException("DataSource must be set (either via dataSource() or jdbcTemplate())");
		}

		private DataSource resolveDataSource() {
			if (this.dataSource != null) {
				return this.dataSource;
			}
			if (this.jdbcTemplate != null && this.jdbcTemplate.getDataSource() != null) {
				return this.jdbcTemplate.getDataSource();
			}
			throw new IllegalArgumentException("DataSource must be set (either via dataSource() or jdbcTemplate())");
		}

		private JdbcBatchExecutionRepositoryDialect resolveDialect(DataSource dataSource) {
			if (this.dialect == null) {
				return JdbcBatchExecutionRepositoryDialect.from(dataSource);
			}
			else {
				warnIfDialectMismatch(dataSource, this.dialect);
				return this.dialect;
			}
		}

		private void warnIfDialectMismatch(DataSource dataSource, JdbcBatchExecutionRepositoryDialect explicitDialect) {
			JdbcBatchExecutionRepositoryDialect detected = JdbcBatchExecutionRepositoryDialect.from(dataSource);
			if (!detected.getClass().equals(explicitDialect.getClass())) {
				logger.warn("Explicitly set dialect {} will be used instead of detected dialect {} from datasource",
						explicitDialect.getClass().getSimpleName(), detected.getClass().getSimpleName());
			}
		}

	}

}
