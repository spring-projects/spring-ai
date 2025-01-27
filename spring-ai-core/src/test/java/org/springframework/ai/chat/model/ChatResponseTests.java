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

package org.springframework.ai.chat.model;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ChatResponse}.
 *
 * @author Thomas Vitale
 */
class ChatResponseTests {

	@Test
	void whenToolCallsArePresentThenReturnTrue() {
		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(new AssistantMessage("", Map.of(),
					List.of(new AssistantMessage.ToolCall("toolA", "function", "toolA", "{}"))))))
			.build();
		assertThat(chatResponse.hasToolCalls()).isTrue();
	}

	@Test
	void whenNoToolCallsArePresentThenReturnFalse() {
		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(new AssistantMessage("Result"))))
			.build();
		assertThat(chatResponse.hasToolCalls()).isFalse();
	}

}
