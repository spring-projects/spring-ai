package org.springframework.ai.vertexai.gemini;

import org.junit.jupiter.api.Test;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import static org.junit.jupiter.api.Assertions.*;

class VertexAiGeminiChatOptionsTest {

	@Test
	void optionsDefault() {
		var options = new VertexAiGeminiChatOptions();

		assertEquals(ToolCallingChatOptions.DEFAULT_TOOL_EXECUTION_MAX_ITERATIONS,
				options.getInternalToolExecutionMaxIterations());
	}

	@Test
	void builderDefault() {
		var options = VertexAiGeminiChatOptions.builder().build();

		assertEquals(ToolCallingChatOptions.DEFAULT_TOOL_EXECUTION_MAX_ITERATIONS,
				options.getInternalToolExecutionMaxIterations());
	}

	@Test
	void testBuilder() {
		var options = VertexAiGeminiChatOptions.builder().internalToolExecutionMaxIterations(3).build();

		assertEquals(3, options.getInternalToolExecutionMaxIterations());
	}

	@Test
	void fromOptions() {
		var original = new VertexAiGeminiChatOptions();
		original.setInternalToolExecutionMaxIterations(3);

		var copied = VertexAiGeminiChatOptions.fromOptions(original);

		assertEquals(original.getInternalToolExecutionMaxIterations(), copied.getInternalToolExecutionMaxIterations());
	}

}
