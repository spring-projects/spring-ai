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

package org.springframework.ai.session.advisor;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.scheduler.Scheduler;

import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.compaction.CompactionStrategy;
import org.springframework.ai.session.compaction.CompactionTrigger;

/**
 * Compacts a session after a turn, for the {@link SessionTurnAdvisor}: now, or on the
 * scheduler when compaction is asynchronous, one at a time per session. Without a trigger
 * and a strategy it does nothing. Compaction is best-effort: a failure is logged, and the
 * next turn retries it.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
final class CompactionScheduler {

	// The advisor's category, so its logging configuration keeps applying
	private static final Logger logger = LoggerFactory.getLogger(SessionTurnAdvisor.class);

	private final SessionService sessionService;

	@Nullable private final CompactionTrigger trigger;

	@Nullable private final CompactionStrategy strategy;

	private final boolean async;

	private final Scheduler scheduler;

	/** The sessions an asynchronous compaction runs on, so it runs once each. */
	private final Set<String> compacting = ConcurrentHashMap.newKeySet();

	CompactionScheduler(SessionService sessionService, @Nullable CompactionTrigger trigger,
			@Nullable CompactionStrategy strategy, boolean async, Scheduler scheduler) {
		this.sessionService = sessionService;
		this.trigger = trigger;
		this.strategy = strategy;
		this.async = async;
		this.scheduler = scheduler;
	}

	/** Compacts the session if the trigger fires, now or on the scheduler. */
	void afterTurn(String sessionId) {
		if (this.trigger == null || this.strategy == null) {
			return;
		}
		CompactionTrigger trigger = this.trigger;
		CompactionStrategy strategy = this.strategy;
		if (!this.async) {
			compact(sessionId, trigger, strategy);
		}
		else if (this.compacting.add(sessionId)) {
			// The caller's thread-local context goes with it: an authorizing
			// SessionService takes the caller from the security context, and tracing
			// keeps its parent
			Runnable task = SessionContexts.snapshot(null).wrap(() -> {
				try {
					compact(sessionId, trigger, strategy);
				}
				finally {
					this.compacting.remove(sessionId);
				}
			});
			try {
				this.scheduler.schedule(task);
			}
			catch (RuntimeException ex) {
				this.compacting.remove(sessionId);
				logger.warn("Could not schedule the compaction of session [{}]; it will be retried after the next "
						+ "turn", sessionId, ex);
			}
		}
	}

	/** Whether an asynchronous compaction runs on the session. */
	boolean isCompacting(String sessionId) {
		return this.compacting.contains(sessionId);
	}

	private void compact(String sessionId, CompactionTrigger trigger, CompactionStrategy strategy) {
		try {
			this.sessionService.compact(sessionId, trigger, strategy);
		}
		catch (RuntimeException ex) {
			logger.warn("Compaction failed for session [{}]; the event log is unchanged and compaction will be "
					+ "retried after the next turn", sessionId, ex);
		}
	}

}
