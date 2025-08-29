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
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

/**
 * Unit tests for {@link ToolExecutionResult}.
 *
 * @author Thomas Vitale
 */
class ToolExecutionResultTests {

	@Test
	void whenSingleToolCallThenSingleGeneration() {
		var toolExecutionResult = ToolExecutionResult.builder()
			.conversationHistory(List.of(new AssistantMessage("Hello, how can I help you?"),
					new UserMessage("I would like to know the weather in London"),
					new AssistantMessage("Call the weather tool"),
					new ToolResponseMessage(List.of(new ToolResponseMessage.ToolResponse("42", "weather",
							"The weather in London is 20 degrees Celsius")))))
			.build();

		var generations = ToolExecutionResult.buildGenerations(toolExecutionResult);

		assertThat(generations).hasSize(1);
		assertThat(generations.get(0).getOutput().getText()).isEqualTo("The weather in London is 20 degrees Celsius");
		assertThat((String) generations.get(0).getMetadata().get(ToolExecutionResult.METADATA_TOOL_NAME))
			.isEqualTo("weather");
		assertThat(generations.get(0).getMetadata().getFinishReason()).isEqualTo(ToolExecutionResult.FINISH_REASON);
	}

	@Test
	void whenMultipleToolCallsThenMultipleGenerations() {
		var toolExecutionResult = ToolExecutionResult.builder()
			.conversationHistory(List.of(new AssistantMessage("Hello, how can I help you?"),
					new UserMessage("I would like to know the weather in London"),
					new AssistantMessage("Call the weather tool and the news tool"),
					new ToolResponseMessage(List.of(
							new ToolResponseMessage.ToolResponse("42", "weather",
									"The weather in London is 20 degrees Celsius"),
							new ToolResponseMessage.ToolResponse("21", "news",
									"There is heavy traffic in the centre of London")))))
			.build();

		var generations = ToolExecutionResult.buildGenerations(toolExecutionResult);

		assertThat(generations).hasSize(2);
		assertThat(generations.get(0).getOutput().getText()).isEqualTo("The weather in London is 20 degrees Celsius");
		assertThat((String) generations.get(0).getMetadata().get(ToolExecutionResult.METADATA_TOOL_NAME))
			.isEqualTo("weather");
		assertThat(generations.get(0).getMetadata().getFinishReason()).isEqualTo(ToolExecutionResult.FINISH_REASON);

		assertThat(generations.get(1).getOutput().getText())
			.isEqualTo("There is heavy traffic in the centre of London");
		assertThat((String) generations.get(1).getMetadata().get(ToolExecutionResult.METADATA_TOOL_NAME))
			.isEqualTo("news");
		assertThat(generations.get(1).getMetadata().getFinishReason()).isEqualTo(ToolExecutionResult.FINISH_REASON);
	}

	@Test
	void whenEmptyConversationHistoryThenThrowsException() {
		var toolExecutionResult = ToolExecutionResult.builder().conversationHistory(List.of()).build();

		assertThatThrownBy(() -> ToolExecutionResult.buildGenerations(toolExecutionResult))
			.isInstanceOf(ArrayIndexOutOfBoundsException.class);
	}

	@Test
	void whenToolResponseWithEmptyResponseListThenEmptyGenerations() {
		var toolExecutionResult = ToolExecutionResult.builder()
			.conversationHistory(
					List.of(new AssistantMessage("Processing request"), new ToolResponseMessage(List.of())))
			.build();

		var generations = ToolExecutionResult.buildGenerations(toolExecutionResult);

		assertThat(generations).isEmpty();
	}

	@Test
	void whenToolResponseWithNullContentThenGenerationWithNullText() {
		var toolExecutionResult = ToolExecutionResult.builder()
			.conversationHistory(
					List.of(new ToolResponseMessage(List.of(new ToolResponseMessage.ToolResponse("1", "tool", null)))))
			.build();

		var generations = ToolExecutionResult.buildGenerations(toolExecutionResult);

		assertThat(generations).hasSize(1);
		assertThat(generations.get(0).getOutput().getText()).isNull();
	}

	@Test
	void whenToolResponseWithEmptyStringContentThenGenerationWithEmptyText() {
		var toolExecutionResult = ToolExecutionResult.builder()
			.conversationHistory(
					List.of(new ToolResponseMessage(List.of(new ToolResponseMessage.ToolResponse("1", "tool", "")))))
			.build();

		var generations = ToolExecutionResult.buildGenerations(toolExecutionResult);

		assertThat(generations).hasSize(1);
		assertThat(generations.get(0).getOutput().getText()).isEmpty();
		assertThat((String) generations.get(0).getMetadata().get(ToolExecutionResult.METADATA_TOOL_NAME))
			.isEqualTo("tool");
	}

	@Test
	void whenBuilderCalledWithoutConversationHistoryThenThrowsException() {
		var toolExecutionResult = ToolExecutionResult.builder().build();

		assertThatThrownBy(() -> ToolExecutionResult.buildGenerations(toolExecutionResult))
			.isInstanceOf(ArrayIndexOutOfBoundsException.class);

		assertThat(toolExecutionResult.conversationHistory()).isNotNull();
		assertThat(toolExecutionResult.conversationHistory()).isEmpty();
	}

}
