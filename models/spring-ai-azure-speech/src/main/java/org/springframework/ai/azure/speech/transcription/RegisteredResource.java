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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;

import org.jspecify.annotations.Nullable;

/**
 * One resource an operation owns, together with the guarantee that its {@code close()}
 * runs at most once however many callers race to release it.
 * <p>
 * The ordered cleanup chain, the deadline fallback, and — for the audio source — the
 * SDK's own disposal of the pull stream all call {@link #closeOnce()} on the same
 * instance. Exactly one of them performs the underlying close; every other caller blocks
 * until that close concludes and then returns, so no caller can report a state the real
 * close has not reached, and {@link #closeOnce()} never propagates anything to the
 * cleanup chain or into SDK callback code.
 * <p>
 * For {@link ResourceKind#RECOGNIZER} the close is additionally gated: the recognizer is
 * not closed until every start or stop action reserved before the close was claimed has
 * genuinely settled, because the SDK refuses to dispose a recognizer while it still
 * counts an asynchronous recognition as active.
 *
 * @author Spring AI Contributors
 */
final class RegisteredResource {

	private final ResourceKind kind;

	private final AutoCloseable target;

	/**
	 * The operation's single shared lock, so that reserving an action and claiming the
	 * close are mutually exclusive.
	 */
	private final Object lock;

	private final BiConsumer<RegisteredResource, @Nullable Throwable> onSettled;

	private final CompletableFuture<Void> closeSignal = new CompletableFuture<>();

	private volatile CloseState state = CloseState.PENDING;

	/**
	 * Recognizer-only. Every other kind leaves this empty and never reserves an action,
	 * so its gate is trivially open.
	 */
	private final List<RecognizerAction> reservedActions = new ArrayList<>();

	private @Nullable RecognizerAction startAction;

	private boolean closeClaimed;

	RegisteredResource(ResourceKind kind, AutoCloseable target, Object lock,
			BiConsumer<RegisteredResource, @Nullable Throwable> onSettled) {
		this.kind = kind;
		this.target = target;
		this.lock = lock;
		this.onSettled = onSettled;
	}

	ResourceKind kind() {
		return this.kind;
	}

	CloseState state() {
		return this.state;
	}

	CompletableFuture<Void> closeSignal() {
		return this.closeSignal;
	}

	boolean isSettled() {
		return this.state != CloseState.PENDING;
	}

	/**
	 * Reserves the one start action, called by the owner thread immediately after the
	 * recognizer is registered.
	 * @return the action to submit next, or {@code null} when the operation is already
	 * decided or the close has already been claimed, in which case the caller must not
	 * start recognition at all
	 */
	@Nullable RecognizerAction reserveStart(boolean decided) {
		synchronized (this.lock) {
			if (this.closeClaimed || decided) {
				return null;
			}
			RecognizerAction action = new RecognizerAction("start");
			this.reservedActions.add(action);
			this.startAction = action;
			return action;
		}
	}

	/**
	 * Reserves a stop action. Callers already holding the shared lock use
	 * {@link #reserveStopLocked()}.
	 * @return the action to submit, or {@code null} when the close has already been
	 * claimed or no start was ever reserved, because stopping a recognizer that was never
	 * told to start is not something any source specifies
	 */
	@Nullable RecognizerAction reserveStop() {
		synchronized (this.lock) {
			return reserveStopLocked();
		}
	}

	/**
	 * The body of {@link #reserveStop()} for a caller that already holds the shared lock,
	 * so that flipping the operation's decision and reserving the stop are one critical
	 * section.
	 */
	@Nullable RecognizerAction reserveStopLocked() {
		if (this.closeClaimed || this.startAction == null) {
			return null;
		}
		RecognizerAction action = new RecognizerAction("stop");
		this.reservedActions.add(action);
		return action;
	}

	/**
	 * Returns the reserved start action, or {@code null} when none was reserved.
	 */
	@Nullable RecognizerAction startAction() {
		synchronized (this.lock) {
			return this.startAction;
		}
	}

	/**
	 * Releases the resource, at most once in total, and never throws.
	 */
	void closeOnce() {
		List<RecognizerAction> gated = null;
		synchronized (this.lock) {
			if (!this.closeClaimed) {
				this.closeClaimed = true;
				gated = List.copyOf(this.reservedActions);
			}
		}
		if (gated != null) {
			boolean interrupted = awaitActions(gated);
			if (gated.stream().anyMatch(RecognizerAction::isUnobservable)) {
				// Closing now would be rejected by the SDK, and could dispose a
				// recognizer with work still running on it. Report instead.
				settle(CloseState.FAILED, new AzureSpeechResourceException("Azure Speech " + this.kind
						+ " was not closed: completion of a submitted SDK action is unobservable"));
			}
			else {
				try {
					this.target.close();
					settle(CloseState.CLOSED, null);
				}
				catch (Exception ex) {
					settle(CloseState.FAILED, new AzureSpeechCleanupException(
							"Azure Speech " + this.kind + " close() failed (" + ex.getClass().getName() + ")"));
				}
			}
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
		try {
			this.closeSignal.join();
		}
		catch (CompletionException ignored) {
			// The sanitized failure is recorded once, by settle(), and surfaced by the
			// operation as a suppressed exception or a WARN. Every caller here only
			// needs to know the attempt concluded, not how it turned out.
		}
	}

	/**
	 * Waits out every gated action without ever giving up on one. Returns whether this
	 * thread was interrupted while waiting, so the caller can restore the flag once.
	 */
	private boolean awaitActions(List<RecognizerAction> gated) {
		boolean interrupted = false;
		for (RecognizerAction action : gated) {
			while (true) {
				try {
					action.settled().get();
					break;
				}
				catch (InterruptedException ex) {
					interrupted = true;
				}
				catch (java.util.concurrent.ExecutionException | CompletionException ex) {
					// An action's slot is only ever completed normally; this is
					// defensive and carries no information the gate can act on.
					break;
				}
			}
		}
		return interrupted;
	}

	/**
	 * Writes the terminal state before completing {@link #closeSignal}, so that a thread
	 * woken by that signal can never observe a state the close has not reached.
	 */
	private void settle(CloseState newState, @Nullable Throwable failure) {
		this.state = newState;
		if (failure == null) {
			this.closeSignal.complete(null);
		}
		else {
			this.closeSignal.completeExceptionally(failure);
		}
		this.onSettled.accept(this, failure);
	}

	@Override
	public String toString() {
		return "RegisteredResource{kind=" + this.kind + ", state=" + this.state + '}';
	}

}
