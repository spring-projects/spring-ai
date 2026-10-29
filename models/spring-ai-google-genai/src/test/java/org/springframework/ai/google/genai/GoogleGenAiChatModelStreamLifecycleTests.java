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

package org.springframework.ai.google.genai;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.ClientOptions;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Hooks;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class GoogleGenAiChatModelStreamLifecycleTests {

	private static final Duration TIMEOUT = Duration.ofSeconds(5);

	private static final String RESPONSE = """
			data: {"candidates":[{"content":{"role":"model","parts":[{"text":"Hello"}]}}],"modelVersion":"gemini-test"}

			""";

	private Client client;

	private ResponseBody responseBody;

	@AfterEach
	void tearDown() {
		this.client.close();
	}

	@Test
	void closesResponseOnCancellation() {
		GoogleGenAiChatModel model = createChatModel(RESPONSE.repeat(3));

		ChatResponse response = model.stream(new Prompt("Hello")).next().block(TIMEOUT);

		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput().getText()).isEqualTo("Hello");

		verify(this.responseBody, timeout(1000)).close();
	}

	@Test
	void closesResponseOnCompletion() {
		GoogleGenAiChatModel model = createChatModel(RESPONSE.repeat(2));

		assertThat(model.stream(new Prompt("Hello")).collectList().block(TIMEOUT)).hasSize(2);

		verify(this.responseBody).close();
	}

	@Test
	void closesResponseOnError() {
		GoogleGenAiChatModel model = createChatModel(RESPONSE + """
				data: {"error":{"code":500,"message":"Stream failed","status":"INTERNAL"}}

				""");

		assertThatExceptionOfType(ApiException.class)
			.isThrownBy(() -> model.stream(new Prompt("Hello")).collectList().block(TIMEOUT));

		verify(this.responseBody).close();
	}

	@Test
	void propagatesCleanupFailureOnCompletion() {
		GoogleGenAiChatModel model = createChatModel(RESPONSE.repeat(2));
		IllegalStateException failure = new IllegalStateException("Failed to close the response");
		doThrow(failure).when(this.responseBody).close();

		assertThatThrownBy(() -> model.stream(new Prompt("Hello")).collectList().block(TIMEOUT)).isSameAs(failure);
		verify(this.responseBody).close();
	}

	@Test
	void retainsStreamErrorWhenCleanupAlsoFails() {
		GoogleGenAiChatModel model = createChatModel(RESPONSE + """
				data: {"error":{"code":500,"message":"Stream failed","status":"INTERNAL"}}

				""");
		IllegalStateException failure = new IllegalStateException("Failed to close the response");
		doThrow(failure).when(this.responseBody).close();

		assertThatThrownBy(() -> model.stream(new Prompt("Hello")).collectList().block(TIMEOUT)).isSameAs(failure)
			.satisfies(error -> assertThat(error.getSuppressed())
				.anySatisfy(suppressed -> assertThat(suppressed).isInstanceOf(ApiException.class)));
		verify(this.responseBody).close();
	}

	@Test
	void reportsCleanupFailureAfterCancellationAsDroppedError() throws Exception {
		GoogleGenAiChatModel model = createChatModel(RESPONSE.repeat(3));
		IllegalStateException failure = new IllegalStateException("Failed to close the response");
		doThrow(failure).when(this.responseBody).close();
		List<Throwable> errors = new CopyOnWriteArrayList<>();
		CountDownLatch errorReported = new CountDownLatch(1);
		Hooks.onErrorDropped(error -> {
			errors.add(error);
			errorReported.countDown();
		});
		try {
			ChatResponse response = model.stream(new Prompt("Hello")).next().block(TIMEOUT);

			assertThat(response).isNotNull();
			assertThat(errorReported.await(5, TimeUnit.SECONDS)).isTrue();
			assertThat(errors).containsExactly(failure);
			verify(this.responseBody, timeout(1000)).close();
		}
		finally {
			Hooks.resetOnErrorDropped();
		}
	}

	private GoogleGenAiChatModel createChatModel(String events) {
		this.responseBody = spy(ResponseBody.create(events, MediaType.get("text/event-stream")));
		OkHttpClient httpClient = new OkHttpClient.Builder()
			.addInterceptor(chain -> new Response.Builder().request(chain.request())
				.protocol(Protocol.HTTP_1_1)
				.code(200)
				.message("OK")
				.header("Content-Type", "text/event-stream")
				.body(this.responseBody)
				.build())
			.build();
		this.client = Client.builder()
			.apiKey("test-key")
			.vertexAI(false)
			.clientOptions(ClientOptions.builder().customHttpClient(httpClient).build())
			.build();
		return GoogleGenAiChatModel.builder().genAiClient(this.client).build();
	}

}
