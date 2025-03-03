/*
 * Copyright 2024-2025 the original author or authors.
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

package org.springframework.ai.chat.memory.jdbc;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.Assert;

/**
 * Configuration for {@link JdbcChatMemory}.
 *
 * @author Jonathan Leijendekker
 * @since 1.0.0
 */
public final class JdbcChatMemoryConfig {

	private final JdbcTemplate jdbcTemplate;

	private JdbcChatMemoryConfig(Builder builder) {
		this.jdbcTemplate = builder.jdbcTemplate;
	}

	public static Builder builder() {
		return new Builder();
	}

	JdbcTemplate getJdbcTemplate() {
		return this.jdbcTemplate;
	}

	public static final class Builder {

		private JdbcTemplate jdbcTemplate;

		private Builder() {
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
