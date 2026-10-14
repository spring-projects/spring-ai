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
import org.springframework.util.Assert;

/**
 * The session advisors of a {@code ChatClient}, configured together: the
 * {@link SessionTurnAdvisor} (the session, compaction) and the
 * {@link SessionMemoryAdvisor} (the history, the messages), each at its default order
 * around the tool-calling loop the {@code ChatClient} registers itself:
 * <pre class="code">
 * ChatClient chatClient = ChatClient.builder(chatModel)
 *     .defaultAdvisors(SessionAdvisors.builder(sessionService)
 *         .compaction(new TurnCountTrigger(20), WindowCompactionStrategy.builder().lastTurns(10).build())
 *         .build())
 *     .build();
 * chatClient.prompt().user("Hi").advisors(SessionAdvisors.session(sessionId)).call().content();
 * </pre>
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
	 * The request runs on the session:
	 * {@code .advisors(SessionAdvisors.session(sessionId))}.
	 */
	public static Consumer<ChatClient.AdvisorSpec> session(String sessionId) {
		Assert.hasText(sessionId, "sessionId must not be empty");
		return advisors -> advisors.param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, sessionId);
	}

	public static final class Builder {

		private final SessionService sessionService;

		@Nullable private Scheduler scheduler;

		@Nullable private CompactionTrigger compactionTrigger;

		@Nullable private CompactionStrategy compactionStrategy;

		private boolean asyncCompaction;

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
		 * Customizes the {@link SessionMemoryAdvisor}, its filters for example.
		 * Customizers run in the order they are added, after the shared settings, so they
		 * can override them.
		 */
		public Builder memory(Consumer<SessionMemoryAdvisor.Builder> memory) {
			Assert.notNull(memory, "memory must not be null");
			this.memory = this.memory.andThen(memory);
			return this;
		}

		/** The advisors, in their order: the turn, then the memory. */
		public List<Advisor> build() {
			Scheduler scheduler = (this.scheduler != null) ? this.scheduler : BaseAdvisor.DEFAULT_SCHEDULER;

			SessionTurnAdvisor.Builder turn = SessionTurnAdvisor.builder(this.sessionService)
				.scheduler(scheduler)
				.asyncCompaction(this.asyncCompaction);
			if (this.compactionTrigger != null && this.compactionStrategy != null) {
				turn.compactionTrigger(this.compactionTrigger).compactionStrategy(this.compactionStrategy);
			}

			SessionMemoryAdvisor.Builder memory = SessionMemoryAdvisor.builder(this.sessionService)
				.scheduler(scheduler);
			this.memory.accept(memory);

			List<Advisor> advisors = new ArrayList<>();
			advisors.add(turn.build());
			advisors.add(memory.build());
			return List.copyOf(advisors);
		}

	}

}
