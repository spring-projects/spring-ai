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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import reactor.core.scheduler.Scheduler;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.compaction.CompactionStrategy;
import org.springframework.ai.session.compaction.CompactionTrigger;
import org.springframework.ai.session.status.TurnService;
import org.springframework.util.Assert;

/**
 * The session advisors of a {@code ChatClient}, configured together: the
 * {@link SessionTurnAdvisor} (the session, the turn status, compaction) and the
 * {@link SessionMemoryAdvisor} (the history, the messages), each at its default order
 * around the tool-calling loop the {@code ChatClient} registers itself, and, when
 * {@link Builder#interruptible(boolean) interruptible}, the
 * {@link SessionInterruptAdvisor} inside the loop: <pre class="code">
 * ChatClient chatClient = ChatClient.builder(chatModel)
 *     .defaultAdvisors(SessionAdvisors.builder(sessionService)
 *         .compaction(new TurnCountTrigger(20), WindowCompactionStrategy.builder().lastTurns(10).build())
 *         .build())
 *     .build();
 * chatClient.prompt().user("Hi").advisors(SessionAdvisors.session(sessionId)).call().content();
 * </pre>
 * <p>
 * The memory advisor is a {@code MemoryAdvisor}, so the tool-calling loop the
 * {@code ChatClient} registers runs with its own history off: each round's prompt is
 * rebuilt from the session.
 * <p>
 * The advisors can still be built one by one, for an order of your own.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class SessionAdvisors {

	private SessionAdvisors() {
	}

	public static Builder builder(SessionService sessionService) {
		return new Builder(sessionService);
	}

	/**
	 * Runs the request on the session:
	 * {@code .advisors(SessionAdvisors.session(sessionId))}. Sets
	 * {@link SessionMemoryAdvisor#SESSION_ID_CONTEXT_KEY}; the turn advisor generates a
	 * run id of its own for the call.
	 * @param sessionId the id of the session the request runs on
	 * @return the advisor customizer to pass to {@code .advisors(...)}
	 * @throws IllegalArgumentException if the session id is empty
	 */
	public static Consumer<ChatClient.AdvisorSpec> session(String sessionId) {
		Assert.hasText(sessionId, "sessionId must not be empty");
		return advisors -> advisors.param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, sessionId);
	}

	/**
	 * Runs the request on the session under the run id of your own harness:
	 * {@code .advisors(SessionAdvisors.session(sessionId, runId))}. Sets
	 * {@link SessionMemoryAdvisor#SESSION_ID_CONTEXT_KEY} and
	 * {@link SessionTurnAdvisor#RUN_ID_CONTEXT_KEY}, the pair {@link TurnService#start}
	 * takes.
	 * <p>
	 * Use it per request. A run id set as a default parameter of the client puts every
	 * call under one run, which the status allows but rarely means what the caller wants.
	 * Call {@link #session(String)} when there is no run id to pass.
	 * @param sessionId the id of the session the request runs on
	 * @param runId the run id the call's turn runs under
	 * @return the advisor customizer to pass to {@code .advisors(...)}
	 * @throws IllegalArgumentException if the session id or the run id is empty
	 */
	public static Consumer<ChatClient.AdvisorSpec> session(String sessionId, String runId) {
		Assert.hasText(sessionId, "sessionId must not be empty");
		Assert.hasText(runId, "runId must not be empty");
		return advisors -> advisors.param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, sessionId)
			.param(SessionTurnAdvisor.RUN_ID_CONTEXT_KEY, runId);
	}

	public static final class Builder {

		private final SessionService sessionService;

		@Nullable private Scheduler scheduler;

		@Nullable private CompactionTrigger compactionTrigger;

		@Nullable private CompactionStrategy compactionStrategy;

		private boolean asyncCompaction;

		@Nullable private Duration turnTimeout = SessionTurnAdvisor.DEFAULT_TURN_TIMEOUT;

		private boolean interruptible;

		private Consumer<SessionMemoryAdvisor.Builder> memory = memory -> {
		};

		private Builder(SessionService sessionService) {
			Assert.notNull(sessionService, "sessionService must not be null");
			this.sessionService = sessionService;
		}

		/** The scheduler the session writes of a streamed call run on. */
		public Builder scheduler(Scheduler scheduler) {
			Assert.notNull(scheduler, "scheduler must not be null");
			this.scheduler = scheduler;
			return this;
		}

		/** Compacts the session once a turn has ended, when the trigger fires. */
		public Builder compaction(CompactionTrigger trigger, CompactionStrategy strategy) {
			Assert.notNull(trigger, "trigger must not be null");
			Assert.notNull(strategy, "strategy must not be null");
			this.compactionTrigger = trigger;
			this.compactionStrategy = strategy;
			return this;
		}

		/**
		 * Whether compaction runs after the call returns, on the scheduler; see
		 * {@link SessionTurnAdvisor.Builder#asyncCompaction(boolean)}.
		 */
		public Builder asyncCompaction(boolean asyncCompaction) {
			this.asyncCompaction = asyncCompaction;
			return this;
		}

		/**
		 * How long a call may hold the session's turn before another call may take it
		 * over; {@link SessionTurnAdvisor#DEFAULT_TURN_TIMEOUT} by default, {@code null}
		 * for no lease. See {@link SessionTurnAdvisor.Builder#turnTimeout(Duration)}.
		 */
		public Builder turnTimeout(@Nullable Duration turnTimeout) {
			Assert.isTrue(turnTimeout == null || turnTimeout.compareTo(Duration.ZERO) > 0,
					"turnTimeout must be positive");
			this.turnTimeout = turnTimeout;
			return this;
		}

		/**
		 * Whether the tool-calling loop stops after an {@link TurnService#interrupt
		 * interrupt}; {@code false} by default. When {@code true} the
		 * {@link SessionInterruptAdvisor} joins the advisors, inside the memory advisor:
		 * it reads the session's status before every model round, one indexed read each,
		 * and ends the call with {@link TurnInterruptedException} when the turn no longer
		 * runs. Without it an interrupt moves the status to idle at once, but the loop
		 * runs on to its end.
		 */
		public Builder interruptible(boolean interruptible) {
			this.interruptible = interruptible;
			return this;
		}

		/**
		 * Customizes the {@link SessionMemoryAdvisor}, its filters for example.
		 * Customizers run in the order they are added, after the shared settings, so they
		 * can override them.
		 */
		public Builder memory(Consumer<SessionMemoryAdvisor.Builder> memory) {
			Assert.notNull(memory, "memory must not be null");
			this.memory = this.memory.andThen(memory);
			return this;
		}

		/**
		 * The advisors, in their order: the turn, the memory, then the interrupt when
		 * {@link #interruptible}.
		 */
		public List<Advisor> build() {
			Scheduler scheduler = (this.scheduler != null) ? this.scheduler : BaseAdvisor.DEFAULT_SCHEDULER;

			SessionTurnAdvisor.Builder turn = SessionTurnAdvisor.builder(this.sessionService)
				.scheduler(scheduler)
				.asyncCompaction(this.asyncCompaction)
				.turnTimeout(this.turnTimeout);
			if (this.compactionTrigger != null && this.compactionStrategy != null) {
				turn.compactionTrigger(this.compactionTrigger).compactionStrategy(this.compactionStrategy);
			}

			SessionMemoryAdvisor.Builder memory = SessionMemoryAdvisor.builder(this.sessionService)
				.scheduler(scheduler);
			this.memory.accept(memory);

			SessionMemoryAdvisor memoryAdvisor = memory.build();
			List<Advisor> advisors = new ArrayList<>();
			advisors.add(turn.build());
			advisors.add(memoryAdvisor);
			if (this.interruptible) {
				// Just inside the memory advisor, wherever a customizer put it, so the
				// round's request is stored before the check throws
				advisors.add(SessionInterruptAdvisor.builder(this.sessionService)
					.scheduler(scheduler)
					.order(memoryAdvisor.getOrder() + 100)
					.build());
			}
			return List.copyOf(advisors);
		}

	}

}
