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

package org.springframework.ai.bedrock.converse;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BedrockAssistantMessage}.
 *
 * @author guan xu
 */
class BedrockAssistantMessageTests {

	@Test
	void copyPreservesReasoningContents() {
		List<BedrockReasoningContent> reasoningContents = Collections.singletonList(null);
		BedrockAssistantMessage original = BedrockAssistantMessage.builder()
			.content("hello")
			.reasoningContents(reasoningContents)
			.build();

		BedrockAssistantMessage copy = (BedrockAssistantMessage) original.copy();

		assertThat(copy).isNotSameAs(original);
		assertThat(copy.getReasoningContents()).hasSize(1);
	}

	@Test
	void promptCopyPreservesBedrockAssistantMessage() {
		List<BedrockReasoningContent> reasoningContents = Collections.singletonList(null);
		BedrockAssistantMessage original = BedrockAssistantMessage.builder()
			.content("hello")
			.reasoningContents(reasoningContents)
			.build();

		Prompt copy = new Prompt(List.of(original)).copy();

		Message copied = copy.getInstructions().get(0);
		assertThat(copied).isInstanceOf(BedrockAssistantMessage.class);
		assertThat(((BedrockAssistantMessage) copied).getReasoningContents()).hasSize(1);
	}

}
