/*
 * Copyright 2025-2025 the original author or authors.
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

import org.junit.jupiter.api.Test;

import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * @author Jonathan Leijendekker
 */
class JdbcChatMemoryConfigTest {

	@Test
	void setValues() {
		var jdbcTemplate = mock(JdbcTemplate.class);
		var config = JdbcChatMemoryConfig.builder().jdbcTemplate(jdbcTemplate).build();

		assertThat(config.getJdbcTemplate()).isEqualTo(jdbcTemplate);
	}

	@Test
	void setJdbcTemplateToNull_shouldThrow() {
		assertThatThrownBy(() -> JdbcChatMemoryConfig.builder().jdbcTemplate(null));
	}

	@Test
	void buildWithNullJdbcTemplate_shouldThrow() {
		assertThatThrownBy(() -> JdbcChatMemoryConfig.builder().build());
	}

}
