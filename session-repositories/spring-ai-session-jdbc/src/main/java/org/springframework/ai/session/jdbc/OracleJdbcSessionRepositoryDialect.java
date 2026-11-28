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
 * The dialect of Oracle Database. The upsert is a {@code MERGE} in a PL/SQL block, and an
 * update leaves {@code created_at} alone as the other dialects do. Oracle has no
 * {@code LIMIT}; the limit clause is the standard {@code FETCH FIRST}, available since
 * Oracle Database 12c.
 *
 * @author Eunji Park
 * @since 2.1.1
 */
public class OracleJdbcSessionRepositoryDialect implements JdbcSessionRepositoryDialect {

	/**
	 * A PL/SQL block rather than a plain {@code MERGE}, for two reasons. A bind in the
	 * {@code USING} query is a {@code VARCHAR2}, which cannot carry metadata longer than
	 * 32767 bytes: assigned to a {@code CLOB} variable first, it can. And {@code MERGE}
	 * does not lock a key that does not exist yet, so two concurrent saves of a new
	 * session can both take the insert branch: the one that loses updates instead.
	 */
	@Override
	public String getUpsertSessionSql() {
		return """
				DECLARE
					v_id         AI_SESSION.id%TYPE := ?;
					v_created_at AI_SESSION.created_at%TYPE := ?;
					v_expires_at AI_SESSION.expires_at%TYPE := ?;
					v_metadata   CLOB := ?;
				BEGIN
					MERGE INTO AI_SESSION t
					USING (SELECT v_id AS id FROM DUAL) s
					ON (t.id = s.id)
					WHEN MATCHED THEN UPDATE
						SET t.expires_at = v_expires_at, t.metadata = v_metadata
					WHEN NOT MATCHED THEN INSERT (id, created_at, expires_at, metadata)
						VALUES (v_id, v_created_at, v_expires_at, v_metadata);
				EXCEPTION
					WHEN DUP_VAL_ON_INDEX THEN
						UPDATE AI_SESSION SET expires_at = v_expires_at, metadata = v_metadata
						WHERE id = v_id;
				END;""";
	}

	@Override
	public String getLimitClause() {
		return "FETCH FIRST ? ROWS ONLY";
	}

	/**
	 * The default predicate without its {@code COALESCE}: Oracle reads {@code ''} as a
	 * {@code NULL} of type {@code CHAR}, which a {@code CLOB} column cannot be coalesced
	 * with. A {@code NULL} text matches no keyword either way, as a keyword is never
	 * blank.
	 */
	@Override
	public String getKeywordPredicate() {
		return "LOWER(e.message_content) LIKE LOWER(?) ESCAPE '!'";
	}

}
