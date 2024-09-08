package org.springframework.ai.minimax.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.minimax.MiniMaxChatModel;
import org.springframework.ai.minimax.MiniMaxChatOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class MiniMaxChatModelNullSafetyTest {

	@Mock
	private MiniMaxApi miniMaxApi;

	@Mock
	private RetryTemplate retryTemplate;

	private MiniMaxChatModel miniMaxChatModel;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		MiniMaxChatOptions options = MiniMaxChatOptions.builder()
				.withModel(MiniMaxApi.DEFAULT_CHAT_MODEL)
				.withTemperature(0.7f)
				.build();
		miniMaxChatModel = new MiniMaxChatModel(miniMaxApi, options, null, retryTemplate);
	}

	@Test
	void testNullMessageHandling() {
		MiniMaxApi.ChatCompletion mockCompletion = createMockCompletionWithNullMessage();
		ResponseEntity<MiniMaxApi.ChatCompletion> mockResponse = ResponseEntity.ok(mockCompletion);

		when(retryTemplate.execute(any())).thenReturn(mockResponse);

		Prompt prompt = new Prompt("Test prompt");
		ChatResponse response = miniMaxChatModel.call(prompt);

		assertNotNull(response);
		assertTrue(response.getResults().isEmpty());
	}

	@Test
	void testNullRoleHandling() {
		MiniMaxApi.ChatCompletion mockCompletion = createMockCompletionWithNullRole();
		ResponseEntity<MiniMaxApi.ChatCompletion> mockResponse = ResponseEntity.ok(mockCompletion);

		when(retryTemplate.execute(any())).thenReturn(mockResponse);

		Prompt prompt = new Prompt("Test prompt");
		ChatResponse response = miniMaxChatModel.call(prompt);

		assertNotNull(response);
		assertEquals(1, response.getResults().size());
		Generation generation = response.getResults().get(0);
		assertNotNull(generation);
		assertTrue(generation.getOutput() instanceof AssistantMessage);
		AssistantMessage assistantMessage = (AssistantMessage) generation.getOutput();
		assertNotNull(assistantMessage.getContent());
		assertEquals("", assistantMessage.getMetadata().get("role"));
	}

	private MiniMaxApi.ChatCompletion createMockCompletionWithNullMessage() {
		MiniMaxApi.ChatCompletion.Choice choice = new MiniMaxApi.ChatCompletion.Choice(
				MiniMaxApi.ChatCompletionFinishReason.STOP, 0, null, null, null);
		MiniMaxApi.Usage usage = new MiniMaxApi.Usage(10, 20, 30);
		return new MiniMaxApi.ChatCompletion("test-id", List.of(choice), 1234567890L, "test-model", null, "chat.completion", null, usage);
	}

	private MiniMaxApi.ChatCompletion createMockCompletionWithNullRole() {
		MiniMaxApi.ChatCompletionMessage message = new MiniMaxApi.ChatCompletionMessage("Test message", null);
		MiniMaxApi.ChatCompletion.Choice choice = new MiniMaxApi.ChatCompletion.Choice(
				MiniMaxApi.ChatCompletionFinishReason.STOP, 0, message, null, null);
		MiniMaxApi.Usage usage = new MiniMaxApi.Usage(10, 20, 30);
		return new MiniMaxApi.ChatCompletion("test-id", List.of(choice), 1234567890L, "test-model", null, "chat.completion", null, usage);
	}
}