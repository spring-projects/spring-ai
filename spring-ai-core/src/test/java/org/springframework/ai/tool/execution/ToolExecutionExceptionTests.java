package org.springframework.ai.tool.execution;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link ToolExecutionException}.
 *
 * @author Thomas Vitale
 */
class ToolExecutionExceptionTests {

	@Test
	void constructorShouldSetCauseAndMessage() {
		String errorMessage = "Test error message";
		RuntimeException cause = new RuntimeException(errorMessage);

		ToolExecutionException exception = new ToolExecutionException(mock(ToolDefinition.class), cause);

		assertThat(exception.getCause()).isEqualTo(cause);
		assertThat(exception.getMessage()).isEqualTo(errorMessage);
	}

	@Test
	void getToolDefinitionShouldReturnToolDefinition() {
		RuntimeException cause = new RuntimeException("Test error");
		ToolDefinition toolDefinition = mock(ToolDefinition.class);
		ToolExecutionException exception = new ToolExecutionException(toolDefinition, cause);

		assertThat(exception.getToolDefinition()).isEqualTo(toolDefinition);
	}

}
