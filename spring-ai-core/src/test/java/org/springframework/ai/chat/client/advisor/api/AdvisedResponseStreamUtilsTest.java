package org.springframework.ai.chat.client.advisor.api;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link AdvisedResponseStreamUtils}.
 *
 * @author ghdcksgml1
 */
class AdvisedResponseStreamUtilsTest {

	@Nested
	class OnFinishReason {

		@Test
		void whenChatResponseIsNullThenReturnFalse() {
			AdvisedResponse response = mock(AdvisedResponse.class);
			given(response.response()).willReturn(null);

			boolean result = AdvisedResponseStreamUtils.onFinishReason().test(response);

			assertFalse(result);
		}

		@Test
		void whenChatResponseResultsIsNullThenReturnFalse() {
			AdvisedResponse response = mock(AdvisedResponse.class);
			ChatResponse chatResponse = mock(ChatResponse.class);

			given(chatResponse.getResults()).willReturn(null);
			given(response.response()).willReturn(chatResponse);

			boolean result = AdvisedResponseStreamUtils.onFinishReason().test(response);

			assertFalse(result);
		}

		@Test
		void whenChatIsRunningThenReturnFalse() {
			AdvisedResponse response = mock(AdvisedResponse.class);
			ChatResponse chatResponse = mock(ChatResponse.class);

			Generation generation = new Generation(new AssistantMessage("running.."), ChatGenerationMetadata.NULL);

			given(chatResponse.getResults()).willReturn(List.of(generation));
			given(response.response()).willReturn(chatResponse);

			boolean result = AdvisedResponseStreamUtils.onFinishReason().test(response);

			assertFalse(result);
		}

		@Test
		void whenChatIsStopThenReturnTrue() {
			AdvisedResponse response = mock(AdvisedResponse.class);
			ChatResponse chatResponse = mock(ChatResponse.class);

			Generation generation = new Generation(new AssistantMessage("finish."),
					ChatGenerationMetadata.builder().finishReason("STOP").build());

			given(chatResponse.getResults()).willReturn(List.of(generation));
			given(response.response()).willReturn(chatResponse);

			boolean result = AdvisedResponseStreamUtils.onFinishReason().test(response);

			assertTrue(result);
		}

	}

}
