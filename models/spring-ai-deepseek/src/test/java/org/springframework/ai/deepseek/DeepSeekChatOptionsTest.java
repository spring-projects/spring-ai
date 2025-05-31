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
		original.setInternalToolExecutionMaxAttempts(3);

		var copy = DeepSeekChatOptions.fromOptions(original);
		assertNotSame(original, copy);
		assertSame(original.getInternalToolExecutionMaxAttempts(), copy.getInternalToolExecutionMaxAttempts());
	}

	@Test
	void optionsDefault() {
		var options = new DeepSeekChatOptions();

		assertEquals(ToolCallingChatOptions.DEFAULT_TOOL_EXECUTION_MAX_ATTEMPTS, options.getInternalToolExecutionMaxAttempts());
	}

	@Test
	void optionsBuilder() {
		var options = DeepSeekChatOptions.builder()
				.internalToolExecutionMaxAttempts(3)
				.build();

		assertEquals(3, options.getInternalToolExecutionMaxAttempts());
	}
}
