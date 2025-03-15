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

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DefaultToolExecutionResult}.
 *
 * @author Thomas Vitale
 */
class DefaultToolExecutionResultTests {

	@Test
	void whenConversationHistoryIsNullThenThrow() {
		assertThatThrownBy(() -> DefaultToolExecutionResult.builder().conversationHistory(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("conversationHistory cannot be null");
	}

	@Test
	void whenConversationHistoryHasNullElementsThenThrow() {
		var history = new ArrayList<Message>();
		history.add(null);
		assertThatThrownBy(() -> DefaultToolExecutionResult.builder().conversationHistory(history).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("conversationHistory cannot contain null elements");
	}

	@Test
	void builder() {
		var conversationHistory = new ArrayList<Message>();
		var result = DefaultToolExecutionResult.builder()
			.conversationHistory(conversationHistory)
			.returnDirect(true)
			.build();
		assertThat(result.conversationHistory()).isEqualTo(conversationHistory);
		assertThat(result.returnDirect()).isTrue();
	}

}
