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

package org.springframework.ai.anthropic;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AnthropicChatModel.AnthropicAssistantMessage}.
 *
 * @author guan xu
 */
class AnthropicAssistantMessageTests {

	@Test
	void copyPreservesThinkingContents() {
		AnthropicChatModel.AnthropicAssistantMessage original = AnthropicChatModel.AnthropicAssistantMessage.builder()
			.content("hello")
			.thinkingContents(List.of(AnthropicChatModel.AnthropicThinkingContent.thinking("think", "sig")))
			.build();

		AnthropicChatModel.AnthropicAssistantMessage copy = (AnthropicChatModel.AnthropicAssistantMessage) original
			.copy();

		assertThat(copy).isNotSameAs(original);
		assertThat(copy.getThinkingContents()).hasSize(1);
		assertThat(copy.getThinkingContents().get(0).thinking()).isEqualTo("think");
		assertThat(copy.getThinkingContents().get(0).signature()).isEqualTo("sig");
	}

	@Test
	void promptCopyPreservesAnthropicAssistantMessage() {
		AnthropicChatModel.AnthropicAssistantMessage original = AnthropicChatModel.AnthropicAssistantMessage.builder()
			.content("hello")
			.thinkingContents(List.of(AnthropicChatModel.AnthropicThinkingContent.thinking("think", "sig"),
					AnthropicChatModel.AnthropicThinkingContent.redacted("data")))
			.build();

		Prompt copy = new Prompt(List.of(original)).copy();

		Message copied = copy.getInstructions().get(0);
		assertThat(copied).isInstanceOf(AnthropicChatModel.AnthropicAssistantMessage.class);
		AnthropicChatModel.AnthropicAssistantMessage anthropicCopy = (AnthropicChatModel.AnthropicAssistantMessage) copied;
		assertThat(anthropicCopy.getThinkingContents()).hasSize(2);
	}

}
