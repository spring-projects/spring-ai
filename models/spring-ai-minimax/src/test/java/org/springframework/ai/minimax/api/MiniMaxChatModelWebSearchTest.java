package org.springframework.ai.minimax.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.minimax.MiniMaxChatModel;
import org.springframework.ai.minimax.MiniMaxChatOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class MiniMaxChatModelWebSearchTest {

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
	void testStandardResponseHandling() {
		MiniMaxApi.ChatCompletion mockCompletion = createMockStandardCompletion();
		ResponseEntity<MiniMaxApi.ChatCompletion> mockResponse = ResponseEntity.ok(mockCompletion);

		when(retryTemplate.execute(any())).thenReturn(mockResponse);

		Prompt prompt = new Prompt("Test prompt");
		ChatResponse response = miniMaxChatModel.call(prompt);

		assertNotNull(response);
		assertEquals(1, response.getResults().size());
		assertEquals("Standard response", response.getResults().get(0).getOutput().getContent());

		MiniMaxApi.ChatCompletion.Choice choice = mockCompletion.choices().get(0);
		assertNotNull(choice.message());
		assertNull(choice.messages());
	}

	@Test
	void testWebSearchResponseHandling() {
		MiniMaxApi.ChatCompletion mockCompletion = createMockWebSearchCompletion();
		ResponseEntity<MiniMaxApi.ChatCompletion> mockResponse = ResponseEntity.ok(mockCompletion);

		when(retryTemplate.execute(any())).thenReturn(mockResponse);

		Prompt prompt = new Prompt("Test prompt");
		ChatResponse response = miniMaxChatModel.call(prompt);

		assertNotNull(response);
		assertEquals(1, response.getResults().size());
		assertEquals("Web search response", response.getResults().get(0).getOutput().getContent());

		MiniMaxApi.ChatCompletion.Choice choice = mockCompletion.choices().get(0);
		assertNull(choice.message());
		assertNotNull(choice.messages());
		assertEquals(1, choice.messages().size());
	}

	private MiniMaxApi.ChatCompletion createMockStandardCompletion() {
		MiniMaxApi.ChatCompletionMessage message = new MiniMaxApi.ChatCompletionMessage("Standard response", MiniMaxApi.ChatCompletionMessage.Role.ASSISTANT);
		MiniMaxApi.ChatCompletion.Choice choice = new MiniMaxApi.ChatCompletion.Choice(
				MiniMaxApi.ChatCompletionFinishReason.STOP, 0, message, null, null);
		MiniMaxApi.Usage usage = new MiniMaxApi.Usage(10, 20, 30);
		return new MiniMaxApi.ChatCompletion("test-id", List.of(choice), 1234567890L, "test-model", null, "chat.completion", null, usage);
	}

	private MiniMaxApi.ChatCompletion createMockWebSearchCompletion() {
		MiniMaxApi.ChatCompletionMessage message = new MiniMaxApi.ChatCompletionMessage("Web search response", MiniMaxApi.ChatCompletionMessage.Role.ASSISTANT);
		MiniMaxApi.ChatCompletion.Choice choice = new MiniMaxApi.ChatCompletion.Choice(
				MiniMaxApi.ChatCompletionFinishReason.STOP, 0, null, List.of(message), null);
		MiniMaxApi.Usage usage = new MiniMaxApi.Usage(15, 25, 40);
		return new MiniMaxApi.ChatCompletion("test-id", List.of(choice), 1234567890L, "test-model", null, "chat.completion", null, usage);
	}
}