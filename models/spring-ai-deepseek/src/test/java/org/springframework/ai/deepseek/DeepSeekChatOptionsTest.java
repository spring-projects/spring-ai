package org.springframework.ai.deepseek;

import org.junit.jupiter.api.Test;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author lambochen
 */
class DeepSeekChatOptionsTest {

	@Test
	void fromOptions() {
		var original = new DeepSeekChatOptions();
		original.setToolExecutionMaxIterations(3);

		var copy = DeepSeekChatOptions.fromOptions(original);
		assertNotSame(original, copy);
		assertSame(original.getToolExecutionMaxIterations(), copy.getToolExecutionMaxIterations());
	}

	@Test
	void optionsDefault() {
		var options = new DeepSeekChatOptions();

		assertEquals(ToolCallingChatOptions.DEFAULT_TOOL_EXECUTION_MAX_ITERATIONS,
				options.getToolExecutionMaxIterations());
	}

	@Test
	void optionsBuilder() {
		var options = DeepSeekChatOptions.builder().toolExecutionMaxIterations(3).build();

		assertEquals(3, options.getToolExecutionMaxIterations());
	}

}
