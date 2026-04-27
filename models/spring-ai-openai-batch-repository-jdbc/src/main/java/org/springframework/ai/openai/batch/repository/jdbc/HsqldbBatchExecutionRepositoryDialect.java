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

/**
 * HSQLDB-specific SQL dialect for batch execution repository.
 *
 * @author Yasin Akbas
 * @since 2.0.0
 */
public class HsqldbBatchExecutionRepositoryDialect implements JdbcBatchExecutionRepositoryDialect {

	@Override
	public String getUpsertSql() {
		return """
				MERGE INTO SPRING_AI_BATCH_EXECUTION AS target
				USING (VALUES (?, ?, ?, ?, ?, ?, ?)) AS source (batch_id, endpoint, status, request_count, input_file_id, created_at, updated_at)
				ON target.batch_id = source.batch_id
				WHEN MATCHED THEN UPDATE SET
				target.endpoint = source.endpoint,
				target.status = source.status,
				target.request_count = source.request_count,
				target.input_file_id = source.input_file_id,
				target.created_at = source.created_at,
				target.updated_at = source.updated_at
				WHEN NOT MATCHED THEN INSERT (batch_id, endpoint, status, request_count, input_file_id, created_at, updated_at)
				VALUES (source.batch_id, source.endpoint, source.status, source.request_count, source.input_file_id, source.created_at, source.updated_at)""";
	}

}
