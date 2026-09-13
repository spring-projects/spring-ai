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

package org.springframework.ai.vectorstore.pgvector.autoconfigure;

import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.Assert;

/**
 * {@link AbstractHealthIndicator Health indicator} for PgVector vector store.
 * <p>
 * Verifies that the {@code vector} PostgreSQL extension is installed and reachable.
 * Reports the extension version as part of the health details when {@code UP}.
 *
 * @author jiajingda
 * @since 1.1.0
 */
public class PgVectorStoreHealthIndicator extends AbstractHealthIndicator {

	private static final String VECTOR_EXTENSION_QUERY = "SELECT extversion FROM pg_extension WHERE extname = 'vector'";

	private final JdbcTemplate jdbcTemplate;

	public PgVectorStoreHealthIndicator(JdbcTemplate jdbcTemplate) {
		super("PgVector health check failed");
		Assert.notNull(jdbcTemplate, "JdbcTemplate must not be null");
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	protected void doHealthCheck(Health.Builder builder) throws Exception {
		String version = this.jdbcTemplate.queryForObject(VECTOR_EXTENSION_QUERY, String.class);
		if (version == null || version.isBlank()) {
			builder.down().withDetail("reason", "PgVector extension not installed");
			return;
		}
		builder.up().withDetail("vectorExtensionVersion", version).withDetail("database", "postgresql");
	}

}
