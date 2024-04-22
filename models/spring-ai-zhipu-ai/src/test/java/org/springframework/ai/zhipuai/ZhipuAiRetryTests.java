package org.springframework.ai.zhipuai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.zhipuai.api.ZhipuAiApi;
import org.springframework.ai.zhipuai.api.ZhipuAiApi.*;
import org.springframework.ai.zhipuai.api.ZhipuAiApi.ChatCompletionMessage.Role;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.when;

/**
 * @author Ricken Bazolo
 */
@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
public class ZhipuAiRetryTests {

	private class TestRetryListener implements RetryListener {

		int onErrorRetryCount = 0;

		int onSuccessRetryCount = 0;

		@Override
		public <T, E extends Throwable> void onSuccess(RetryContext context, RetryCallback<T, E> callback, T result) {
			onSuccessRetryCount = context.getRetryCount();
		}

		@Override
		public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback,
				Throwable throwable) {
			onErrorRetryCount = context.getRetryCount();
		}

	}

	private TestRetryListener retryListener;

	private RetryTemplate retryTemplate;

	private @Mock ZhipuAiApi zhipuAiApi;

	private ZhipuAiChatClient chatClient;

	private ZhipuAiEmbeddingClient embeddingClient;

	@BeforeEach
	public void beforeEach() {
		retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;
		retryListener = new TestRetryListener();
		retryTemplate.registerListener(retryListener);

		chatClient = new ZhipuAiChatClient(zhipuAiApi,
				ZhipuAiChatOptions.builder()
					.withTemperature(0.7f)
					.withTopP(1f)
					.withModel(ChatModel.GLM_4.getValue())
					.build(),
				null, retryTemplate);
		embeddingClient = new ZhipuAiEmbeddingClient(zhipuAiApi, MetadataMode.EMBED,
				ZhipuAiEmbeddingOptions.builder().withModel(ZhipuAiApi.EmbeddingModel.EMBED.getValue()).build(),
				retryTemplate);
	}

	@Test
	public void zhipuAiChatTransientError() {

		var choice = new ChatCompletion.Choice(0, new ChatCompletionMessage("Response", Role.ASSISTANT),
				ChatCompletionFinishReason.STOP);
		ChatCompletion expectedChatCompletion = new ChatCompletion("id", List.of(choice), 789L, "model", "",
				new ZhipuAiApi.Usage(10, 10, 10));

		when(zhipuAiApi.chatCompletionEntity(isA(ChatCompletionRequest.class)))
			.thenThrow(new TransientAiException("Transient Error 1"))
			.thenThrow(new TransientAiException("Transient Error 2"))
			.thenReturn(ResponseEntity.of(Optional.of(expectedChatCompletion)));

		var result = chatClient.call(new Prompt("text"));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getContent()).isSameAs("Response");
		assertThat(retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void zhipuAiChatNonTransientError() {
		when(zhipuAiApi.chatCompletionEntity(isA(ChatCompletionRequest.class)))
				.thenThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> chatClient.call(new Prompt("text")));
	}

	@Test
	public void zhipuAiChatStreamTransientError() {

		var choice = new ChatCompletionChunk.ChunkChoice(0, new ChatCompletionMessage("Response", Role.ASSISTANT),
				ChatCompletionFinishReason.STOP);
		ChatCompletionChunk expectedChatCompletion = new ChatCompletionChunk("id", 789L, "model", List.of(choice));

		when(zhipuAiApi.chatCompletionStream(isA(ChatCompletionRequest.class)))
			.thenThrow(new TransientAiException("Transient Error 1"))
			.thenThrow(new TransientAiException("Transient Error 2"))
			.thenReturn(Flux.just(expectedChatCompletion));

		var result = chatClient.stream(new Prompt("text"));

		assertThat(result).isNotNull();
		assertThat(result.collectList().block().get(0).getResult().getOutput().getContent()).isSameAs("Response");
		assertThat(retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void zhipuAiChatStreamNonTransientError() {
		when(zhipuAiApi.chatCompletionStream(isA(ChatCompletionRequest.class)))
				.thenThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> chatClient.stream(new Prompt("text")));
	}

	@Test
	public void zhipuAiEmbeddingTransientError() {

		EmbeddingList<Embedding> expectedEmbeddings = new EmbeddingList<>("list",
				List.of(new Embedding(0, List.of(9.9, 8.8))), "model", new ZhipuAiApi.Usage(10, 10, 10));

		when(zhipuAiApi.embeddings(isA(EmbeddingRequest.class)))
			.thenThrow(new TransientAiException("Transient Error 1"))
			.thenThrow(new TransientAiException("Transient Error 2"))
			.thenReturn(ResponseEntity.of(Optional.of(expectedEmbeddings)));

		var result = embeddingClient
			.call(new org.springframework.ai.embedding.EmbeddingRequest(List.of("text1", "text2"), null));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput()).isEqualTo(List.of(9.9, 8.8));
		assertThat(retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void zhipuAiEmbeddingNonTransientError() {
		when(zhipuAiApi.embeddings(isA(EmbeddingRequest.class)))
				.thenThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> embeddingClient
				.call(new org.springframework.ai.embedding.EmbeddingRequest(List.of("text1", "text2"), null)));
	}

}
