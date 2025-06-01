package org.springframework.ai.model.tool;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolExecutionEligibilityCheckerTest {

	@Test
	void isToolExecutionRequired() {
		ToolExecutionEligibilityChecker checker = new TestToolExecutionEligibilityChecker();

		ToolCallingChatOptions promptOptions = ToolCallingChatOptions.builder().build();
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("test"))));
		promptOptions.setInternalToolExecutionMaxIterations(2);

		assertThat(checker.isToolExecutionRequired(promptOptions, chatResponse, 1)).isTrue();
		assertThat(checker.isToolExecutionRequired(promptOptions, chatResponse, 2)).isTrue();

		// attempts value is oversize
		assertThat(checker.isToolExecutionRequired(promptOptions, chatResponse, 3)).isFalse();
	}

	@Test
	void isInternalToolExecutionEnabled() {

		ToolExecutionEligibilityChecker checker = new TestToolExecutionEligibilityChecker();

		ToolCallingChatOptions promptOptions = ToolCallingChatOptions.builder().build();
		promptOptions.setInternalToolExecutionMaxIterations(2);

		assertThat(checker.isInternalToolExecutionEnabled(promptOptions, 1)).isTrue();
		assertThat(checker.isInternalToolExecutionEnabled(promptOptions, 2)).isTrue();

		// attempts value is oversize
		assertThat(checker.isInternalToolExecutionEnabled(promptOptions, 3)).isFalse();

	}

	class TestToolExecutionEligibilityChecker implements ToolExecutionEligibilityChecker {

		@Override
		public Boolean apply(ChatResponse chatResponse) {
			return true;
		}

	}

}
