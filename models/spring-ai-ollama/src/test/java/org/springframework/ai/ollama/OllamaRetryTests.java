package org.springframework.ai.ollama;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.when;

/**
 * Tests for the OllamaRetryTests class.
 *
 * @author Alexandros Pappas
 */
@ExtendWith(MockitoExtension.class)
class OllamaRetryTests {

	private static final String MODEL = OllamaModel.LLAMA3_2.getName();

	private TestRetryListener retryListener;

	private RetryTemplate retryTemplate;

	@Mock
	private OllamaApi ollamaApi;

	private OllamaChatModel chatModel;

	@BeforeEach
	public void beforeEach() {
		this.retryTemplate = RetryUtils.SHORT_RETRY_TEMPLATE;
		this.retryListener = new TestRetryListener();
		this.retryTemplate.registerListener(this.retryListener);

		this.chatModel = OllamaChatModel.builder()
			.ollamaApi(this.ollamaApi)
			.defaultOptions(OllamaOptions.builder().model(MODEL).temperature(0.9).build())
			.retryTemplate(this.retryTemplate)
			.build();
	}

	@Test
	void ollamaChatTransientError() {
		String promptText = "What is the capital of Bulgaria and what is the size? What it the national anthem?";
		var expectedChatResponse = new OllamaApi.ChatResponse("CHAT_COMPLETION_ID", Instant.now(),
				OllamaApi.Message.builder(OllamaApi.Message.Role.ASSISTANT).content("Response").build(), null, true,
				null, null, null, null, null, null);

		when(this.ollamaApi.chat(isA(OllamaApi.ChatRequest.class)))
			.thenThrow(new TransientAiException("Transient Error 1"))
			.thenThrow(new TransientAiException("Transient Error 2"))
			.thenReturn(expectedChatResponse);

		var result = this.chatModel.call(new Prompt(promptText));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getText()).isSameAs("Response");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);
	}

	private static class TestRetryListener implements RetryListener {

		int onErrorRetryCount = 0;

		int onSuccessRetryCount = 0;

		@Override
		public <T, E extends Throwable> void onSuccess(RetryContext context, RetryCallback<T, E> callback, T result) {
			this.onSuccessRetryCount = context.getRetryCount();
		}

		@Override
		public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback,
				Throwable throwable) {
			this.onErrorRetryCount = context.getRetryCount();
		}

	}

}
