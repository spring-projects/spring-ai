package org.springframework.ai.tool.definition;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DefaultToolDefinition}.
 *
 * @author Thomas Vitale
 */
class DefaultToolDefinitionTests {

	@Test
	void shouldCreateDefaultToolDefinition() {
		var toolDefinition = new DefaultToolDefinition("name", "description", "{}");
		assertThat(toolDefinition.name()).isEqualTo("name");
		assertThat(toolDefinition.description()).isEqualTo("description");
		assertThat(toolDefinition.inputSchema()).isEqualTo("{}");
	}

	@Test
	void shouldThrowExceptionWhenNameIsNull() {
		assertThatThrownBy(() -> new DefaultToolDefinition(null, "description", "{}"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("name cannot be null or empty");
	}

	@Test
	void shouldThrowExceptionWhenNameIsEmpty() {
		assertThatThrownBy(() -> new DefaultToolDefinition("", "description", "{}"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("name cannot be null or empty");
	}

	@Test
	void shouldThrowExceptionWhenDescriptionIsNull() {
		assertThatThrownBy(() -> new DefaultToolDefinition("name", null, "{}"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("description cannot be null or empty");
	}

	@Test
	void shouldThrowExceptionWhenDescriptionIsEmpty() {
		assertThatThrownBy(() -> new DefaultToolDefinition("name", "", "{}"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("description cannot be null or empty");
	}

	@Test
	void shouldThrowExceptionWhenInputSchemaIsNull() {
		assertThatThrownBy(() -> new DefaultToolDefinition("name", "description", null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("inputSchema cannot be null or empty");
	}

	@Test
	void shouldThrowExceptionWhenInputSchemaIsEmpty() {
		assertThatThrownBy(() -> new DefaultToolDefinition("name", "description", ""))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("inputSchema cannot be null or empty");
	}

}
