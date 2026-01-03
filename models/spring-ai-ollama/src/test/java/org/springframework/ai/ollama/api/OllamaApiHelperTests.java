/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.ollama.api;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link OllamaApiHelper}
 *
 * @author Sun Yuhan
 */
@ExtendWith(MockitoExtension.class)
class OllamaApiHelperTests {

	@Test
	void isStreamingToolCallWhenResponseIsNullShouldReturnFalse() {
		boolean result = OllamaApiHelper.isStreamingToolCall(null);
		assertThat(result).isFalse();
	}

	@Test
	void isStreamingToolCallWhenMessageIsNullShouldReturnFalse() {
		OllamaApi.ChatResponse response = mock(OllamaApi.ChatResponse.class);
		when(response.message()).thenReturn(null);

		boolean result = OllamaApiHelper.isStreamingToolCall(response);
		assertThat(result).isFalse();
	}

	@Test
	void isStreamingToolCallWhenToolCallsIsNullShouldReturnFalse() {
		OllamaApi.ChatResponse response = mock(OllamaApi.ChatResponse.class);
		OllamaApi.Message message = mock(OllamaApi.Message.class);
		when(response.message()).thenReturn(message);
		when(message.toolCalls()).thenReturn(null);

		boolean result = OllamaApiHelper.isStreamingToolCall(response);
		assertThat(result).isFalse();
	}

	@Test
	void isStreamingToolCallWhenToolCallsIsEmptyShouldReturnFalse() {
		OllamaApi.ChatResponse response = mock(OllamaApi.ChatResponse.class);
		OllamaApi.Message message = mock(OllamaApi.Message.class);
		when(response.message()).thenReturn(message);
		when(message.toolCalls()).thenReturn(Collections.emptyList());

		boolean result = OllamaApiHelper.isStreamingToolCall(response);
		assertThat(result).isFalse();
	}

	@Test
	void isStreamingToolCallWhenToolCallsHasElementsShouldReturnTrue() {
		OllamaApi.ChatResponse response = mock(OllamaApi.ChatResponse.class);
		OllamaApi.Message message = mock(OllamaApi.Message.class);
		List<OllamaApi.Message.ToolCall> toolCalls = Arrays.asList(mock(OllamaApi.Message.ToolCall.class));
		when(response.message()).thenReturn(message);
		when(message.toolCalls()).thenReturn(toolCalls);

		boolean result = OllamaApiHelper.isStreamingToolCall(response);
		assertThat(result).isTrue();
	}

	@Test
	void isStreamingDoneWhenResponseIsNullShouldReturnFalse() {
		boolean result = OllamaApiHelper.isStreamingDone(null);
		assertThat(result).isFalse();
	}

	@Test
	void isStreamingDoneWhenDoneIsFalseShouldReturnFalse() {
		OllamaApi.ChatResponse response = mock(OllamaApi.ChatResponse.class);
		when(response.done()).thenReturn(false);

		boolean result = OllamaApiHelper.isStreamingDone(response);
		assertThat(result).isFalse();
	}

	@Test
	void isStreamingDoneWhenDoneReasonIsNotStopShouldReturnFalse() {
		OllamaApi.ChatResponse response = mock(OllamaApi.ChatResponse.class);
		when(response.done()).thenReturn(true);
		when(response.doneReason()).thenReturn("other");

		boolean result = OllamaApiHelper.isStreamingDone(response);
		assertThat(result).isFalse();
	}

	@Test
	void isStreamingDoneWhenDoneIsTrueAndDoneReasonIsStopShouldReturnTrue() {
		OllamaApi.ChatResponse response = mock(OllamaApi.ChatResponse.class);
		when(response.done()).thenReturn(true);
		when(response.doneReason()).thenReturn("stop");

		boolean result = OllamaApiHelper.isStreamingDone(response);
		assertThat(result).isTrue();
	}

	@Test
	void mergeWhenBothResponsesHaveValuesShouldMergeCorrectly() {
		Instant previousCreatedAt = Instant.now().minusSeconds(10);
		OllamaApi.Message previousMessage = OllamaApi.Message.builder(OllamaApi.Message.Role.ASSISTANT)
			.content("Previous content")
			.thinking("Previous thinking")
			.images(Arrays.asList("image1"))
			.toolCalls(Arrays.asList(mock(OllamaApi.Message.ToolCall.class)))
			.toolName("Previous tool")
			.build();

		OllamaApi.ChatResponse previous = new OllamaApi.ChatResponse("previous-model", previousCreatedAt,
				previousMessage, "previous-reason", false, 100L, 50L, 10, 200L, 5, 100L);

		Instant currentCreatedAt = Instant.now();
		OllamaApi.Message currentMessage = OllamaApi.Message.builder(OllamaApi.Message.Role.USER)
			.content("Current content")
			.thinking("Current thinking")
			.images(Arrays.asList("image2"))
			.toolCalls(Arrays.asList(mock(OllamaApi.Message.ToolCall.class)))
			.toolName("Current tool")
			.build();

		OllamaApi.ChatResponse current = new OllamaApi.ChatResponse("current-model", currentCreatedAt, currentMessage,
				"stop", true, 200L, 100L, 20, 400L, 10, 200L);

		OllamaApi.ChatResponse result = OllamaApiHelper.merge(previous, current);

		assertThat(result.model()).isEqualTo("current-model");
		assertThat(result.createdAt()).isEqualTo(currentCreatedAt);
		assertThat(result.message().content()).isEqualTo("Previous contentCurrent content");
		assertThat(result.message().thinking()).isEqualTo("Previous thinkingCurrent thinking");
		assertThat(result.message().role()).isEqualTo(OllamaApi.Message.Role.USER);
		assertThat(result.message().images()).containsExactly("image1", "image2");
		assertThat(result.message().toolCalls()).hasSize(2);
		assertThat(result.message().toolName()).isEqualTo("Previous toolCurrent tool");
		assertThat(result.doneReason()).isEqualTo("stop");
		assertThat(result.done()).isTrue();
		assertThat(result.totalDuration()).isEqualTo(300L);
		assertThat(result.loadDuration()).isEqualTo(150L);
		assertThat(result.promptEvalCount()).isEqualTo(30);
		assertThat(result.promptEvalDuration()).isEqualTo(600L);
		assertThat(result.evalCount()).isEqualTo(15);
		assertThat(result.evalDuration()).isEqualTo(300L);
	}

	@Test
	void mergeStringsShouldConcatenate() {
		OllamaApi.Message previousMessage = OllamaApi.Message.builder(OllamaApi.Message.Role.ASSISTANT)
			.content("Hello")
			.thinking("Think")
			.toolName("Tool")
			.build();
		OllamaApi.ChatResponse previous = new OllamaApi.ChatResponse("model1", Instant.now(), previousMessage,
				"reason1", false, null, null, null, null, null, null);

		OllamaApi.Message currentMessage = OllamaApi.Message.builder(OllamaApi.Message.Role.ASSISTANT)
			.content(" World")
			.thinking("ing")
			.toolName("Box")
			.build();
		OllamaApi.ChatResponse current = new OllamaApi.ChatResponse("model2", Instant.now(), currentMessage, "reason2",
				true, null, null, null, null, null, null);

		OllamaApi.ChatResponse result = OllamaApiHelper.merge(previous, current);

		assertThat(result.message().content()).isEqualTo("Hello World");
		assertThat(result.message().thinking()).isEqualTo("Thinking");
		assertThat(result.message().toolName()).isEqualTo("ToolBox");
		assertThat(result.doneReason()).isEqualTo("reason2");
		assertThat(result.done()).isTrue();
	}

	@Test
	void mergeNumbersShouldSum() {
		OllamaApi.Message dummyMessage = OllamaApi.Message.builder(OllamaApi.Message.Role.ASSISTANT).build();

		OllamaApi.ChatResponse previous = new OllamaApi.ChatResponse(null, null, dummyMessage, null, null, 100L, 50L,
				10, 200L, 5, 100L);

		OllamaApi.ChatResponse current = new OllamaApi.ChatResponse(null, null, dummyMessage, null, null, 200L, 100L,
				20, 400L, 10, 200L);

		OllamaApi.ChatResponse result = OllamaApiHelper.merge(previous, current);

		assertThat(result.totalDuration()).isEqualTo(300L);
		assertThat(result.loadDuration()).isEqualTo(150L);
		assertThat(result.promptEvalCount()).isEqualTo(30);
		assertThat(result.promptEvalDuration()).isEqualTo(600L);
		assertThat(result.evalCount()).isEqualTo(15);
		assertThat(result.evalDuration()).isEqualTo(300L);
	}

	@Test
	void mergeListsShouldCombine() {
		OllamaApi.Message previousMessage = OllamaApi.Message.builder(OllamaApi.Message.Role.ASSISTANT)
			.images(Arrays.asList("image1", "image2"))
			.build();
		OllamaApi.ChatResponse previous = new OllamaApi.ChatResponse(null, null, previousMessage, null, null, null,
				null, null, null, null, null);

		OllamaApi.Message currentMessage = OllamaApi.Message.builder(OllamaApi.Message.Role.ASSISTANT)
			.images(Arrays.asList("image3", "image4"))
			.build();
		OllamaApi.ChatResponse current = new OllamaApi.ChatResponse(null, null, currentMessage, null, null, null, null,
				null, null, null, null);

		OllamaApi.ChatResponse result = OllamaApiHelper.merge(previous, current);

		assertThat(result.message().images()).containsExactly("image1", "image2", "image3", "image4");
	}

}
