package org.springframework.ai.tool.metadata;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.execution.ToolExecutionMode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ToolMetadata}.
 *
 * @author Thomas Vitale
 */
class ToolMetadataTests {

	@Test
	void shouldCreateDefaultToolMetadataBuilder() {
		var toolMetadata = ToolMetadata.builder().build();
		assertThat(toolMetadata.executionMode()).isEqualTo(ToolExecutionMode.BLOCKING);
		assertThat(toolMetadata.returnDirect()).isFalse();
	}

	@Test
	void shouldCreateToolMetadataFromMethod() {
		var toolMetadata = ToolMetadata.from(Tools.class.getDeclaredMethods()[0]);
		assertThat(toolMetadata.executionMode()).isEqualTo(ToolExecutionMode.BLOCKING);
		assertThat(toolMetadata.returnDirect()).isTrue();
	}

	static class Tools {

		@Tool(value = "Test description", returnDirect = true)
		public List<String> mySuperTool(String input) {
			return List.of(input);
		}

	}

}
