package org.springframework.ai.tool;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ToolCallback}.
 *
 * @author Thomas Vitale
 */
class ToolCallbackTests {

	@Test
	void shouldOnlyImplementRequiredMethods() {
		var testToolCallback = new TestToolCallback("test");
		assertThat(testToolCallback.getToolDefinition()).isNotNull();
		assertThat(testToolCallback.getToolMetadata()).isNotNull();
	}

	static class TestToolCallback implements ToolCallback {

		private final ToolDefinition toolDefinition;

		public TestToolCallback(String name) {
			this.toolDefinition = ToolDefinition.builder().name(name).description(name).inputTypeSchema("{}").build();
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return toolDefinition;
		}

		@Override
		public String call(String toolInput) {
			return "";
		}

	}

}
