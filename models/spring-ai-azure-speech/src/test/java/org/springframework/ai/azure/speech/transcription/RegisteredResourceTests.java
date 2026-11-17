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

package org.springframework.ai.azure.speech.transcription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RegisteredResource}: the guarantee that one resource is closed at most
 * once, never before the recognizer actions it must outlive, and never by throwing at its
 * caller.
 *
 * @author Spring AI Contributors
 */
class RegisteredResourceTests {

	private final Object lock = new Object();

	private final List<Throwable> settled = new ArrayList<>();

	@Test
	void closesTheTargetExactlyOnceHoweverManyCallersRace() throws Exception {
		AtomicInteger closes = new AtomicInteger();
		RegisteredResource resource = resource(ResourceKind.SOURCE, closes::incrementAndGet);

		int callers = 8;
		CountDownLatch ready = new CountDownLatch(callers);
		CountDownLatch go = new CountDownLatch(1);
		List<Thread> threads = new ArrayList<>();
		for (int i = 0; i < callers; i++) {
			Thread thread = new Thread(() -> {
				ready.countDown();
				awaitQuietly(go);
				resource.closeOnce();
			});
			thread.start();
			threads.add(thread);
		}
		assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
		go.countDown();
		for (Thread thread : threads) {
			thread.join(5000);
		}

		assertThat(closes).hasValue(1);
		assertThat(resource.state()).isEqualTo(CloseState.CLOSED);
	}

	@Test
	void recordsAFailedCloseWithoutThrowingAtTheCaller() {
		RegisteredResource resource = resource(ResourceKind.RECOGNIZER, () -> {
			throw new IllegalStateException("boom");
		});

		resource.closeOnce();
		resource.closeOnce();

		assertThat(resource.state()).isEqualTo(CloseState.FAILED);
		assertThat(this.settled).hasSize(1);
		assertThat(this.settled.get(0)).isInstanceOf(AzureSpeechCleanupException.class)
			.hasMessageContaining("RECOGNIZER close() failed")
			.hasMessageContaining("IllegalStateException");
	}

	@Test
	void theFailureMessageCarriesOnlyTheExceptionType() {
		RegisteredResource resource = resource(ResourceKind.SOURCE, () -> {
			throw new IllegalStateException("s3cret-key leaked in the SDK message");
		});

		resource.closeOnce();

		assertThat(this.settled.get(0)).hasMessageNotContaining("s3cret-key");
	}

	@Test
	void aResourceWithNoReservedActionsClosesStraightAway() {
		AtomicInteger closes = new AtomicInteger();
		RegisteredResource resource = resource(ResourceKind.AUDIO_CONFIG, closes::incrementAndGet);

		resource.closeOnce();

		assertThat(closes).hasValue(1);
	}

	@Test
	void theRecognizerIsNotClosedUntilEveryReservedActionSettles() throws Exception {
		AtomicInteger closes = new AtomicInteger();
		RegisteredResource resource = resource(ResourceKind.RECOGNIZER, closes::incrementAndGet);
		RecognizerAction start = resource.reserveStart(false);
		RecognizerAction stop = resource.reserveStop();
		assertThat(start).isNotNull();
		assertThat(stop).isNotNull();

		Thread closer = new Thread(resource::closeOnce);
		closer.start();

		// The close is claimed but gated: neither action has settled.
		awaitWaiting(closer);
		assertThat(closes).hasValue(0);

		start.settled().complete(null);
		awaitWaiting(closer);
		assertThat(closes).hasValue(0);

		stop.settled().complete(null);
		closer.join(5000);

		assertThat(closes).hasValue(1);
		assertThat(resource.state()).isEqualTo(CloseState.CLOSED);
	}

	@Test
	void anActionReservedAfterTheCloseIsClaimedIsRefused() {
		RegisteredResource resource = resource(ResourceKind.RECOGNIZER, () -> {
		});
		RecognizerAction start = resource.reserveStart(false);
		assertThat(start).isNotNull();
		start.settled().complete(null);

		resource.closeOnce();

		assertThat(resource.reserveStop()).isNull();
		assertThat(resource.reserveStart(false)).isNull();
	}

	@Test
	void reserveStartRefusesOnceTheOperationIsDecided() {
		RegisteredResource resource = resource(ResourceKind.RECOGNIZER, () -> {
		});

		assertThat(resource.reserveStart(true)).isNull();
	}

	@Test
	void reserveStopRefusesWhenNoStartWasEverReserved() {
		RegisteredResource resource = resource(ResourceKind.RECOGNIZER, () -> {
		});

		assertThat(resource.reserveStop()).isNull();
	}

	@Test
	void anUnobservableActionLeavesTheRecognizerDeliberatelyUnclosed() {
		AtomicInteger closes = new AtomicInteger();
		RegisteredResource resource = resource(ResourceKind.RECOGNIZER, closes::incrementAndGet);
		RecognizerAction start = resource.reserveStart(false);
		assertThat(start).isNotNull();
		start.markUnobservable();
		start.settled().complete(null);

		resource.closeOnce();

		assertThat(closes).hasValue(0);
		assertThat(resource.state()).isEqualTo(CloseState.FAILED);
		assertThat(this.settled.get(0)).isInstanceOf(AzureSpeechResourceException.class)
			.hasMessageContaining("unobservable");
	}

	private RegisteredResource resource(ResourceKind kind, ThrowingClose close) {
		return new RegisteredResource(kind, close::close, this.lock, (ignored, failure) -> {
			if (failure != null) {
				this.settled.add(failure);
			}
		});
	}

	/**
	 * Waits until {@code thread} is parked, so a gate assertion never depends on a sleep.
	 */
	private static void awaitWaiting(Thread thread) {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (System.nanoTime() < deadline) {
			Thread.State state = thread.getState();
			if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) {
				return;
			}
			Thread.onSpinWait();
		}
		throw new AssertionError("thread never parked: " + thread.getState());
	}

	private static void awaitQuietly(CountDownLatch latch) {
		try {
			latch.await(5, TimeUnit.SECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface ThrowingClose {

		void close() throws Exception;

	}

}
