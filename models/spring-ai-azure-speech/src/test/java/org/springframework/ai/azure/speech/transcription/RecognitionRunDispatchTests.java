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

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import com.microsoft.cognitiveservices.speech.SpeechConfig;
import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.core.io.ByteArrayResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for what an operation does when the executor it hands lifecycle work to refuses
 * it: the completion waiter for a submitted recognizer action, the fallback waiter
 * thread, and the dispatch of a deferred stop.
 * <p>
 * Rejection is armed for exactly one submission at a time, because the same executor also
 * drives the ordered cleanup chain: a blanket rejection would prove nothing about the
 * path under test.
 *
 * @author Spring AI Contributors
 */
@Timeout(60)
class RecognitionRunDispatchTests {

	private static final byte[] WAV = TestWav.pcm(16000, 1, 16, 320);

	/**
	 * Loads the SDK's native library once, so that a deadline measured in a test is never
	 * racing a one-off class-initialization cost.
	 */
	@BeforeAll
	static void warmUpSpeechConfigNatives() {
		SpeechConfig.fromSubscription("warm-up", "westus").close();
	}

	private final FakeSpeechSession session = new FakeSpeechSession();

	private final AtomicReference<RecognitionRun> run = new AtomicReference<>();

	private final ExecutorService ownerExecutor = AzureSpeechTranscriptionModel.daemonPool("dispatch-owner-");

	private final ExecutorService cleanupDelegate = AzureSpeechTranscriptionModel.daemonPool("dispatch-cleanup-");

	private final OneShotRejectingExecutor cleanupExecutor = new OneShotRejectingExecutor(this.cleanupDelegate);

	private final RecordingThreadFactory waiterThreads = new RecordingThreadFactory();

	@AfterEach
	void shutDownExecutors() {
		this.ownerExecutor.shutdownNow();
		this.cleanupDelegate.shutdownNow();
	}

	@Test
	void aRejectedWaiterDispatchFallsBackToADedicatedWaiterThread() throws Exception {
		// The start waiter is the first task this operation hands to the cleanup
		// executor, so arming the one-shot rejection up front targets exactly it.
		this.cleanupExecutor.rejectNextTask();
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(Duration.ofSeconds(5));

		awaitUntil(() -> this.waiterThreads.created.get() == 1, "the fallback waiter thread to be created");
		this.session.startFuture.complete(null);
		this.session.emitSegment("still observed", 0, 100);
		this.session.emitSessionStopped();

		assertThat(call.get(10, TimeUnit.SECONDS).getResult().getOutput()).isEqualTo("still observed");
		assertThat(this.cleanupExecutor.rejections).hasValue(1);
		assertThat(this.session.closeCount(ResourceKind.RECOGNIZER)).isEqualTo(1);
		assertThat(this.run.get().getPendingResourceKinds()).isEmpty();
	}

	@Test
	void aWaiterThatCannotBeDispatchedAtAllLeavesTheRecognizerReportedRatherThanClosed() throws Exception {
		this.waiterThreads.fail = true;
		this.cleanupExecutor.rejectNextTask();
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(Duration.ofSeconds(5));

		awaitUntil(() -> this.session.startCalls == 1, "the start to reach the SDK");
		this.session.startFuture.complete(null);
		this.session.emitSegment("delivered", 0, 100);
		this.session.emitSessionStopped();

		// Nothing can observe the start finishing, so closing the recognizer could
		// dispose it with work still running on it: it is reported instead, and the
		// resources below it are still released.
		assertThat(call.get(10, TimeUnit.SECONDS).getResult().getOutput()).isEqualTo("delivered");
		assertThat(this.session.closeCount(ResourceKind.RECOGNIZER)).isZero();
		assertThat(this.session.closeCount(ResourceKind.AUDIO_CONFIG)).isEqualTo(1);
		assertThat(this.session.closeCount(ResourceKind.PULL_STREAM)).isEqualTo(1);
		// A close that was attempted and concluded, even as a failure, is not pending.
		assertThat(this.run.get().getPendingResourceKinds()).isEmpty();
	}

	@Test
	void anInterruptedWaiterKeepsWaitingForTheSdkAndRestoresTheFlag() throws Exception {
		this.cleanupExecutor.rejectNextTask();
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(Duration.ofSeconds(10));

		awaitUntil(() -> this.waiterThreads.created.get() == 1, "the fallback waiter thread to be created");
		Thread waiter = this.waiterThreads.lastThread;
		assertThat(waiter).isNotNull();
		this.session.emitSessionStopped();
		awaitUntil(() -> this.run.get().isDecided(), "the corroboration window to close");

		waiter.interrupt();

		// The interrupt is not the SDK action concluding: the recognizer's gate stays
		// shut while the start future is outstanding.
		assertThat(this.session.closeCount(ResourceKind.RECOGNIZER)).isZero();
		assertThat(this.run.get().getPendingResourceKinds()).contains(ResourceKind.RECOGNIZER);

		this.session.startFuture.complete(null);

		assertThat(call.get(10, TimeUnit.SECONDS).getResult().getOutput()).isEmpty();
		assertThat(this.session.closeCount(ResourceKind.RECOGNIZER)).isEqualTo(1);
		awaitUntil(() -> this.waiterThreads.interruptedAfterRun.get() != null, "the waiter thread to finish");
		assertThat(this.waiterThreads.interruptedAfterRun.get()).as("the waiter restores the interrupt it absorbed")
			.isTrue();
	}

	@Test
	void aRejectedStopDispatchClosesTheRecognizerWithoutAnExplicitStop() {
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(Duration.ofSeconds(1));

		awaitUntil(() -> this.session.startCalls == 1, "the start to reach the SDK");
		this.session.startFuture.complete(null);
		awaitUntil(this::startActionSettled, "the start action to settle");
		// With the start action settled, the deadline dispatches the stop it reserves
		// before it schedules the cleanup chain, so the armed rejection targets exactly
		// that dispatch.
		this.cleanupExecutor.rejectNextTask();

		AzureSpeechTranscriptionException failure = failureOf(call);

		assertThat(failure.getMessage()).contains("timed out after");
		assertThat(this.cleanupExecutor.rejections).hasValue(1);
		assertThat(this.session.stopCalls).isZero();
		awaitUntil(() -> this.session.closeCount(ResourceKind.RECOGNIZER) == 1,
				"the recognizer to be closed without a stop");
		assertThat(this.session.calls).doesNotContain("stop");
	}

	// ------------------------------------------------------------------ helpers

	private CompletableFuture<AudioTranscriptionResponse> callAsync(Duration timeout) {
		AzureSpeechTranscriptionModel model = AzureSpeechTranscriptionModel.builder()
			.speechConfigFactory(() -> SpeechConfig.fromSubscription("test-key", "westus"))
			.defaultOptions(AzureSpeechAudioTranscriptionOptions.builder().timeout(timeout).build())
			.observationRegistry(ObservationRegistry.NOOP)
			.sessionFactory(this.session)
			.ownerExecutor(this.ownerExecutor)
			.cleanupExecutor(this.cleanupExecutor)
			.waiterThreadFactory(this.waiterThreads)
			.corroborationWindow(Duration.ofMillis(20))
			.runObserver(this.run::set)
			.build();
		return CompletableFuture
			.supplyAsync(() -> model.call(new AudioTranscriptionPrompt(new ByteArrayResource(WAV))));
	}

	private static AzureSpeechTranscriptionException failureOf(CompletableFuture<AudioTranscriptionResponse> call) {
		try {
			call.get(10, TimeUnit.SECONDS);
		}
		catch (ExecutionException ex) {
			Throwable cause = ex.getCause();
			if (cause instanceof AzureSpeechTranscriptionException failure) {
				return failure;
			}
			throw new AssertionError("expected an AzureSpeechTranscriptionException but got " + cause, cause);
		}
		catch (InterruptedException | TimeoutException ex) {
			throw new AssertionError(ex);
		}
		throw new AssertionError("expected the call to fail");
	}

	/**
	 * Reports whether the recognizer's start action has reached its terminal state.
	 * <p>
	 * A reserved stop is always submitted through the cleanup executor, never inline on
	 * the caller. What this state decides is <em>when</em> that submission is handed to
	 * the executor: once the start has settled, the next reservation reaches the executor
	 * on the thread that reserves it rather than later from whichever thread completes
	 * the start. Arming a one-shot rejection only after this is true is what makes the
	 * deadline's stop the submission the rejection lands on.
	 */
	private boolean startActionSettled() {
		RegisteredResource resource = this.run.get().recognizerResource();
		RecognizerAction startAction = (resource != null) ? resource.startAction() : null;
		return startAction != null && startAction.settled().isDone();
	}

	private static void awaitUntil(BooleanSupplier condition, String what) {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (System.nanoTime() < deadline) {
			if (condition.getAsBoolean()) {
				return;
			}
			Thread.onSpinWait();
		}
		throw new AssertionError("timed out waiting for " + what);
	}

	/**
	 * Refuses exactly one submission per arming, and otherwise behaves like the model's
	 * own cleanup pool, including its abort policy.
	 */
	private static final class OneShotRejectingExecutor implements Executor {

		private final Executor delegate;

		private final AtomicBoolean armed = new AtomicBoolean();

		private final AtomicInteger rejections = new AtomicInteger();

		OneShotRejectingExecutor(Executor delegate) {
			this.delegate = delegate;
		}

		void rejectNextTask() {
			this.armed.set(true);
		}

		@Override
		public void execute(Runnable command) {
			if (this.armed.compareAndSet(true, false)) {
				this.rejections.incrementAndGet();
				throw new RejectedExecutionException("the cleanup executor refused this task");
			}
			this.delegate.execute(command);
		}

	}

	/**
	 * Stands in for the model's waiter thread factory, so a test can find the fallback
	 * waiter, make the factory itself fail, and see the interrupt state the waiter leaves
	 * behind.
	 */
	private static final class RecordingThreadFactory implements ThreadFactory {

		private final AtomicInteger created = new AtomicInteger();

		private final AtomicReference<@Nullable Boolean> interruptedAfterRun = new AtomicReference<>();

		private volatile boolean fail;

		private volatile @Nullable Thread lastThread;

		@Override
		public Thread newThread(Runnable runnable) {
			if (this.fail) {
				throw new IllegalStateException("no waiter thread is available");
			}
			Thread thread = new Thread(() -> {
				runnable.run();
				this.interruptedAfterRun.set(Thread.currentThread().isInterrupted());
			}, "dispatch-waiter-" + (this.created.get() + 1));
			thread.setDaemon(true);
			this.lastThread = thread;
			// Counted last, so observing the count implies the thread is already visible.
			this.created.incrementAndGet();
			return thread;
		}

	}

}
