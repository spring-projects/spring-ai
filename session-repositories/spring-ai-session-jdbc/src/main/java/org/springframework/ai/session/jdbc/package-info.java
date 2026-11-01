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

/**
 * JDBC-backed implementation of {@link org.springframework.ai.session.SessionRepository}.
 *
 * <p>
 * Entry point: {@link org.springframework.ai.session.jdbc.JdbcSessionRepository}.
 *
 * <p>
 * DDL scripts for each supported database are available on the classpath at
 * {@code org/springframework/ai/session/jdbc/schema-{db}.sql} (e.g.
 * {@code schema-postgresql.sql}, {@code schema-h2.sql}, {@code schema-mysql.sql}).
 */
@org.jspecify.annotations.NullMarked
package org.springframework.ai.session.jdbc;
