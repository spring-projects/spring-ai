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
 * The dialect of Microsoft SQL Server (2016 and later).
 * <p>
 * The upsert and the insert-if-absent are a {@code MERGE} under {@code HOLDLOCK}, so that
 * two concurrent saves of a new id cannot both take the insert branch, and the source
 * values are cast so that a {@code null} expiry keeps its column type. The
 * insert-if-absent is not a plain {@code INSERT}, because its duplicate-key error would
 * roll an enclosing transaction back on a connection with {@code XACT_ABORT ON}. The id
 * is cast wider than its column, so that an over-long id is rejected by the insert rather
 * than silently truncated to the id of another session. SQL Server has no {@code LIMIT};
 * rows are limited with {@code OFFSET ... FETCH}.
 *
 * @author Durga Dasepalli
 * @author Christian Tzolov
 * @since 2.1.0
 */
public class SqlServerJdbcSessionRepositoryDialect implements JdbcSessionRepositoryDialect {

	@Override
	public String getUpsertSessionSql() {
		return """
				MERGE INTO AI_SESSION WITH (HOLDLOCK) AS t
				USING (SELECT CAST(? AS NVARCHAR(4000)) AS id, CAST(? AS DATETIME2(6)) AS created_at,
						CAST(? AS DATETIME2(6)) AS expires_at, CAST(? AS NVARCHAR(MAX)) AS metadata) AS s
				ON t.id = s.id
				WHEN MATCHED THEN UPDATE
					SET expires_at = s.expires_at, metadata = s.metadata
				WHEN NOT MATCHED THEN INSERT (id, created_at, expires_at, metadata)
					VALUES (s.id, s.created_at, s.expires_at, s.metadata);
				""";
	}

	@Override
	public String getInsertSessionIfAbsentSql() {
		// A duplicate-key error rolls an enclosing transaction back on the server when
		// XACT_ABORT is ON; insert only when the id is absent instead and report an
		// existing id through the update count.
		return """
				MERGE INTO AI_SESSION WITH (HOLDLOCK) AS t
				USING (SELECT CAST(? AS NVARCHAR(4000)) AS id, CAST(? AS DATETIME2(6)) AS created_at,
						CAST(? AS DATETIME2(6)) AS expires_at, CAST(? AS NVARCHAR(MAX)) AS metadata) AS s
				ON t.id = s.id
				WHEN NOT MATCHED THEN INSERT (id, created_at, expires_at, metadata)
					VALUES (s.id, s.created_at, s.expires_at, s.metadata);
				""";
	}

	@Override
	public String getLimitClause() {
		return "OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY";
	}

}
