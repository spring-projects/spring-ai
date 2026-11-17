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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.jspecify.annotations.Nullable;

/**
 * One start or stop call issued against the recognizer, tracked so that the recognizer is
 * never closed while the SDK still counts that call as running.
 * <p>
 * {@link #settled} completes only when the action genuinely concluded: either nothing was
 * ever handed to the SDK, or some thread observed {@link #sdkFuture} reach a terminal
 * state, or the degenerate {@link #unobservable} case below. A failure merely to
 * <em>schedule</em> the observing thread is never one of those reasons.
 *
 * @author Spring AI Contributors
 */
final class RecognizerAction {

	/**
	 * {@code start} or {@code stop}, used in log messages only.
	 */
	private final String name;

	private final CompletableFuture<Void> settled = new CompletableFuture<>();

	private volatile @Nullable Future<Void> sdkFuture;

	private volatile boolean submitted;

	private volatile boolean unobservable;

	private volatile boolean dispatchRan;

	RecognizerAction(String name) {
		this.name = name;
	}

	String name() {
		return this.name;
	}

	CompletableFuture<Void> settled() {
		return this.settled;
	}

	@Nullable Future<Void> sdkFuture() {
		return this.sdkFuture;
	}

	void sdkFuture(Future<Void> sdkFuture) {
		this.sdkFuture = sdkFuture;
	}

	/**
	 * Returns {@code true} once the SDK accepted the submission, meaning work may be
	 * running on the recognizer.
	 */
	boolean isSubmitted() {
		return this.submitted;
	}

	void markSubmitted() {
		this.submitted = true;
	}

	/**
	 * Returns {@code true} when no thread could be found to observe this action's
	 * completion, so the recognizer must not be closed.
	 */
	boolean isUnobservable() {
		return this.unobservable;
	}

	void markUnobservable() {
		this.unobservable = true;
	}

	/**
	 * Returns {@code true} once the deferred submission body has begun running. Read only
	 * when a dispatch throws, to tell "the task never ran" from "the task ran and the
	 * executor threw afterwards".
	 */
	boolean hasDispatchRun() {
		return this.dispatchRan;
	}

	void markDispatchRun() {
		this.dispatchRan = true;
	}

}
