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
 * two concurrent saves of a new id cannot both take the insert branch. The lock covers
 * the key range around the id and is held until the transaction ends: in a caller's
 * transaction that stays open, an insert of another new id into that range and a write to
 * the next session in key order wait for it. The insert-if-absent is not a plain
 * {@code INSERT}, because its duplicate-key error would roll an enclosing transaction
 * back on a connection with {@code XACT_ABORT ON}.
 * <p>
 * The timestamps and the metadata are cast so that a {@code null} expiry keeps its column
 * type. The id is not: a cast silently cuts a value longer than its type, to the id of
 * another session at worst, whereas the insert of an over-long id is rejected. SQL Server
 * has no {@code LIMIT}; rows are limited with {@code OFFSET ... FETCH}.
 *
 * @author Durga Dasepalli
 * @author Christian Tzolov
 * @since 2.1.0
 */
public class SqlServerJdbcSessionRepositoryDialect implements JdbcSessionRepositoryDialect {

	private static final String MERGE_SESSION = """
			MERGE INTO AI_SESSION WITH (HOLDLOCK) AS t
			USING (SELECT ? AS id, CAST(? AS DATETIME2(6)) AS created_at,
					CAST(? AS DATETIME2(6)) AS expires_at, CAST(? AS NVARCHAR(MAX)) AS metadata) AS s
			ON t.id = s.id
			""";

	private static final String UPDATE_WHEN_MATCHED = """
			WHEN MATCHED THEN UPDATE
				SET expires_at = s.expires_at, metadata = s.metadata
			""";

	private static final String INSERT_WHEN_NOT_MATCHED = """
			WHEN NOT MATCHED THEN INSERT (id, created_at, expires_at, metadata)
				VALUES (s.id, s.created_at, s.expires_at, s.metadata);
			""";

	@Override
	public String getUpsertSessionSql() {
		return MERGE_SESSION + UPDATE_WHEN_MATCHED + INSERT_WHEN_NOT_MATCHED;
	}

	@Override
	public String getInsertSessionIfAbsentSql() {
		// A duplicate-key error rolls an enclosing transaction back on the server when
		// XACT_ABORT is ON; insert only when the id is absent instead and report an
		// existing id through the update count.
		return MERGE_SESSION + INSERT_WHEN_NOT_MATCHED;
	}

	@Override
	public String getLimitClause() {
		return "OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY";
	}

	@Override
	public String toKeywordParameter(String keyword) {
		// LIKE takes [ as the start of a character set here; escaped, it is text. The
		// default escaping leaves the brackets alone, so this adds no second escape
		return JdbcSessionRepositoryDialect.super.toKeywordParameter(keyword).replace("[", "![");
	}

}
