package org.springframework.ai.zhipuai.chat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * @author lambochen
 */
class ZhiPuAiChatOptionsTests {

	@Test
	void testDefaultValue() {
		var options = new ZhiPuAiChatOptions();

		assertThat(options.getToolExecutionMaxIterations())
			.isEqualTo(ToolCallingChatOptions.DEFAULT_TOOL_EXECUTION_MAX_ITERATIONS);
	}

	@Test
	void testSetter() {
		var options = new ZhiPuAiChatOptions();
		options.setToolExecutionMaxIterations(3);
		assertThat(options.getToolExecutionMaxIterations()).isEqualTo(3);
	}

	@Test
	void testBuilder() {
		var options = ZhiPuAiChatOptions.builder().toolExecutionMaxIterations(3).build();

		assertThat(options.getToolExecutionMaxIterations()).isEqualTo(3);
	}

}
