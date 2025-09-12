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

package org.springframework.ai.anthropic.api;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import org.springframework.ai.anthropic.api.StreamHelper.ChatCompletionResponseBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ilayaperumal Gopinathan
 */
class StreamHelperTests {

	@Test
	void testErrorEventTypeWithEmptyContentBlock() {
		AnthropicApi.ErrorEvent errorEvent = new AnthropicApi.ErrorEvent(AnthropicApi.EventType.ERROR,
				new AnthropicApi.ErrorEvent.Error("error", "error message"));
		AtomicReference<ChatCompletionResponseBuilder> contentBlockReference = new AtomicReference<>();
		StreamHelper streamHelper = new StreamHelper();
		AnthropicApi.ChatCompletionResponse response = streamHelper.eventToChatCompletionResponse(errorEvent,
				contentBlockReference);
		assertThat(response).isNotNull();
	}

	@Test
	void testMultipleErrorEventsHandling() {
		StreamHelper streamHelper = new StreamHelper();
		AtomicReference<ChatCompletionResponseBuilder> contentBlockReference = new AtomicReference<>();

		AnthropicApi.ErrorEvent firstError = new AnthropicApi.ErrorEvent(AnthropicApi.EventType.ERROR,
				new AnthropicApi.ErrorEvent.Error("validation_error", "Invalid input"));
		AnthropicApi.ErrorEvent secondError = new AnthropicApi.ErrorEvent(AnthropicApi.EventType.ERROR,
				new AnthropicApi.ErrorEvent.Error("server_error", "Internal server error"));

		AnthropicApi.ChatCompletionResponse response1 = streamHelper.eventToChatCompletionResponse(firstError,
				contentBlockReference);
		AnthropicApi.ChatCompletionResponse response2 = streamHelper.eventToChatCompletionResponse(secondError,
				contentBlockReference);

		assertThat(response1).isNotNull();
		assertThat(response2).isNotNull();
	}

}
