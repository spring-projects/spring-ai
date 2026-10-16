/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.chat.client.advisor.api;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.memory.ChatMemory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MemoryAdvisor}.
 *
 * @author Christian Tzolov
 */
class MemoryAdvisorTests {

	@Test
	void conversationIdKeyIsTheOneChatMemoryUses() {
		// Both keys must stay the same text, so a request carrying either one reaches
		// every memory advisor, the ChatMemory-based ones included
		assertThat(MemoryAdvisor.CONVERSATION_ID).isEqualTo(ChatMemory.CONVERSATION_ID)
			.isEqualTo("chat_memory_conversation_id");
	}

}
