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

package org.springframework.ai.openai.setup;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterators;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.openai.core.http.StreamResponse;
import io.micrometer.observation.Observation;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Hooks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Tests for {@link OpenAiStreams#stream}, which streams with the sync OpenAI client.
 *
 * @author sajeevKR
 */
class OpenAiStreamsTests {

	private static final Duration TIMEOUT = Duration.ofSeconds(5);

	private final Scheduler scheduler = Schedulers.newSingle("test-stream");

	private final List<Throwable> errors = new CopyOnWriteArrayList<>();

	@BeforeEach
	void setUp() {
		Hooks.onErrorDropped(this.errors::add);
	}

	@AfterEach
	void tearDown() {
		Hooks.resetOnErrorDropped();
		this.scheduler.dispose();
	}

	@Test
	void streamsTheItemsAndClosesTheResponse() {
		FakeStreamResponse response = new FakeStreamResponse(List.of("a", "b").iterator());

		List<String> items = OpenAiStreams.stream(() -> response, Observation.NOOP, this.scheduler)
			.collectList()
			.block(TIMEOUT);

		assertThat(items).containsExactly("a", "b");
		assertThat(response.isClosed()).isTrue();
	}

	@Test
	void sendsTheRequestAndReadsTheResponseOnTheScheduler() {
		AtomicReference<String> requestThread = new AtomicReference<>();
		AtomicReference<String> readThread = new AtomicReference<>();
		Iterator<String> items = List.of("a").iterator();
		FakeStreamResponse response = new FakeStreamResponse(new Iterator<>() {

			@Override
			public boolean hasNext() {
				readThread.set(Thread.currentThread().getName());
				return items.hasNext();
			}

			@Override
			public String next() {
				return items.next();
			}

		});

		OpenAiStreams.stream(() -> {
			requestThread.set(Thread.currentThread().getName());
			return response;
		}, Observation.NOOP, this.scheduler).blockLast(TIMEOUT);

		assertThat(requestThread.get()).startsWith("test-stream");
		assertThat(readThread.get()).startsWith("test-stream");
	}

	@Test
	void sendsTheRequestWhileTheObservationIsCurrent() {
		TestObservationRegistry registry = TestObservationRegistry.create();
		Observation observation = Observation.start("test", registry);
		AtomicReference<Observation> current = new AtomicReference<>();

		OpenAiStreams.stream(() -> {
			current.set(registry.getCurrentObservation());
			return new FakeStreamResponse(List.<String>of().iterator());
		}, observation, this.scheduler).blockLast(TIMEOUT);
		observation.stop();

		assertThat(current.get()).isSameAs(observation);
	}

	@Test
	void requestErrorReachesTheSubscriber() {
		IllegalStateException failure = new IllegalStateException("bad request");

		assertThatThrownBy(() -> OpenAiStreams.<String>stream(() -> {
			throw failure;
		}, Observation.NOOP, this.scheduler).blockLast(TIMEOUT)).isSameAs(failure);
	}

	@Test
	void readErrorReachesTheSubscriberAndClosesTheResponse() {
		UncheckedIOException failure = new UncheckedIOException(new IOException("stream failed"));
		FakeStreamResponse response = new FakeStreamResponse(new Iterator<>() {

			@Override
			public boolean hasNext() {
				throw failure;
			}

			@Override
			public String next() {
				throw new IllegalStateException();
			}

		});

		assertThatThrownBy(
				() -> OpenAiStreams.stream(() -> response, Observation.NOOP, this.scheduler).blockLast(TIMEOUT))
			.isSameAs(failure);
		assertThat(response.isClosed()).isTrue();
	}

	@Test
	void cancelBeforeTheFirstItemClosesTheResponseWithoutReportingAnError() throws Exception {
		// The read blocks until the response is closed, as a socket read does while the
		// model is thinking before its first event.
		BlockingStreamResponse response = new BlockingStreamResponse();

		Disposable subscription = OpenAiStreams.stream(() -> response, Observation.NOOP, this.scheduler)
			.subscribe(item -> {
			}, this.errors::add);
		assertThat(response.reading.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
		subscription.dispose();

		assertThat(response.closed.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
		assertThat(response.failed.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
		await().during(Duration.ofMillis(200)).atMost(TIMEOUT).until(this.errors::isEmpty);
	}

	@Test
	void cancelWhileSendingClosesTheResponseOnceItArrivesWithoutInterrupting() throws Exception {
		CountDownLatch requestSent = new CountDownLatch(1);
		CountDownLatch replyAllowed = new CountDownLatch(1);
		AtomicBoolean interrupted = new AtomicBoolean();
		FakeStreamResponse response = new FakeStreamResponse(List.of("a").iterator());

		Disposable subscription = OpenAiStreams.stream(() -> {
			requestSent.countDown();
			try {
				if (!replyAllowed.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
					throw new IllegalStateException("No reply");
				}
			}
			catch (InterruptedException ex) {
				// OkHttp fails an HTTP/2 request in flight, and its observation, when its
				// thread is interrupted.
				interrupted.set(true);
				Thread.currentThread().interrupt();
				throw new IllegalStateException(ex);
			}
			return response;
		}, Observation.NOOP, this.scheduler).subscribe(item -> {
		}, this.errors::add);
		assertThat(requestSent.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
		subscription.dispose();
		replyAllowed.countDown();

		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(response.isClosed()).isTrue());
		assertThat(interrupted).isFalse();
		assertThat(response.isStreamed()).isFalse();
		assertThat(this.errors).isEmpty();
	}

	private static class FakeStreamResponse implements StreamResponse<String> {

		private final Iterator<String> items;

		private final AtomicBoolean streamed = new AtomicBoolean();

		final CountDownLatch closed = new CountDownLatch(1);

		FakeStreamResponse(Iterator<String> items) {
			this.items = items;
		}

		@Override
		public Stream<String> stream() {
			this.streamed.set(true);
			return StreamSupport.stream(Spliterators.spliteratorUnknownSize(this.items, 0), false);
		}

		@Override
		public void close() {
			this.closed.countDown();
		}

		boolean isClosed() {
			return this.closed.getCount() == 0;
		}

		boolean isStreamed() {
			return this.streamed.get();
		}

	}

	/**
	 * Blocks reading the first item until it is closed, and then fails the read.
	 */
	private static final class BlockingStreamResponse extends FakeStreamResponse {

		final CountDownLatch reading = new CountDownLatch(1);

		final CountDownLatch failed = new CountDownLatch(1);

		BlockingStreamResponse() {
			super(Collections.emptyIterator());
		}

		@Override
		public Stream<String> stream() {
			Iterator<String> iterator = new Iterator<>() {

				@Override
				public boolean hasNext() {
					BlockingStreamResponse.this.reading.countDown();
					awaitClose();
					BlockingStreamResponse.this.failed.countDown();
					throw new UncheckedIOException(new IOException("closed"));
				}

				@Override
				public String next() {
					throw new NoSuchElementException();
				}

			};
			return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 0), false);
		}

		/**
		 * Waits for the close as a socket read does: an interrupt does not end the wait.
		 * The wait is much longer than the test waits, so that only a close ends it in
		 * time.
		 */
		private void awaitClose() {
			long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(1);
			boolean interrupted = false;
			while (this.closed.getCount() > 0 && System.nanoTime() < deadline) {
				try {
					this.closed.await(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
				}
				catch (InterruptedException ex) {
					interrupted = true;
				}
			}
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}

	}

}
