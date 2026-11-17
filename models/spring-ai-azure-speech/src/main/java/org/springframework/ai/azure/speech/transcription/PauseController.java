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

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test-only coordination for {@link PausePoint}s. Never installed in production.
 *
 * @author Spring AI Contributors
 */
final class PauseController {

	private final Map<PausePoint, CountDownLatch> release = new EnumMap<>(PausePoint.class);

	private final Map<PausePoint, CountDownLatch> reached = new EnumMap<>(PausePoint.class);

	/**
	 * Arms {@code point} so that the next thread reaching it blocks until
	 * {@link #release(PausePoint)}.
	 */
	synchronized void arm(PausePoint point) {
		this.release.put(point, new CountDownLatch(1));
		this.reached.put(point, new CountDownLatch(1));
	}

	/**
	 * Blocks until a thread reaches {@code point}, failing rather than hanging if none
	 * ever does.
	 */
	void awaitReached(PausePoint point) throws InterruptedException {
		CountDownLatch latch;
		synchronized (this) {
			latch = this.reached.get(point);
		}
		if (latch != null && !latch.await(30, TimeUnit.SECONDS)) {
			throw new IllegalStateException("no thread reached the pause point " + point);
		}
	}

	/**
	 * Lets the paused thread continue.
	 */
	synchronized void release(PausePoint point) {
		CountDownLatch latch = this.release.get(point);
		if (latch != null) {
			latch.countDown();
		}
	}

	void pauseIfRequested(PausePoint point) {
		CountDownLatch reachedLatch;
		CountDownLatch releaseLatch;
		synchronized (this) {
			reachedLatch = this.reached.get(point);
			releaseLatch = this.release.get(point);
		}
		if (releaseLatch == null || reachedLatch == null) {
			return;
		}
		reachedLatch.countDown();
		try {
			releaseLatch.await();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

}
