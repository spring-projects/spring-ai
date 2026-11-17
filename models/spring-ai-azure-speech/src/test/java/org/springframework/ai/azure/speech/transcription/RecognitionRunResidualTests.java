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
import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import com.microsoft.cognitiveservices.speech.SpeechConfig;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.core.io.ByteArrayResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the two residual signals an operation reports — resources whose close has not
 * concluded, and acquisition work still in flight — and for the narrow windows around
 * reserving the recognizer's start action.
 * <p>
 * Each window is widened deterministically through a named pause point rather than being
 * chased with a sleep.
 *
 * @author Spring AI Contributors
 */
@Timeout(60)
class RecognitionRunResidualTests {

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

	private final PauseController pauses = new PauseController();

	private final AtomicReference<RecognitionRun> run = new AtomicReference<>();

	private final AtomicInteger configCreations = new AtomicInteger();

	private final ExecutorService ownerExecutor = AzureSpeechTranscriptionModel.daemonPool("residual-owner-");

	private final ExecutorService cleanupExecutor = AzureSpeechTranscriptionModel.daemonPool("residual-cleanup-");

	@AfterEach
	void shutDownExecutors() {
		this.ownerExecutor.shutdownNow();
		this.cleanupExecutor.shutdownNow();
	}

	@Test
	void anOutstandingStartHoldsTheWholeCleanupChainAndIsReportedAsSuch() throws Exception {
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(Duration.ofSeconds(10), Duration.ofMillis(5));

		awaitUntil(() -> this.session.startCalls == 1, "the start to be submitted");
		// The service reports the session ended while the start future is outstanding.
		this.session.emitSessionStopped();
		awaitUntil(() -> this.run.get().isDecided(), "the corroboration window to close");

		// The chain begins at the recognizer, whose gate is still held by the start
		// action, so nothing below it has been reached either.
		assertThat(this.run.get().getPendingResourceKinds())
			.isEqualTo(EnumSet.of(ResourceKind.RECOGNIZER, ResourceKind.AUDIO_CONFIG, ResourceKind.PULL_STREAM,
					ResourceKind.STREAM_FORMAT, ResourceKind.SPEECH_CONFIG, ResourceKind.SOURCE));
		assertThat(this.run.get().getInFlightAcquisitions()).isEqualTo(1);
		assertThat(this.run.get().isPublished()).isFalse();
		assertThat(this.session.closeCount(ResourceKind.RECOGNIZER)).isZero();

		this.session.startFuture.complete(null);

		assertThat(call.get(10, TimeUnit.SECONDS).getResult().getOutput()).isEmpty();
		assertThat(this.run.get().getPendingResourceKinds()).isEmpty();
		assertThat(this.run.get().getInFlightAcquisitions()).isZero();
		assertThat(this.run.get().awaitFullyAccounted(5000)).isTrue();
	}

	@Test
	void inFlightWorkIsReportedIndependentlyOfResourceClosure() throws Exception {
		this.pauses.arm(PausePoint.AFTER_START_FUTURE_TERMINAL);
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(Duration.ofSeconds(10), Duration.ofMillis(5));

		awaitUntil(() -> this.session.startCalls == 1, "the start to be submitted");
		this.session.startFuture.complete(null);
		this.pauses.awaitReached(PausePoint.AFTER_START_FUTURE_TERMINAL);

		this.session.emitSegment("hello", 0, 100);
		this.session.emitSessionStopped();
		awaitUntil(() -> this.run.get().isDecided(), "the corroboration window to close");
		awaitUntil(() -> this.run.get().getPendingResourceKinds().isEmpty(), "the cleanup chain to drain");

		// Every resource has settled, yet the owner's tracked step has not returned:
		// the two residual signals are reported independently of one another.
		assertThat(this.run.get().getInFlightAcquisitions()).isEqualTo(1);
		assertThat(this.run.get().isPublished()).isFalse();

		this.pauses.release(PausePoint.AFTER_START_FUTURE_TERMINAL);

		AudioTranscriptionResponse response = call.get(10, TimeUnit.SECONDS);

		assertThat(response.getResult().getOutput()).isEqualTo("hello");
		assertThat(this.run.get().getInFlightAcquisitions()).isZero();
		assertThat(this.run.get().awaitFullyAccounted(5000)).isTrue();
	}

	@Test
	void aGiveUpPublicationReportsTheInFlightResidualAndKeepsTheDecidedOutcome() throws Exception {
		this.pauses.arm(PausePoint.AFTER_START_FUTURE_TERMINAL);
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(Duration.ofMillis(400), Duration.ofMillis(5));

		awaitUntil(() -> this.session.startCalls == 1, "the start to be submitted");
		this.session.startFuture.complete(null);
		this.pauses.awaitReached(PausePoint.AFTER_START_FUTURE_TERMINAL);

		this.session.emitSegment("decided", 0, 100);
		this.session.emitSessionStopped();
		awaitUntil(() -> this.run.get().getPendingResourceKinds().isEmpty(), "the cleanup chain to drain");

		// The deadline, not the owner, ends the wait; the already-decided outcome is
		// published unchanged rather than becoming a timeout.
		AudioTranscriptionResponse response = call.get(10, TimeUnit.SECONDS);

		assertThat(response.getResult().getOutput()).isEqualTo("decided");
		assertThat(this.run.get().getInFlightAcquisitions()).isEqualTo(1);
		assertThat(this.run.get().getPendingResourceKinds()).isEmpty();

		this.pauses.release(PausePoint.AFTER_START_FUTURE_TERMINAL);
		awaitUntil(() -> this.run.get().getInFlightAcquisitions() == 0, "the tracked step to return");
		assertThat(this.run.get().awaitFullyAccounted(5000)).isTrue();
	}

	@Test
	void aDeadlineBeforeTheStartIsReservedStartsAndStopsNothing() throws Exception {
		this.pauses.arm(PausePoint.BEFORE_RESERVE_START);
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(Duration.ofMillis(300), Duration.ofMillis(5));

		this.pauses.awaitReached(PausePoint.BEFORE_RESERVE_START);
		AzureSpeechTranscriptionException failure = failureOf(call);
		this.pauses.release(PausePoint.BEFORE_RESERVE_START);

		assertThat(failure.getMessage()).contains("timed out after");
		awaitUntil(() -> this.session.closeCount(ResourceKind.RECOGNIZER) == 1, "the recognizer to be closed");
		assertThat(this.session.startCalls).isZero();
		assertThat(this.session.stopCalls).isZero();
		assertThat(this.session.calls).doesNotContain("start", "stop");
	}

	@Test
	void aDeadlineAfterTheStartIsReservedStillOrdersStartThenStopThenClose() throws Exception {
		this.pauses.arm(PausePoint.AFTER_RESERVE_START);
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(Duration.ofSeconds(1), Duration.ofMillis(5));

		this.pauses.awaitReached(PausePoint.AFTER_RESERVE_START);
		// The start is admitted but has not reached the SDK yet.
		assertThat(this.session.calls).doesNotContain("start", "stop");

		AzureSpeechTranscriptionException failure = failureOf(call);
		assertThat(failure.getMessage()).contains("timed out after");

		this.pauses.release(PausePoint.AFTER_RESERVE_START);
		awaitUntil(() -> this.session.startCalls == 1, "the start to reach the SDK");
		this.session.startFuture.complete(null);
		awaitUntil(() -> this.session.stopCalls == 1, "the deferred stop to reach the SDK");
		this.session.stopFuture.complete(null);
		awaitUntil(() -> this.session.closeCount(ResourceKind.RECOGNIZER) == 1, "the recognizer to be closed");

		assertThat(this.session.calls).containsSubsequence("start", "stop", "close:RECOGNIZER");
		assertThat(this.session.recognitionRunningAtClose).isFalse();
	}

	@Test
	void aBlockingSdkStopAtTheDeadlineNeverHoldsTheCaller() throws Exception {
		// The deadline decides on the caller's thread. If the stop it reserves were
		// submitted inline, a blocking SDK stop would keep call() past its timeout.
		CountDownLatch heldInsideStop = this.session.blockStopSubmission();
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(Duration.ofMillis(50), Duration.ofMillis(5));

		awaitUntil(() -> this.session.startCalls == 1, "the start to reach the SDK");
		this.session.startFuture.complete(null);

		// The caller returns on time even though a thread is still parked in the SDK.
		AzureSpeechTranscriptionException failure = failureOf(call);
		assertThat(failure.getMessage()).contains("timed out after");

		awaitUntil(() -> this.session.stopCalls == 1, "the stop to reach the SDK");
		Thread held = this.session.stopCallThread;
		assertThat(held).isNotNull();
		assertThat(held.getName()).startsWith("residual-cleanup-");

		heldInsideStop.countDown();
		this.session.stopFuture.complete(null);
		awaitUntil(() -> this.session.closeCount(ResourceKind.RECOGNIZER) == 1, "the recognizer to be closed");
	}

	@Test
	void theCallerIsReleasedWithinItsTimeoutWhileAnSdkStopIsStillBlocked() {
		// The test above proves the blocked stop runs somewhere other than the caller's
		// thread. It does not measure what the timeout criterion actually bounds: how
		// long call() held its caller. A regression that waited for the parked stop
		// would hold the caller until the gate below is released -- which happens after
		// this assertion, so it could not pass.
		Duration timeout = Duration.ofMillis(400);
		CountDownLatch heldInsideStop = this.session.blockStopSubmission();
		AtomicLong callerNanos = new AtomicLong();
		CompletableFuture<AudioTranscriptionResponse> call = callAsyncTimed(timeout, Duration.ofMillis(5), callerNanos);

		awaitUntil(() -> this.session.startCalls == 1, "the start to reach the SDK");
		this.session.startFuture.complete(null);

		assertThat(failureOf(call).getMessage()).contains("timed out after");

		Duration held = Duration.ofNanos(callerNanos.get());
		// Not early: the deadline, and nothing else, is what released the caller.
		assertThat(held).as("time held inside call()").isGreaterThanOrEqualTo(timeout);
		// Not held behind the parked SDK stop. The criterion bounds the caller at the
		// timeout itself; the slack here is test tolerance only. Five local runs
		// overshot the deadline by 56-104 ms, so a second is roughly ten times the
		// observed worst case -- wide enough that scheduling noise on a loaded machine
		// cannot fail this, while a wait on the parked stop, which is released only
		// after this assertion, is unbounded and so cannot pass any finite bound.
		assertThat(held).as("time held inside call()").isLessThan(timeout.plusSeconds(1));

		heldInsideStop.countDown();
		this.session.stopFuture.complete(null);
		awaitUntil(() -> this.session.closeCount(ResourceKind.RECOGNIZER) == 1, "the recognizer to be closed");
	}

	@Test
	void anAsynchronousStartFailureIsReducedToItsTypeAndReleasesEverything() {
		CompletableFuture<AudioTranscriptionResponse> call = callAsync(Duration.ofSeconds(5), Duration.ofMillis(5));

		awaitUntil(() -> this.session.startCalls == 1, "the start to reach the SDK");
		this.session.startFuture.completeExceptionally(
				new IllegalStateException("start failed for key test-key at /etc/azure/secret.json"));

		AzureSpeechTranscriptionException failure = failureOf(call);

		assertThat(failure.getMessage()).contains("could not be started").contains("java.lang.IllegalStateException");
		assertThat(failure.getMessage()).doesNotContain("test-key").doesNotContain("/etc/azure/secret.json");
		assertThat(failure.getCause()).isNull();
		// A start that never ran needs no stop, but everything acquired is released.
		awaitUntil(() -> this.session.closeCount(ResourceKind.RECOGNIZER) == 1, "the recognizer to be closed");
		assertThat(this.session.stopCalls).isZero();
	}

	// ------------------------------------------------------------------ helpers

	private AzureSpeechTranscriptionModel modelWith(Duration timeout, Duration corroborationWindow) {
		return AzureSpeechTranscriptionModel.builder().speechConfigFactory(() -> {
			this.configCreations.incrementAndGet();
			return SpeechConfig.fromSubscription("test-key", "westus");
		})
			.defaultOptions(AzureSpeechAudioTranscriptionOptions.builder().timeout(timeout).build())
			.observationRegistry(ObservationRegistry.NOOP)
			.sessionFactory(this.session)
			.ownerExecutor(this.ownerExecutor)
			.cleanupExecutor(this.cleanupExecutor)
			.corroborationWindow(corroborationWindow)
			.pauses(this.pauses)
			.runObserver(this.run::set)
			.build();
	}

	private CompletableFuture<AudioTranscriptionResponse> callAsync(Duration timeout, Duration corroborationWindow) {
		AzureSpeechTranscriptionModel model = modelWith(timeout, corroborationWindow);
		return CompletableFuture
			.supplyAsync(() -> model.call(new AudioTranscriptionPrompt(new ByteArrayResource(WAV))));
	}

	/**
	 * Runs one call on its own thread and records how long that thread was held inside
	 * {@code call()} itself, so that the executor's scheduling delay is never counted
	 * against the deadline the criterion bounds.
	 */
	private CompletableFuture<AudioTranscriptionResponse> callAsyncTimed(Duration timeout, Duration corroborationWindow,
			AtomicLong callerNanos) {
		AzureSpeechTranscriptionModel model = modelWith(timeout, corroborationWindow);
		return CompletableFuture.supplyAsync(() -> {
			long entry = System.nanoTime();
			try {
				return model.call(new AudioTranscriptionPrompt(new ByteArrayResource(WAV)));
			}
			finally {
				callerNanos.set(System.nanoTime() - entry);
			}
		});
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

	private void awaitUntil(BooleanSupplier condition, String what) {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (System.nanoTime() < deadline) {
			if (condition.getAsBoolean()) {
				return;
			}
			Thread.onSpinWait();
		}
		throw new AssertionError("timed out waiting for " + what);
	}

}
