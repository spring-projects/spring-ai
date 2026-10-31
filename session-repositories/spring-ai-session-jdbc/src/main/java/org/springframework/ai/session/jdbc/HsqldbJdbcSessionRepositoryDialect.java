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

/**
 * The dialect of HSQLDB. The upsert is a standard {@code MERGE ... USING} that updates
 * only {@code expires_at} and {@code metadata}, so {@code created_at} keeps its value as
 * with the other dialects.
 *
 * @author Rojan Francis
 * @since 2.1.0
 */
public class HsqldbJdbcSessionRepositoryDialect implements JdbcSessionRepositoryDialect {

	@Override
	public String getUpsertSessionSql() {
		return """
				MERGE INTO AI_SESSION t
				USING (VALUES (CAST(? AS VARCHAR(255)), CAST(? AS TIMESTAMP), CAST(? AS TIMESTAMP),
						CAST(? AS LONGVARCHAR)))
					AS s (id, created_at, expires_at, metadata)
				ON t.id = s.id
				WHEN MATCHED THEN UPDATE
					SET expires_at = s.expires_at, metadata = s.metadata
				WHEN NOT MATCHED THEN INSERT (id, created_at, expires_at, metadata)
					VALUES (s.id, s.created_at, s.expires_at, s.metadata)
				""";
	}

}
