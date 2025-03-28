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
