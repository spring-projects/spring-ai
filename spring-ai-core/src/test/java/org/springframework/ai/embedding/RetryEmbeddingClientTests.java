/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.embedding;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Christian Tzolov
 */
public class RetryEmbeddingClientTests {

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

	@BeforeEach
	public void beforeEach() {
		retryTemplate = RetryTemplate.builder()
			.maxAttempts(3)
			.exponentialBackoff(Duration.ofMillis(100), 5, Duration.ofSeconds(60))
			.build();
		retryListener = new TestRetryListener();
		retryTemplate.registerListener(retryListener);
	}

	@Test
	public void retryEmbedString() {

		List<Double> returnEmbedding = List.of(1.0d, 2.0d, 3.0d);

		EmbeddingClient delegate = mock(EmbeddingClient.class);

		when(delegate.embed(anyString())).thenThrow(new RuntimeException())
			.thenThrow(new RuntimeException())
			.thenReturn(returnEmbedding);

		RetryEmbeddingClient client = new RetryEmbeddingClient(retryTemplate, delegate);

		List<Double> embedding = client.embed("test");

		assertThat(embedding).isNotNull();
		assertThat(embedding).isEqualTo(returnEmbedding);
		assertThat(retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void retryEmbedStringList() {

		List<Double> returnEmbedding = List.of(1.0d, 2.0d, 3.0d);

		EmbeddingClient delegate = mock(EmbeddingClient.class);

		when(delegate.embed(anyList())).thenThrow(new RuntimeException())
			.thenThrow(new RuntimeException())
			.thenReturn(List.of(returnEmbedding));

		RetryEmbeddingClient client = new RetryEmbeddingClient(retryTemplate, delegate);

		List<List<Double>> embedding = client.embed(List.of("test"));

		assertThat(embedding).hasSize(1);
		assertThat(embedding.get(0)).isNotNull();
		assertThat(embedding.get(0)).isEqualTo(returnEmbedding);
		assertThat(retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void retryEmbedDocument() {

		List<Double> returnEmbedding = List.of(1.0d, 2.0d, 3.0d);

		EmbeddingClient delegate = mock(EmbeddingClient.class);

		when(delegate.embed(isA(Document.class))).thenThrow(new RuntimeException())
			.thenThrow(new RuntimeException())
			.thenReturn(returnEmbedding);

		RetryEmbeddingClient client = new RetryEmbeddingClient(retryTemplate, delegate);

		List<Double> embedding = client.embed(new Document("test"));

		assertThat(embedding).isNotNull();
		assertThat(embedding).isEqualTo(returnEmbedding);
		assertThat(retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void retryEmbedEmbeddingResponse() {

		EmbeddingClient delegate = mock(EmbeddingClient.class);

		var response = new EmbeddingResponse(List.of(new Embedding(List.of(1.0d, 2.0d, 3.0d), 0)), Map.of());

		when(delegate.embedForResponse(anyList())).thenThrow(new RuntimeException())
			.thenThrow(new RuntimeException())
			.thenReturn(response);

		RetryEmbeddingClient client = new RetryEmbeddingClient(retryTemplate, delegate);

		EmbeddingResponse embedding = client.embedForResponse(List.of("test"));

		assertThat(embedding).isNotNull();
		assertThat(embedding).isSameAs(response);
		assertThat(retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void retryEmbedDimensions() {

		List<Double> returnEmbedding = List.of(1.0d, 2.0d, 3.0d);
		EmbeddingClient delegate = mock(EmbeddingClient.class);

		when(delegate.embed(anyString())).thenThrow(new RuntimeException())
			.thenThrow(new RuntimeException())
			.thenReturn(returnEmbedding);

		RetryEmbeddingClient client = new RetryEmbeddingClient(retryTemplate, delegate);

		int dimensions = client.dimensions();

		assertThat(dimensions).isEqualTo(returnEmbedding.size());
		assertThat(retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(retryListener.onErrorRetryCount).isEqualTo(2);
	}

}
