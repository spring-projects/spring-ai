/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.chat.memory;

import java.sql.SQLException;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.util.Assert;

/**
 * Configuration for the JDBC {@link ChatMemory}. When
 * {@link JdbcChatMemoryConfig#initializeSchema} is set to {@code true} (default is
 * {@code false}) and {@link #initializeSchema()} is called, then the schema based on the
 * database will be created.
 *
 * @author Jonathan Leijendekker
 * @since 1.0.0
 */
public final class JdbcChatMemoryConfig {

	private static final Logger logger = LoggerFactory.getLogger(JdbcChatMemoryConfig.class);

	public static final boolean DEFAULT_SCHEMA_INITIALIZATION = false;

	private final boolean initializeSchema;

	private final JdbcTemplate jdbcTemplate;

	private JdbcChatMemoryConfig(Builder builder) {
		this.initializeSchema = builder.initializeSchema;
		this.jdbcTemplate = builder.jdbcTemplate;
	}

	public static Builder builder() {
		return new Builder();
	}

	JdbcTemplate getJdbcTemplate() {
		return this.jdbcTemplate;
	}

	void initializeSchema() {
		if (!this.initializeSchema) {
			return;
		}

		logger.info("Initializing JdbcChatMemory schema");

		String productName;

		try (var connection = Objects.requireNonNull(this.jdbcTemplate.getDataSource()).getConnection()) {
			var metadata = connection.getMetaData();
			productName = metadata.getDatabaseProductName();
		}
		catch (SQLException e) {
			throw new CannotGetJdbcConnectionException("Failed to obtain JDBC metadata", e);
		}

		var fileName = String.format("schema.%s.sql", productName.toLowerCase());
		var resource = new ClassPathResource(fileName, JdbcChatMemoryConfig.class);
		var databasePopulator = new ResourceDatabasePopulator(resource);
		databasePopulator.execute(this.jdbcTemplate.getDataSource());
	}

	public static final class Builder {

		private boolean initializeSchema = DEFAULT_SCHEMA_INITIALIZATION;

		private JdbcTemplate jdbcTemplate;

		private Builder() {
		}

		public Builder setInitializeSchema(boolean initializeSchema) {
			this.initializeSchema = initializeSchema;
			return this;
		}

		public Builder jdbcTemplate(JdbcTemplate jdbcTemplate) {
			Assert.notNull(jdbcTemplate, "jdbc template must not be null");

			this.jdbcTemplate = jdbcTemplate;
			return this;
		}

		public JdbcChatMemoryConfig build() {
			Assert.notNull(this.jdbcTemplate, "jdbc template must not be null");

			return new JdbcChatMemoryConfig(this);
		}

	}

}
