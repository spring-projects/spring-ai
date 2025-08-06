package org.springframework.ai.anthropic.api;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import org.springframework.ai.anthropic.api.StreamHelper.ChatCompletionResponseBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ilayaperumal Gopinathan
 */
class StreamHelperTest {

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
