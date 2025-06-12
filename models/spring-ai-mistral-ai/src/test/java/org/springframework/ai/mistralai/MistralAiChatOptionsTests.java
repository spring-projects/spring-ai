package org.springframework.ai.mistralai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author lambochen
 */
class MistralAiChatOptionsTests {

	@Test
	void testOptionsDefault() {
		var options = new MistralAiChatOptions();

		assertThat(options.getInternalToolExecutionMaxIterations())
			.isEqualTo(ToolCallingChatOptions.DEFAULT_TOOL_EXECUTION_MAX_ITERATIONS);
	}

	@Test
	void testOptionsCustom() {
		var options = new MistralAiChatOptions();

		options.setInternalToolExecutionMaxIterations(3);

		assertThat(options.getInternalToolExecutionMaxIterations()).isEqualTo(3);
	}

	@Test
	void testBuilder() {
		var options = MistralAiChatOptions.builder().internalToolExecutionMaxIterations(3).build();

		assertThat(options.getInternalToolExecutionMaxIterations()).isEqualTo(3);
	}

}
