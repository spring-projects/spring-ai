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
 * PostgreSQL dialect for batch execution repository.
 *
 * @author Yasin Akbas
 * @since 2.0.0
 */
public class PostgresBatchExecutionRepositoryDialect implements JdbcBatchExecutionRepositoryDialect {

	@Override
	public String getUpsertSql() {
		return """
				INSERT INTO SPRING_AI_BATCH_EXECUTION (batch_id, endpoint, status, request_count, input_file_id, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (batch_id) DO UPDATE SET
				endpoint = EXCLUDED.endpoint,
				status = EXCLUDED.status,
				request_count = EXCLUDED.request_count,
				input_file_id = EXCLUDED.input_file_id,
				created_at = EXCLUDED.created_at,
				updated_at = EXCLUDED.updated_at""";
	}

}
