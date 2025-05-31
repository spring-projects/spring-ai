package org.springframework.ai.vertexai.gemini;

import org.junit.jupiter.api.Test;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import static org.junit.jupiter.api.Assertions.*;

class VertexAiGeminiChatOptionsTest {

	@Test
	void optionsDefault() {
		var options = new VertexAiGeminiChatOptions();

		assertEquals(ToolCallingChatOptions.DEFAULT_TOOL_EXECUTION_MAX_ATTEMPTS, options.getInternalToolExecutionMaxAttempts());
	}

	@Test
	void builderDefault() {
		var options = VertexAiGeminiChatOptions.builder().build();

		assertEquals(ToolCallingChatOptions.DEFAULT_TOOL_EXECUTION_MAX_ATTEMPTS, options.getInternalToolExecutionMaxAttempts());
	}

	@Test
	void testBuilder() {
		var options = VertexAiGeminiChatOptions.builder()
				.internalToolExecutionMaxAttempts(3)
				.build();

		assertEquals(3, options.getInternalToolExecutionMaxAttempts());
	}

	@Test
	void fromOptions() {
		var original = new VertexAiGeminiChatOptions();
		original.setInternalToolExecutionMaxAttempts(3);

		var copied = VertexAiGeminiChatOptions.fromOptions(original);

		assertEquals(original.getInternalToolExecutionMaxAttempts(), copied.getInternalToolExecutionMaxAttempts());
	}
}
