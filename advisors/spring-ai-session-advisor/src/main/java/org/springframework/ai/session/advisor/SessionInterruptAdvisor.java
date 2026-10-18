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

import io.micrometer.context.ContextSnapshot;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.status.TurnService;
import org.springframework.ai.session.status.TurnStatus;
import org.springframework.core.Ordered;
import org.springframework.util.Assert;

/**
 * Stops the tool-calling loop after an interrupt. Before each model round the advisor
 * reads the session's {@link TurnService#status status}. When the turn no longer runs
 * under the call's run id, because {@link TurnService#interrupt} ended it, the advisor
 * throws {@link TurnInterruptedException} and the loop unwinds; the rounds already run
 * stay in the log, the tool results of the last one included.
 * <p>
 * The check runs on every pass of the chain through the advisor, the first one included.
 * The advisor does not know which advisor loops around it, the tool-calling advisor, the
 * structured output advisor or a retrying advisor of your own, so it reads the status
 * every time. The check is one indexed read per round, which is why the advisor is
 * opt-in: {@link SessionAdvisors.Builder#interruptible(boolean)} adds it. <pre>{@code
 * ChatClient chatClient = ChatClient.builder(chatModel)
 *     .defaultAdvisors(SessionAdvisors.builder(sessionService).interruptible(true).build())
 *     .build();
 *
 * // from a stop button, another instance, a scheduler
 * sessionService.turns().interrupt(sessionId, "user pressed stop");
 * }</pre>
 * <p>
 * The default order ({@link #DEFAULT_ORDER}) places it just inside the
 * {@link SessionMemoryAdvisor}, so the memory advisor has stored the round's request, the
 * tool results of the round before it, by the time the check throws, and the log keeps
 * every round that ran. It sits inside the tool-calling advisor, so an interrupt raised
 * while tools execute lands before the next model request, not only between turns. A loop
 * that runs around the advisor is checked between its rounds; a loop that runs inside it,
 * the structured output advisor at its default order for example, is not, and neither
 * does the session record its retries. Order a retrying advisor between the tool-calling
 * advisor and the memory advisor to have its retries both recorded and interruptible.
 * <p>
 * The run id comes from {@link SessionTurnAdvisor#RUN_ID_CONTEXT_KEY}, which the
 * {@link SessionTurnAdvisor} sets on the request it forwards. The advisor therefore needs
 * the turn advisor outside the loop and fails the call when the key is missing.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class SessionInterruptAdvisor implements BaseAdvisor {

	/**
	 * Just inside the {@link SessionMemoryAdvisor} ({@code HIGHEST_PRECEDENCE + 1000})
	 * and inside the tool-calling advisor ({@code HIGHEST_PRECEDENCE + 300}), so the
	 * memory advisor stores a round's request before the check throws, and a looping
	 * advisor ordered between the tool-calling and the memory advisor has its rounds
	 * recorded and checked alike.
	 */
	public static final int DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 1100;

	private final TurnService turns;

	private final int order;

	private final Scheduler scheduler;

	private SessionInterruptAdvisor(Builder builder) {
		this.turns = builder.sessionService.turns();
		this.order = builder.order;
		this.scheduler = builder.scheduler;
	}

	public static Builder builder(SessionService sessionService) {
		return new Builder(sessionService);
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	@Override
	public Scheduler getScheduler() {
		return this.scheduler;
	}

	@Override
	public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
		String sessionId = SessionContexts.sessionId(request.context());
		String runId = SessionContexts.runId(request.context());
		if (runId == null) {
			throw new IllegalStateException("No run id found in advisor context. SessionInterruptAdvisor runs inside "
					+ "the tool-calling loop and needs the SessionTurnAdvisor outside it, which starts the turn and "
					+ "sets SessionTurnAdvisor.RUN_ID_CONTEXT_KEY on the request it forwards");
		}
		TurnStatus status = this.turns.status(sessionId);
		if (!status.isRunning(runId)) {
			throw new TurnInterruptedException(sessionId, runId, status);
		}
		return request;
	}

	@Override
	public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
		return response;
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
		// The status read runs on the scheduler with the caller's context restored, like
		// the session calls of the other advisors
		return Flux.deferContextual(contextView -> {
			ContextSnapshot context = SessionContexts.snapshot(contextView);
			return Mono.just(request)
				.publishOn(this.scheduler)
				.map(r -> SessionContexts.withContext(context, () -> before(r, chain)))
				.flatMapMany(chain::nextStream);
		});
	}

	public static final class Builder {

		private final SessionService sessionService;

		private int order = DEFAULT_ORDER;

		private Scheduler scheduler = BaseAdvisor.DEFAULT_SCHEDULER;

		private Builder(SessionService sessionService) {
			Assert.notNull(sessionService, "sessionService must not be null");
			this.sessionService = sessionService;
		}

		/**
		 * The advisor order; {@link #DEFAULT_ORDER} by default. Keep it inside the
		 * tool-calling advisor and every other looping advisor whose rounds the check
		 * must see, and outside the memory advisor.
		 */
		public Builder order(int order) {
			this.order = order;
			return this;
		}

		/**
		 * The scheduler that runs the status read of a streamed call; the advisors'
		 * default scheduler by default.
		 */
		public Builder scheduler(Scheduler scheduler) {
			Assert.notNull(scheduler, "scheduler must not be null");
			this.scheduler = scheduler;
			return this;
		}

		public SessionInterruptAdvisor build() {
			return new SessionInterruptAdvisor(this);
		}

	}

}
