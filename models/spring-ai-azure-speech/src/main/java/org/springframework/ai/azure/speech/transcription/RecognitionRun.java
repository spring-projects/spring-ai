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
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The state of one transcription operation: what has been recognized, what has been
 * acquired, when the outcome is settled, and when the caller may see it.
 * <p>
 * One lock guards every field below, so that the decision, the resource registry, the
 * in-flight acquisition count, and the recognizer's action reservations all move
 * together. Callers are an owner thread running the acquisition sequence, SDK callback
 * threads delivering events, cleanup threads releasing resources, and the caller thread
 * waiting for publication.
 * <p>
 * Two rules do most of the work:
 * <ul>
 * <li><b>Deciding.</b> An error-bearing event decides the outcome immediately. A
 * non-error terminal event only makes it provisional, for a corroboration window bounded
 * by {@link #DEFAULT_CORROBORATION_WINDOW} and clipped to the remaining deadline, during
 * which a genuine error can still upgrade it. The deadline, a caller interrupt, or a
 * cancellation either accepts a provisional outcome early or, with nothing decided yet,
 * decides the operation itself.</li>
 * <li><b>Publishing.</b> Ordinary publication waits for every resource to settle and
 * every acquisition to conclude, so an ordinary operation has finished accounting for
 * everything it owns before its outcome is seen. Give-up publication, triggered by the
 * deadline, an interrupt, or a streaming subscriber's cancellation, publishes the
 * already-decided outcome at once and lets cleanup finish behind it, reporting exactly
 * what is still outstanding.</li>
 * </ul>
 *
 * @author Spring AI Contributors
 */
final class RecognitionRun {

	/**
	 * How long a non-error terminal event waits for a contradicting error before its
	 * outcome is committed. No primary source states a typical inter-event latency, so
	 * this is a deliberately minimized engineering choice rather than a measured bound:
	 * an error delivered after the window still loses, which is the disclosed and tested
	 * completeness residual.
	 */
	static final long DEFAULT_CORROBORATION_WINDOW_MILLIS = 250L;

	private static final Logger logger = LoggerFactory.getLogger(RecognitionRun.class);

	private final Object lock = new Object();

	private final Executor cleanupExecutor;

	private final ThreadFactory waiterThreadFactory;

	private final LongSupplier nanoTime;

	private final @Nullable PauseController pauses;

	private final @Nullable Consumer<AzureSpeechSegment> segmentListener;

	private final long timeoutNanos;

	private final long corroborationWindowNanos;

	private final long deadlineNanos;

	private final List<RegisteredResource> registry = new ArrayList<>();

	private final List<AzureSpeechSegment> segments = new ArrayList<>();

	private final List<Throwable> cleanupFailures = new ArrayList<>();

	private int noMatchCount;

	private int lateEvents;

	private int inFlightAcquisitions;

	private boolean decided;

	private boolean published;

	private boolean cleanupScheduled;

	private boolean stopRequested;

	private boolean canceledBySubscriber;

	private boolean modelRequestedStop;

	private boolean provisionalArmed;

	private long provisionalExpiryNanos;

	private @Nullable AzureSpeechTranscriptionException firstRecordedError;

	private @Nullable Outcome outcome;

	private @Nullable RegisteredResource recognizerResource;

	private @Nullable RecognizerHandle recognizer;

	RecognitionRun(Executor cleanupExecutor, ThreadFactory waiterThreadFactory, LongSupplier nanoTime,
			long timeoutNanos, long corroborationWindowNanos, @Nullable PauseController pauses,
			@Nullable Consumer<AzureSpeechSegment> segmentListener) {
		this.cleanupExecutor = cleanupExecutor;
		this.waiterThreadFactory = waiterThreadFactory;
		this.nanoTime = nanoTime;
		this.timeoutNanos = timeoutNanos;
		this.corroborationWindowNanos = corroborationWindowNanos;
		this.pauses = pauses;
		this.segmentListener = segmentListener;
		this.deadlineNanos = nanoTime.getAsLong() + timeoutNanos;
	}

	// ---------------------------------------------------------------- acquisition

	/**
	 * Runs one acquisition step and registers whatever it produced.
	 * <p>
	 * Admission and the in-flight reservation happen in one critical section, so the
	 * decision cannot flip between checking it and starting the call. If the decision
	 * does flip while the call is in flight, the resource it eventually returns is
	 * registered anyway — visible to {@link #getPendingResourceKinds()} from that instant
	 * — and closed immediately, rather than being leaked.
	 * @return the acquired resource, or {@code null} when the caller must stop its
	 * acquisition sequence
	 */
	<T extends AutoCloseable> @Nullable T registerOrClose(ResourceKind kind, AcquisitionStep<T> blockingAcquire)
			throws Exception {
		synchronized (this.lock) {
			if (this.decided) {
				return null;
			}
			this.inFlightAcquisitions++;
		}
		try {
			T resource = blockingAcquire.get();
			RegisteredResource registered = new RegisteredResource(kind, resource, this.lock, this::onResourceSettled);
			boolean late;
			synchronized (this.lock) {
				this.registry.add(registered);
				late = this.decided;
				if (!late && kind == ResourceKind.RECOGNIZER) {
					this.recognizerResource = registered;
					this.recognizer = (RecognizerHandle) resource;
				}
				this.lock.notifyAll();
			}
			if (late) {
				closeLate(registered);
				return null;
			}
			return resource;
		}
		finally {
			synchronized (this.lock) {
				this.inFlightAcquisitions--;
				this.lock.notifyAll();
			}
		}
	}

	private void closeLate(RegisteredResource registered) {
		try {
			this.cleanupExecutor.execute(registered::closeOnce);
		}
		catch (RejectedExecutionException ex) {
			// The operation is already decided and publication never waits on this
			// thread, so releasing it here is safe and strictly better than leaking it.
			logger.warn("Azure Speech cleanup executor rejected a late {} close ({}); closing it inline",
					registered.kind(), ex.getClass().getName());
			registered.closeOnce();
		}
	}

	/**
	 * Runs a blocking call on a resource that is already registered, such as the WAV
	 * header read or the initial start wait. Accounts for the call itself without
	 * touching the registry.
	 * @throws AzureSpeechAbortedException if the operation was decided before the step
	 * began
	 */
	void trackBlockingStep(ThrowingRunnable step) throws Exception {
		synchronized (this.lock) {
			if (this.decided) {
				throw new AzureSpeechAbortedException("Azure Speech operation was decided before this step");
			}
			this.inFlightAcquisitions++;
		}
		try {
			step.run();
		}
		finally {
			synchronized (this.lock) {
				this.inFlightAcquisitions--;
				this.lock.notifyAll();
			}
		}
	}

	void pauseIfRequested(PausePoint point) {
		if (this.pauses != null) {
			this.pauses.pauseIfRequested(point);
		}
	}

	// ---------------------------------------------------------------- recognizer actions

	/**
	 * Reserves the one start action for the registered recognizer.
	 * @return the action to submit next, or {@code null} when recognition must not start
	 */
	@Nullable RecognizerAction reserveStart() {
		synchronized (this.lock) {
			RegisteredResource resource = this.recognizerResource;
			if (resource == null) {
				return null;
			}
			return resource.reserveStart(this.decided);
		}
	}

	/**
	 * Hands an action to the SDK and guarantees that some thread will observe it
	 * finishing.
	 * @return the submitted future, which the caller may also wait on
	 * @throws AzureSpeechActionSubmissionException if the SDK rejected the submission, in
	 * which case nothing is running and the action is already settled
	 */
	Future<Void> submitAndWire(Supplier<Future<Void>> sdkCall, RecognizerAction action) {
		Future<Void> sdkFuture;
		try {
			sdkFuture = sdkCall.get();
			action.sdkFuture(sdkFuture);
			action.markSubmitted();
		}
		catch (RuntimeException ex) {
			action.settled().complete(null);
			throw new AzureSpeechActionSubmissionException("Azure Speech recognizer " + action.name()
					+ " could not be submitted (" + ex.getClass().getName() + ")");
		}
		dispatchWaiter(action);
		return sdkFuture;
	}

	/**
	 * Finds a thread to observe {@code action} reaching a terminal state. Never settles
	 * the action itself, except in the degenerate case where no thread can be found at
	 * all, which also marks the action unobservable so the recognizer is reported rather
	 * than closed.
	 */
	private void dispatchWaiter(RecognizerAction action) {
		Runnable waiter = () -> awaitTerminal(action);
		try {
			this.cleanupExecutor.execute(waiter);
			return;
		}
		catch (RejectedExecutionException ex) {
			logger.warn("Azure Speech cleanup executor rejected the {} waiter ({}); using a dedicated waiter thread",
					action.name(), ex.getClass().getName());
		}
		try {
			this.waiterThreadFactory.newThread(waiter).start();
		}
		catch (RuntimeException ex) {
			action.markUnobservable();
			logger.warn("Azure Speech recognizer {} completion cannot be observed ({}); "
					+ "the recognizer will not be closed", action.name(), ex.getClass().getName());
			action.settled().complete(null);
		}
	}

	/**
	 * Waits for an action's SDK future without ever mistaking an interrupt of this thread
	 * for the action concluding.
	 */
	private static void awaitTerminal(RecognizerAction action) {
		Future<Void> sdkFuture = action.sdkFuture();
		if (sdkFuture == null) {
			action.settled().complete(null);
			return;
		}
		boolean interrupted = false;
		try {
			while (true) {
				try {
					sdkFuture.get();
					action.settled().complete(null);
					return;
				}
				catch (ExecutionException ex) {
					Throwable cause = ex.getCause();
					logger.warn("Azure Speech recognizer {} failed ({})", action.name(),
							(cause != null) ? cause.getClass().getName() : ex.getClass().getName());
					action.settled().complete(null);
					return;
				}
				catch (CancellationException ex) {
					logger.warn("Azure Speech recognizer {} was cancelled", action.name());
					action.settled().complete(null);
					return;
				}
				catch (InterruptedException ex) {
					// This thread was interrupted, not the SDK action: keep waiting, or
					// the gate would open with work still running on the recognizer.
					interrupted = true;
				}
			}
		}
		finally {
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}

	/**
	 * Submits a reserved stop, but never before the start action has settled, so the stop
	 * always reaches the SDK after the start future is terminal.
	 * <p>
	 * Submission is always handed to {@code dispatcher} rather than run inline. The
	 * thread that completes the start action is either an SDK callback thread, which must
	 * not re-enter the SDK, or the caller's thread, whose deadline has already passed; an
	 * SDK stop submission may block on neither.
	 * @param dispatcher the executor to hand submission to
	 */
	void submitStopAfterStart(RecognizerAction stopAction, Executor dispatcher) {
		RegisteredResource resource;
		RecognizerHandle handle;
		synchronized (this.lock) {
			resource = this.recognizerResource;
			handle = this.recognizer;
		}
		RecognizerAction startAction = (resource != null) ? resource.startAction() : null;
		if (startAction == null || handle == null) {
			stopAction.settled().complete(null);
			return;
		}
		Runnable submit = () -> {
			stopAction.markDispatchRun();
			if (!startAction.isSubmitted() || startAction.isUnobservable()) {
				// The start never reached the SDK, or its completion cannot be
				// observed: there is nothing running to stop.
				stopAction.settled().complete(null);
				return;
			}
			try {
				submitAndWire(handle::stopContinuousRecognition, stopAction);
			}
			catch (AzureSpeechActionSubmissionException ex) {
				// submitAndWire already settled the action. An outcome is decided by
				// now, so this must never become the caller's failure.
				logger.warn(
						"Azure Speech recognizer stop could not be submitted; " + "closing without an explicit stop",
						ex);
			}
		};
		startAction.settled().whenComplete((ignored, error) -> {
			try {
				dispatcher.execute(submit);
			}
			catch (RuntimeException ex) {
				if (stopAction.hasDispatchRun()) {
					logger.warn("Azure Speech stop dispatch failed after the submission already ran ({})",
							ex.getClass().getName());
					return;
				}
				stopAction.settled().complete(null);
				logger.warn("Azure Speech cleanup executor rejected the stop dispatch ({}); "
						+ "the recognizer will be closed without an explicit stop", ex.getClass().getName());
			}
		});
	}

	// ---------------------------------------------------------------- events

	/**
	 * Records one final segment and, for a streaming operation, hands it to the
	 * subscriber's sink.
	 * <p>
	 * The hand-off happens inside the critical section deliberately: publication takes
	 * the same lock, so a segment accepted before the outcome was decided always reaches
	 * the sink before the terminal signal that follows it, and a segment arriving after
	 * the decision is counted as late and never handed over at all. Nothing downstream
	 * runs here, because the stream hops to its own worker before an element is
	 * delivered.
	 */
	void onRecognized(AzureSpeechSegment segment) {
		synchronized (this.lock) {
			if (this.decided) {
				this.lateEvents++;
				return;
			}
			this.segments.add(segment);
			notifySegmentListenerLocked(segment);
			this.lock.notifyAll();
		}
	}

	/**
	 * Hands a segment to the streaming sink, which must never be able to derail the
	 * operation: the sink belongs to Reactor, and what it throws says nothing about
	 * whether the recognition itself succeeded. The segment is already in the snapshot
	 * either way, so such a failure costs the stream one element and nothing else.
	 */
	private void notifySegmentListenerLocked(AzureSpeechSegment segment) {
		if (this.segmentListener == null) {
			return;
		}
		try {
			this.segmentListener.accept(segment);
		}
		catch (RuntimeException ex) {
			logger.warn("Azure Speech could not hand a recognized segment to the subscriber ({})",
					ex.getClass().getName());
		}
	}

	void onNoMatch() {
		synchronized (this.lock) {
			if (this.decided) {
				this.lateEvents++;
				return;
			}
			this.noMatchCount++;
		}
	}

	void onCanceled(CancellationEvent event) {
		synchronized (this.lock) {
			if (this.decided) {
				this.lateEvents++;
				logger.debug("Azure Speech cancellation arrived after the outcome was decided: reason={}, code={}",
						event.reason(), event.errorCode());
				return;
			}
			boolean isError = event.cause() == CancellationCause.ERROR
					|| (event.cause() == CancellationCause.CANCELLED_BY_USER && !this.modelRequestedStop);
			if (!isError) {
				armProvisionalLocked();
				return;
			}
			recordErrorLocked(AzureSpeechTranscriptionException.canceled(event.reason(), event.errorCode()));
			decideLocked();
		}
		// The service told us it stopped, so no stop request is issued.
		scheduleCleanup();
	}

	void onSessionStopped() {
		synchronized (this.lock) {
			if (this.decided) {
				this.lateEvents++;
				return;
			}
			armProvisionalLocked();
		}
	}

	/**
	 * Reports that the model's own pull callback could not serve the PCM payload. The
	 * failure is self-determined, so it is decisive at once; the stop it needs is
	 * reserved here, on the SDK's reader thread, but submitted elsewhere, because nothing
	 * establishes that re-entering the SDK from its own callback is safe.
	 */
	void onPullFailure(AzureSpeechTranscriptionException error) {
		RecognizerAction stopAction = null;
		synchronized (this.lock) {
			if (this.decided) {
				return;
			}
			recordErrorLocked(error);
			stopAction = reserveStopLocked();
			decideLocked();
		}
		if (stopAction != null) {
			submitStopAfterStart(stopAction, this.cleanupExecutor);
		}
		scheduleCleanup();
	}

	// ---------------------------------------------------------------- deciding

	private void armProvisionalLocked() {
		if (this.provisionalArmed) {
			return;
		}
		this.provisionalArmed = true;
		long now = this.nanoTime.getAsLong();
		long naturalExpiry = now + this.corroborationWindowNanos;
		this.provisionalExpiryNanos = (naturalExpiry - this.deadlineNanos > 0) ? this.deadlineNanos : naturalExpiry;
		this.lock.notifyAll();
	}

	private void recordErrorLocked(AzureSpeechTranscriptionException error) {
		if (this.firstRecordedError == null) {
			this.firstRecordedError = error;
		}
	}

	/**
	 * Commits the outcome from whatever has been observed so far.
	 */
	private void decideLocked() {
		this.decided = true;
		this.provisionalArmed = false;
		AzureSpeechTranscriptionException error = this.firstRecordedError;
		this.outcome = (error != null) ? Outcome.failure(error)
				: Outcome.success(new ArrayList<>(this.segments), this.noMatchCount);
		this.lock.notifyAll();
	}

	private @Nullable RecognizerAction reserveStopLocked() {
		RegisteredResource resource = this.recognizerResource;
		if (resource == null || this.stopRequested) {
			return null;
		}
		RecognizerAction action = resource.reserveStopLocked();
		if (action != null) {
			this.stopRequested = true;
			this.modelRequestedStop = true;
		}
		return action;
	}

	/**
	 * Records a failure the owner thread hit while setting the operation up, such as an
	 * unreadable header or a rejected start submission.
	 */
	void onOwnerFailure(RuntimeException failure) {
		synchronized (this.lock) {
			if (this.decided) {
				return;
			}
			this.decided = true;
			this.provisionalArmed = false;
			AzureSpeechTranscriptionException recorded = this.firstRecordedError;
			this.outcome = Outcome.failure((recorded != null) ? recorded : failure);
			this.lock.notifyAll();
		}
		scheduleCleanup();
	}

	/**
	 * Reports that a streaming subscriber cancelled, which is a give-up trigger exactly
	 * like the deadline: whatever is already decided is published at once, and with
	 * nothing decided the operation itself is decided here.
	 * <p>
	 * The decision is left to the waiting publisher thread rather than taken here,
	 * because cancellation runs on whichever thread cancelled the subscription — it must
	 * not be asked to reserve a stop, submit one, or wait for anything.
	 */
	void onSubscriberCancel() {
		synchronized (this.lock) {
			if (this.canceledBySubscriber) {
				return;
			}
			this.canceledBySubscriber = true;
			this.lock.notifyAll();
		}
	}

	// ---------------------------------------------------------------- cleanup

	/**
	 * Schedules every registered resource for release, in strict reverse registration
	 * order, at most once per operation. Each link runs whether or not the previous one
	 * failed, so one failing close never strands the resources below it.
	 */
	void scheduleCleanup() {
		List<RegisteredResource> snapshot;
		synchronized (this.lock) {
			if (this.cleanupScheduled) {
				return;
			}
			this.cleanupScheduled = true;
			snapshot = new ArrayList<>(this.registry);
		}
		Collections.reverse(snapshot);
		CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
		for (RegisteredResource resource : snapshot) {
			chain = chain.handleAsync((ignored, error) -> {
				resource.closeOnce();
				return null;
			}, this.cleanupExecutor);
		}
	}

	/**
	 * Releases every still-unsettled resource independently, used once the operation has
	 * given up waiting for the ordered chain.
	 */
	private void submitDeadlineFallback() {
		List<RegisteredResource> pending;
		synchronized (this.lock) {
			pending = this.registry.stream().filter(resource -> !resource.isSettled()).toList();
		}
		for (RegisteredResource resource : pending) {
			try {
				this.cleanupExecutor.execute(resource::closeOnce);
			}
			catch (RejectedExecutionException ex) {
				logger.warn("Azure Speech cleanup executor rejected the fallback close of {} ({})", resource.kind(),
						ex.getClass().getName());
			}
		}
	}

	/**
	 * Releases one registered resource through its own idempotent close, used when the
	 * SDK disposes an object the model also owns.
	 */
	void closeRegistered(ResourceKind kind) {
		RegisteredResource target = null;
		synchronized (this.lock) {
			for (RegisteredResource resource : this.registry) {
				if (resource.kind() == kind) {
					target = resource;
					break;
				}
			}
		}
		if (target != null) {
			target.closeOnce();
		}
	}

	private void onResourceSettled(RegisteredResource resource, @Nullable Throwable failure) {
		synchronized (this.lock) {
			if (failure != null) {
				if (this.published) {
					logger.warn("Azure Speech {} did not release cleanly: {}", resource.kind(), failure.getMessage());
				}
				else {
					this.cleanupFailures.add(failure);
				}
			}
			this.lock.notifyAll();
		}
	}

	// ---------------------------------------------------------------- publication

	/**
	 * Waits for the operation's outcome on whichever thread wants it — the caller's for a
	 * blocking call, the model's publisher for a streaming subscription — which is also
	 * what enforces the deadline and closes a corroboration window.
	 * <p>
	 * It returns within the configured timeout in every case: either ordinary publication
	 * completes first, or the deadline publishes whatever has been decided, or the
	 * deadline itself decides the operation. A cancelled subscriber ends the wait the
	 * same way the deadline does, only sooner.
	 */
	Outcome awaitOutcome() {
		boolean interrupted = false;
		try {
			while (true) {
				Runnable afterLock = null;
				Outcome result = null;
				synchronized (this.lock) {
					if (this.published) {
						return requireOutcome();
					}
					long now = this.nanoTime.getAsLong();
					GiveUpReason giveUp = giveUpReasonLocked(now - this.deadlineNanos >= 0, interrupted);
					if (!this.decided) {
						if (this.provisionalArmed && now - this.provisionalExpiryNanos >= 0) {
							// The window elapsed with no contradicting error.
							decideLocked();
							afterLock = this::scheduleCleanup;
						}
						else if (giveUp != null) {
							afterLock = giveUpDecideLocked(giveUp);
						}
					}
					if (afterLock == null) {
						if (this.decided && this.inFlightAcquisitions == 0 && allResourcesSettledLocked()) {
							result = publishLocked();
						}
						else if (this.decided && giveUp != null) {
							result = publishLocked();
							afterLock = this::submitDeadlineFallback;
						}
						else {
							try {
								this.lock.wait(waitMillisLocked(now));
							}
							catch (InterruptedException ex) {
								interrupted = true;
							}
							continue;
						}
					}
				}
				if (afterLock != null) {
					afterLock.run();
				}
				if (result != null) {
					return result;
				}
			}
		}
		finally {
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}

	/**
	 * Returns why the wait must end now, or {@code null} while it may continue. A
	 * cancelled subscriber and an interrupted caller are both more specific than the
	 * deadline, so they are reported in preference to it.
	 */
	private @Nullable GiveUpReason giveUpReasonLocked(boolean deadlineReached, boolean interrupted) {
		if (interrupted) {
			return GiveUpReason.INTERRUPT;
		}
		if (this.canceledBySubscriber) {
			return GiveUpReason.CANCELLATION;
		}
		return deadlineReached ? GiveUpReason.DEADLINE : null;
	}

	/**
	 * Decides the operation because the caller stopped waiting, either accepting a
	 * provisional outcome early or, with nothing observed at all, failing it.
	 * @return the work to run once the lock is released
	 */
	private Runnable giveUpDecideLocked(GiveUpReason reason) {
		boolean acceptEarly = this.provisionalArmed || this.firstRecordedError != null;
		RecognizerAction stopAction = null;
		if (!acceptEarly) {
			// Nothing has told us the session ended, so ask the recognizer to stop.
			stopAction = reserveStopLocked();
			this.firstRecordedError = switch (reason) {
				case INTERRUPT -> AzureSpeechTranscriptionException.interrupted(new InterruptedException());
				case CANCELLATION -> AzureSpeechTranscriptionException.canceledBySubscriber();
				case DEADLINE -> AzureSpeechTranscriptionException.timedOut(Duration.ofNanos(this.timeoutNanos), null);
			};
		}
		decideLocked();
		RecognizerAction reserved = stopAction;
		return () -> {
			if (reserved != null) {
				// Dispatched, never inline: this runs on the caller's thread after its
				// deadline has already passed, and an SDK stop submission may block.
				submitStopAfterStart(reserved, this.cleanupExecutor);
			}
			scheduleCleanup();
		};
	}

	private Outcome publishLocked() {
		this.published = true;
		Outcome result = requireOutcome();
		RuntimeException error = result.error();
		for (Throwable failure : this.cleanupFailures) {
			if (error != null) {
				error.addSuppressed(failure);
			}
			else {
				logger.warn("Azure Speech resource did not release cleanly: {}", failure.getMessage());
			}
		}
		this.cleanupFailures.clear();
		this.lock.notifyAll();
		return result;
	}

	private Outcome requireOutcome() {
		Outcome result = this.outcome;
		if (result == null) {
			throw new IllegalStateException("Azure Speech operation published without an outcome");
		}
		return result;
	}

	private long waitMillisLocked(long now) {
		long targetNanos = this.deadlineNanos;
		if (!this.decided && this.provisionalArmed && this.provisionalExpiryNanos - targetNanos < 0) {
			targetNanos = this.provisionalExpiryNanos;
		}
		long remainingNanos = targetNanos - now;
		if (remainingNanos <= 0) {
			return 1L;
		}
		return Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remainingNanos) + 1L);
	}

	private boolean allResourcesSettledLocked() {
		return this.registry.stream().allMatch(RegisteredResource::isSettled);
	}

	// ---------------------------------------------------------------- observation hooks

	/**
	 * Returns every registered resource kind whose close has not concluded.
	 * <p>
	 * A kind that reached {@link CloseState#FAILED} is not pending: its close was
	 * attempted and concluded. A step still acquiring a resource that does not exist yet
	 * is not pending either; that shows up only in {@link #getInFlightAcquisitions()}.
	 */
	Set<ResourceKind> getPendingResourceKinds() {
		synchronized (this.lock) {
			Set<ResourceKind> pending = EnumSet.noneOf(ResourceKind.class);
			for (RegisteredResource resource : this.registry) {
				if (!resource.isSettled()) {
					pending.add(resource.kind());
				}
			}
			return pending;
		}
	}

	int getInFlightAcquisitions() {
		synchronized (this.lock) {
			return this.inFlightAcquisitions;
		}
	}

	int getLateEvents() {
		synchronized (this.lock) {
			return this.lateEvents;
		}
	}

	boolean isDecided() {
		synchronized (this.lock) {
			return this.decided;
		}
	}

	boolean isPublished() {
		synchronized (this.lock) {
			return this.published;
		}
	}

	@Nullable RegisteredResource recognizerResource() {
		synchronized (this.lock) {
			return this.recognizerResource;
		}
	}

	/**
	 * Blocks until nothing is acquiring and every registered resource has settled.
	 * @return {@code true} if that state was reached within {@code timeoutMillis}
	 */
	boolean awaitFullyAccounted(long timeoutMillis) throws InterruptedException {
		long deadline = this.nanoTime.getAsLong() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
		synchronized (this.lock) {
			while (this.inFlightAcquisitions != 0 || !allResourcesSettledLocked()) {
				long remaining = deadline - this.nanoTime.getAsLong();
				if (remaining <= 0) {
					return false;
				}
				this.lock.wait(Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remaining) + 1L));
			}
			return true;
		}
	}

	/**
	 * A blocking step that may throw a checked exception.
	 */
	/**
	 * Why a wait for publication ended without the operation settling on its own.
	 */
	private enum GiveUpReason {

		/**
		 * The configured timeout elapsed.
		 */
		DEADLINE,

		/**
		 * The thread waiting for the outcome was interrupted.
		 */
		INTERRUPT,

		/**
		 * A streaming subscriber cancelled its subscription.
		 */
		CANCELLATION

	}

	@FunctionalInterface
	interface ThrowingRunnable {

		void run() throws Exception;

	}

	/**
	 * An acquisition call that may throw a checked exception.
	 */
	@FunctionalInterface
	interface AcquisitionStep<T> {

		T get() throws Exception;

	}

}
