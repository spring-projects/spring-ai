/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.openai;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import org.springframework.ai.openai.setup.OpenAiSetup;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests for OpenAI client ownership and closing {@link OpenAiChatModel}.
 *
 * @author Emrys
 */
class OpenAiChatModelCloseTests {

	private final OpenAIClient openAiClient = mock(OpenAIClient.class);

	private final OpenAIClientAsync openAiClientAsync = mock(OpenAIClientAsync.class);

	@Test
	void closeDoesNotCloseBorrowedClients() {
		OpenAiChatModel model = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.build();

		model.close();

		verify(this.openAiClient, never()).close();
		verify(this.openAiClientAsync, never()).close();
	}

	@Test
	void closeClosesOnlyOwnedAsyncClient() {
		OpenAiChatModel model = buildModel(OpenAiChatModel.builder().openAiClient(this.openAiClient));

		model.close();

		verify(this.openAiClient, never()).close();
		verify(this.openAiClientAsync).close();
	}

	@Test
	void closeClosesOnlyOwnedSyncClient() {
		OpenAiChatModel model = buildModel(OpenAiChatModel.builder().openAiClientAsync(this.openAiClientAsync));

		model.close();

		verify(this.openAiClient).close();
		verify(this.openAiClientAsync, never()).close();
	}

	@Test
	void closeClosesBothOwnedClients() {
		OpenAiChatModel model = buildModel(OpenAiChatModel.builder());

		model.close();

		verify(this.openAiClient).close();
		verify(this.openAiClientAsync).close();
	}

	@Test
	void closeAttemptsBothClientsAndSuppressesSecondFailure() {
		RuntimeException syncFailure = new IllegalStateException("sync close failed");
		RuntimeException asyncFailure = new IllegalStateException("async close failed");
		doThrow(syncFailure).when(this.openAiClient).close();
		doThrow(asyncFailure).when(this.openAiClientAsync).close();
		OpenAiChatModel model = buildModel(OpenAiChatModel.builder());

		assertThatThrownBy(model::close).isSameAs(syncFailure).hasSuppressedException(asyncFailure);

		verify(this.openAiClient).close();
		verify(this.openAiClientAsync).close();
	}

	@Test
	void closeStillClosesAsyncClientWhenSyncClientFails() {
		RuntimeException failure = new IllegalStateException("sync close failed");
		doThrow(failure).when(this.openAiClient).close();
		OpenAiChatModel model = buildModel(OpenAiChatModel.builder());

		assertThatThrownBy(model::close).isSameAs(failure).hasNoSuppressedExceptions();

		verify(this.openAiClient).close();
		verify(this.openAiClientAsync).close();
	}

	@Test
	void closePropagatesAsyncClientFailure() {
		RuntimeException failure = new IllegalStateException("async close failed");
		doThrow(failure).when(this.openAiClientAsync).close();
		OpenAiChatModel model = buildModel(OpenAiChatModel.builder());

		assertThatThrownBy(model::close).isSameAs(failure).hasNoSuppressedExceptions();

		verify(this.openAiClient).close();
		verify(this.openAiClientAsync).close();
	}

	@Test
	void closeDoesNotCloseClientsTwice() {
		OpenAiChatModel model = buildModel(OpenAiChatModel.builder());

		model.close();
		model.close();

		verify(this.openAiClient).close();
		verify(this.openAiClientAsync).close();
		verifyNoMoreInteractions(this.openAiClient, this.openAiClientAsync);
	}

	@Test
	void closeDoesNotRetryAfterFailure() {
		RuntimeException failure = new IllegalStateException("sync close failed");
		doThrow(failure).when(this.openAiClient).close();
		OpenAiChatModel model = buildModel(OpenAiChatModel.builder());

		assertThatThrownBy(model::close).isSameAs(failure);
		model.close();

		verify(this.openAiClient).close();
		verify(this.openAiClientAsync).close();
		verifyNoMoreInteractions(this.openAiClient, this.openAiClientAsync);
	}

	@Test
	void closeDoesNotSuppressFailureOntoItself() {
		RuntimeException failure = new IllegalStateException("shared close failure");
		doThrow(failure).when(this.openAiClient).close();
		doThrow(failure).when(this.openAiClientAsync).close();
		OpenAiChatModel model = buildModel(OpenAiChatModel.builder());

		assertThatThrownBy(model::close).isSameAs(failure).hasNoSuppressedExceptions();

		verify(this.openAiClient).close();
		verify(this.openAiClientAsync).close();
	}

	private OpenAiChatModel buildModel(OpenAiChatModel.Builder builder) {
		try (MockedStatic<OpenAiSetup> setup = mockStatic(OpenAiSetup.class,
				invocation -> switch (invocation.getMethod().getName()) {
					case "setupSyncClient" -> this.openAiClient;
					case "setupAsyncClient" -> this.openAiClientAsync;
					default -> throw new AssertionError("Unexpected OpenAiSetup method: " + invocation.getMethod());
				})) {
			return builder.build();
		}
	}

}
