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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.google.genai.Client;
import com.google.genai.types.ClientOptions;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.ForwardingSource;
import okio.Okio;
import okio.Source;
import okio.Timeout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import reactor.core.Disposable;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class GoogleGenAiChatModelStreamThreadingTests {

	private static final String RESPONSE = """
			data: {"candidates":[{"content":{"role":"model","parts":[{"text":"Hello"}]}}],"modelVersion":"gemini-test"}

			""";

	private final AtomicReference<String> openingThread = new AtomicReference<>();

	private Client client;

	@AfterEach
	void tearDown() {
		this.client.close();
	}

	@Test
	void opensAndReadsStreamOnBoundedElasticWhenDemandComesFromAnotherThread() throws Exception {
		Set<String> readingThreads = ConcurrentHashMap.newKeySet();
		Source source = new ForwardingSource(new Buffer().writeUtf8(RESPONSE.repeat(8))) {
			@Override
			public long read(Buffer sink, long byteCount) throws IOException {
				readingThreads.add(Thread.currentThread().getName());
				return super.read(sink, Math.min(byteCount, RESPONSE.getBytes(StandardCharsets.UTF_8).length));
			}

		};
		ResponseBody body = spy(ResponseBody.create(MediaType.get("text/event-stream"), -1, Okio.buffer(source)));
		GoogleGenAiChatModel model = createChatModel(body);
		Scheduler consumer = Schedulers.newSingle("stream-consumer");
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<Throwable> error = new AtomicReference<>();
		BaseSubscriber<ChatResponse> subscriber = new BaseSubscriber<>() {

			@Override
			protected void hookOnSubscribe(Subscription subscription) {
				request(1);
			}

			@Override
			protected void hookOnNext(ChatResponse value) {
				consumer.schedule(() -> request(1));
			}

			@Override
			protected void hookOnError(Throwable throwable) {
				error.set(throwable);
			}

			@Override
			protected void hookFinally(SignalType type) {
				done.countDown();
			}

		};
		try {
			model.stream(new Prompt("Hello")).subscribeOn(consumer).subscribe(subscriber);

			assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
			assertThat(error.get()).isNull();
			assertThat(this.openingThread.get()).startsWith("boundedElastic-");
			assertThat(readingThreads).isNotEmpty().allSatisfy(name -> assertThat(name).startsWith("boundedElastic-"));
			verify(body, timeout(1000)).close();
		}
		finally {
			subscriber.dispose();
			consumer.dispose();
		}
	}

	@Test
	void closesStreamThatArrivesAfterCancellationWithoutReadingAnotherEvent() throws Exception {
		BlockingEventSource source = new BlockingEventSource();
		ResponseBody body = spy(ResponseBody.create(MediaType.get("text/event-stream"), -1, Okio.buffer(source)));
		GoogleGenAiChatModel model = createChatModel(body);
		Disposable subscription = model.stream(new Prompt("Hello"))
			.subscribeOn(Schedulers.boundedElastic())
			.subscribe();
		try {
			assertThat(source.readStarted.await(5, TimeUnit.SECONDS)).isTrue();
			subscription.dispose();
			source.firstEvent.countDown();

			verify(body, timeout(1000)).close();
			assertThat(source.anotherRead.getCount()).isEqualTo(1);
		}
		finally {
			subscription.dispose();
			source.firstEvent.countDown();
			source.endOfStream.countDown();
			assertThat(source.closed.await(5, TimeUnit.SECONDS)).isTrue();
		}
	}

	@Test
	void preservesStreamCreationExceptionWrapping() {
		IllegalStateException failure = new IllegalStateException("Failed to open the response");
		OkHttpClient httpClient = new OkHttpClient.Builder().addInterceptor(chain -> {
			throw failure;
		}).build();
		GoogleGenAiChatModel model = createChatModel(httpClient);

		assertThatThrownBy(() -> model.stream(new Prompt("Hello")).collectList().block(Duration.ofSeconds(5)))
			.isInstanceOf(RuntimeException.class)
			.hasMessage("Failed to generate content")
			.hasCause(failure);
	}

	@Test
	void cancellationDoesNotBlockEventLoopWhileSdkIsReading() throws Exception {
		BlockingReadSource source = new BlockingReadSource();
		ResponseBody body = spy(ResponseBody.create(MediaType.get("text/event-stream"), -1, Okio.buffer(source)));
		GoogleGenAiChatModel model = createChatModel(body);
		CountDownLatch firstResponse = new CountDownLatch(1);
		CountDownLatch cancelStarted = new CountDownLatch(1);
		CountDownLatch cancelReturned = new CountDownLatch(1);
		AtomicReference<Throwable> error = new AtomicReference<>();
		BaseSubscriber<ChatResponse> subscriber = new BaseSubscriber<>() {

			@Override
			protected void hookOnSubscribe(Subscription subscription) {
				request(1);
			}

			@Override
			protected void hookOnNext(ChatResponse response) {
				firstResponse.countDown();
			}

			@Override
			protected void hookOnError(Throwable failure) {
				error.set(failure);
			}

		};
		Scheduler eventLoop = Schedulers.newSingle("cancel-event-loop");
		try {
			model.stream(new Prompt("Hello")).subscribe(subscriber);
			assertThat(firstResponse.await(5, TimeUnit.SECONDS)).isTrue();
			assertThat(source.readBlocked.await(5, TimeUnit.SECONDS)).isTrue();
			eventLoop.schedule(() -> {
				cancelStarted.countDown();
				subscriber.dispose();
				cancelReturned.countDown();
			});

			assertThat(cancelStarted.await(5, TimeUnit.SECONDS)).isTrue();
			assertThat(cancelReturned.await(2, TimeUnit.SECONDS)).as("cancel must return while the SDK read is blocked")
				.isTrue();
			assertThat(source.releaseRead.getCount()).isEqualTo(1);
			source.releaseRead.countDown();

			verify(body, timeout(5000)).close();
			assertThat(source.closingThread.get()).startsWith("boundedElastic-");
			assertThat(error.get()).isNull();
		}
		finally {
			source.releaseRead.countDown();
			subscriber.dispose();
			assertThat(source.closed.await(5, TimeUnit.SECONDS)).isTrue();
			eventLoop.dispose();
		}
	}

	@Test
	void closesEachResponseOnceWhenCancellationRacesWithOpening() throws Exception {
		AtomicReference<OpeningResponse> nextResponse = new AtomicReference<>();
		OkHttpClient httpClient = new OkHttpClient.Builder().addInterceptor(chain -> {
			OpeningResponse response = nextResponse.get();
			response.started.countDown();
			try {
				response.returnResponse.await(5, TimeUnit.SECONDS);
			}
			catch (Exception ex) {
				throw new IOException("Failed to synchronize response delivery", ex);
			}
			return response(chain.request(), response.body);
		}).build();
		GoogleGenAiChatModel model = createChatModel(httpClient);
		Flux<ChatResponse> responses = model.stream(new Prompt("Hello"));
		List<ResponseBody> bodies = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			OpeningResponse response = new OpeningResponse();
			nextResponse.set(response);
			bodies.add(response.body);
			Disposable subscription = responses.subscribe();
			try {
				assertThat(response.started.await(5, TimeUnit.SECONDS)).isTrue();
				response.returnResponse.await(5, TimeUnit.SECONDS);
				subscription.dispose();
				verify(response.body, timeout(5000)).close();
			}
			finally {
				subscription.dispose();
			}
		}
		bodies.forEach(body -> verify(body).close());
	}

	@Test
	void reportsCleanupFailureForStreamArrivingAfterCancellation() throws Exception {
		BlockingEventSource source = new BlockingEventSource();
		ResponseBody body = spy(ResponseBody.create(MediaType.get("text/event-stream"), -1, Okio.buffer(source)));
		IllegalStateException failure = new IllegalStateException("Failed to close the late response");
		doThrow(failure).when(body).close();
		GoogleGenAiChatModel model = createChatModel(body);
		List<Throwable> errors = new CopyOnWriteArrayList<>();
		CountDownLatch errorReported = new CountDownLatch(1);
		Hooks.onErrorDropped(error -> {
			errors.add(error);
			errorReported.countDown();
		});
		Disposable subscription = model.stream(new Prompt("Hello")).subscribe();
		try {
			assertThat(source.readStarted.await(5, TimeUnit.SECONDS)).isTrue();
			subscription.dispose();
			source.firstEvent.countDown();

			assertThat(errorReported.await(5, TimeUnit.SECONDS)).isTrue();
			assertThat(errors).containsExactly(failure);
			verify(body).close();
			assertThat(source.anotherRead.getCount()).isEqualTo(1);
		}
		finally {
			try {
				source.firstEvent.countDown();
				source.endOfStream.countDown();
				subscription.dispose();
				assertThat(source.closed.await(5, TimeUnit.SECONDS)).isTrue();
			}
			finally {
				Hooks.resetOnErrorDropped();
			}
		}
	}

	private GoogleGenAiChatModel createChatModel(ResponseBody body) {
		OkHttpClient httpClient = new OkHttpClient.Builder().addInterceptor(chain -> {
			this.openingThread.set(Thread.currentThread().getName());
			return response(chain.request(), body);
		}).build();
		return createChatModel(httpClient);
	}

	private GoogleGenAiChatModel createChatModel(OkHttpClient httpClient) {
		this.client = Client.builder()
			.apiKey("test-key")
			.vertexAI(false)
			.clientOptions(ClientOptions.builder().customHttpClient(httpClient).build())
			.build();
		return GoogleGenAiChatModel.builder().genAiClient(this.client).build();
	}

	private static Response response(Request request, ResponseBody body) {
		return new Response.Builder().request(request)
			.protocol(Protocol.HTTP_1_1)
			.code(200)
			.message("OK")
			.header("Content-Type", "text/event-stream")
			.body(body)
			.build();
	}

	private static void awaitIgnoringInterrupts(CountDownLatch latch) throws IOException {
		while (true) {
			try {
				if (!latch.await(10, TimeUnit.SECONDS)) {
					throw new IOException("Timed out waiting for the test to release the response");
				}
				return;
			}
			catch (InterruptedException ex) {
				// Simulate a socket read that does not stop on thread interruption.
			}
		}
	}

	private static final class OpeningResponse {

		private final ResponseBody body = spy(
				ResponseBody.create(RESPONSE.repeat(3), MediaType.get("text/event-stream")));

		private final CountDownLatch started = new CountDownLatch(1);

		private final CyclicBarrier returnResponse = new CyclicBarrier(2);

	}

	private static final class BlockingReadSource implements Source {

		private final CountDownLatch readBlocked = new CountDownLatch(1);

		private final CountDownLatch releaseRead = new CountDownLatch(1);

		private final CountDownLatch closed = new CountDownLatch(1);

		private final AtomicReference<String> closingThread = new AtomicReference<>();

		private int reads;

		@Override
		public long read(Buffer sink, long byteCount) throws IOException {
			if (this.reads++ < 2) {
				sink.writeUtf8(RESPONSE);
				return RESPONSE.getBytes(StandardCharsets.UTF_8).length;
			}
			this.readBlocked.countDown();
			awaitIgnoringInterrupts(this.releaseRead);
			return -1;
		}

		@Override
		public Timeout timeout() {
			return Timeout.NONE;
		}

		@Override
		public void close() {
			this.closingThread.set(Thread.currentThread().getName());
			this.closed.countDown();
		}

	}

	private static final class BlockingEventSource implements Source {

		private final CountDownLatch readStarted = new CountDownLatch(1);

		private final CountDownLatch firstEvent = new CountDownLatch(1);

		private final CountDownLatch anotherRead = new CountDownLatch(1);

		private final CountDownLatch endOfStream = new CountDownLatch(1);

		private final CountDownLatch closed = new CountDownLatch(1);

		private boolean first = true;

		@Override
		public long read(Buffer sink, long byteCount) throws IOException {
			if (this.first) {
				this.first = false;
				this.readStarted.countDown();
				awaitIgnoringInterrupts(this.firstEvent);
				sink.writeUtf8(RESPONSE);
				return RESPONSE.getBytes(StandardCharsets.UTF_8).length;
			}
			this.anotherRead.countDown();
			awaitIgnoringInterrupts(this.endOfStream);
			return -1;
		}

		@Override
		public Timeout timeout() {
			return Timeout.NONE;
		}

		@Override
		public void close() {
			this.closed.countDown();
		}

	}

}
