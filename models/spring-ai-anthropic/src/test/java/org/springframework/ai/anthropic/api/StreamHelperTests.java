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

}
