/*
 * Copyright 2023-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.model.tool;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import static org.assertj.core.api.Assertions.assertThat;

class ToolExecutionEligibilityCheckerTest {

	@Test
	void isToolExecutionRequired() {
		ToolExecutionEligibilityChecker checker = new TestToolExecutionEligibilityChecker();

		ToolCallingChatOptions promptOptions = ToolCallingChatOptions.builder().build();
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("test"))));
		promptOptions.setToolExecutionMaxIterations(2);

		assertThat(checker.isToolExecutionRequired(promptOptions, chatResponse, 1)).isTrue();
		assertThat(checker.isToolExecutionRequired(promptOptions, chatResponse, 2)).isTrue();

		// attempts value is oversize
		assertThat(checker.isToolExecutionRequired(promptOptions, chatResponse, 3)).isFalse();
	}

	@Test
	void isInternalToolExecutionEnabled() {

		ToolExecutionEligibilityChecker checker = new TestToolExecutionEligibilityChecker();

		ToolCallingChatOptions promptOptions = ToolCallingChatOptions.builder().build();
		promptOptions.setToolExecutionMaxIterations(2);

		assertThat(checker.isInternalToolExecutionEnabled(promptOptions, 1)).isTrue();
		assertThat(checker.isInternalToolExecutionEnabled(promptOptions, 2)).isTrue();

		// attempts value is oversize
		assertThat(checker.isInternalToolExecutionEnabled(promptOptions, 3)).isFalse();

	}

	static class TestToolExecutionEligibilityChecker implements ToolExecutionEligibilityChecker {

		@Override
		public Boolean apply(ChatResponse chatResponse) {
			return true;
		}

	}

}
