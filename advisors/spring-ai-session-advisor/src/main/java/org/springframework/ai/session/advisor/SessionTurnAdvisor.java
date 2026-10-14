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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.context.ContextSnapshotFactory;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.compaction.CompactionStrategy;
import org.springframework.ai.session.compaction.CompactionTrigger;
import org.springframework.core.Ordered;
import org.springframework.util.Assert;

/**
 * Runs each {@code ChatClient} call as one turn of the session: the counterpart of the
 * {@link SessionMemoryAdvisor}, which works on each model call. It sits outside the
 * tool-calling loop (order {@link #DEFAULT_ORDER}), so it sees the request before the
 * first round and the turn's final response after the last one, however the loop ended.
 * <ol>
 * <li>Finds the session or creates it on first use, with the metadata from
 * {@link SessionMemoryAdvisor#SESSION_METADATA_CONTEXT_KEY}, such as the agent
 * attributes.</li>
 * <li>After a loop that ended on a {@code returnDirect} tool or a breached tool call
 * limit, stores the reply's tool results, which no model round stored.</li>
 * <li>Compacts the session once the call has returned, if a
 * {@link Builder#compactionTrigger trigger} fires: before the reply is returned, or after
 * it on the scheduler with {@link Builder#asyncCompaction(boolean)}. Compaction is
 * best-effort: a failure is logged and the reply returned, and it is retried after the
 * next turn. It appends a boundary with an optimistic compare-and-swap, so of two
 * concurrent compactions only the first lands, and a compaction that a new turn's writes
 * overtake is skipped and retried.</li>
 * </ol>
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class SessionTurnAdvisor implements CallAdvisor, StreamAdvisor {

	/**
	 * Outside the tool-calling advisor ({@code HIGHEST_PRECEDENCE + 300}), so the loop
	 * runs inside the turn.
	 */
	public static final int DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 200;

	/** The result a tool call gets in the log when the loop ended before it ran. */
	public static final String NOT_RUN_TOOL_RESULT = "The tool call did not run: the tool call limit was reached.";

	private static final Logger logger = LoggerFactory.getLogger(SessionTurnAdvisor.class);

	/**
	 * Carries the caller's thread-local context, such as the security context, to an
	 * async compaction.
	 */
	private static final ContextSnapshotFactory CONTEXT_SNAPSHOTS = ContextSnapshotFactory.builder().build();

	private final SessionService sessionService;

	private final int order;

	private final Scheduler scheduler;

	@Nullable private final CompactionTrigger compactionTrigger;

	@Nullable private final CompactionStrategy compactionStrategy;

	private final boolean asyncCompaction;

	/**
	 * The sessions an asynchronous compaction of this advisor runs on, so it runs once
	 * each.
	 */
	private final Set<String> compacting = ConcurrentHashMap.newKeySet();

	private SessionTurnAdvisor(Builder builder) {
		this.sessionService = builder.sessionService;
		this.order = builder.order;
		this.scheduler = builder.scheduler;
		this.compactionTrigger = builder.compactionTrigger;
		this.compactionStrategy = builder.compactionStrategy;
		this.asyncCompaction = builder.asyncCompaction;
	}

	public static Builder builder(SessionService sessionService) {
		return new Builder(sessionService);
	}

	@Override
	public String getName() {
		return getClass().getSimpleName();
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
		Assert.notNull(request, "request must not be null");
		Assert.notNull(chain, "chain must not be null");
		String sessionId = SessionContexts.sessionId(request.context());
		SessionContexts.findOrCreate(this.sessionService, sessionId, request.context());
		ChatClientResponse response = chain.nextCall(request);
		complete(sessionId, response.chatResponse());
		return response;
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
		Assert.notNull(request, "request must not be null");
		Assert.notNull(chain, "chain must not be null");
		String sessionId = SessionContexts.sessionId(request.context());
		return Mono.just(request).publishOn(this.scheduler).flatMapMany(r -> {
			SessionContexts.findOrCreate(this.sessionService, sessionId, r.context());
			// A returnDirect result or a breached limit ends the stream on a chunk of its
			// own
			AtomicReference<@Nullable ChatResponse> unanswered = new AtomicReference<>();
			// Back on the scheduler, so the session writes never run on the model's
			// thread
			return chain.nextStream(r).doOnNext(response -> {
				if (endsWithoutModel(response.chatResponse())) {
					unanswered.set(response.chatResponse());
				}
			}).publishOn(this.scheduler).doOnComplete(() -> complete(sessionId, unanswered.get()));
		});
	}

	/** Stores the results of a loop that ended without a model round, then compacts. */
	private void complete(String sessionId, @Nullable ChatResponse response) {
		if (response != null && endsWithoutModel(response)) {
			storeResults(sessionId, response);
		}
		compactIfTriggered(sessionId);
	}

	/**
	 * Whether the loop ended on a reply's tool calls without a model round after them: a
	 * {@code returnDirect} result or a breached tool call limit. No round stored the
	 * results then.
	 */
	private static boolean endsWithoutModel(@Nullable ChatResponse response) {
		return response != null && response.getResults()
			.stream()
			.map(Generation::getMetadata)
			.anyMatch(metadata -> ToolExecutionResult.FINISH_REASON.equals(metadata.getFinishReason())
					|| ToolCallLimitExceededException.FINISH_REASON.equals(metadata.getFinishReason()));
	}

	/**
	 * Answers the stored reply's tool calls in the log after a loop that ended without a
	 * model round: with the results that came back directly, or, for a breached limit,
	 * with the results of the calls that ran, the breach for the call that broke it and a
	 * note for the calls after it. The results come in the order of the calls. Once: the
	 * id derives from the reply's.
	 */
	private void storeResults(String sessionId, ChatResponse response) {
		try {
			SessionEvent stored = newestConversationEvent(sessionId);
			if (stored == null || !stored.hasToolCalls()) {
				return;
			}
			List<AssistantMessage.ToolCall> calls = ((AssistantMessage) stored.getMessage()).getToolCalls();
			List<String> answers = answers(response, calls.size());
			List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
			for (int i = 0; i < calls.size(); i++) {
				responses
					.add(new ToolResponseMessage.ToolResponse(calls.get(i).id(), calls.get(i).name(), answers.get(i)));
			}
			this.sessionService.appendEvent(SessionEvent.builder()
				.id(stored.getId() + "-results")
				.sessionId(sessionId)
				.message(ToolResponseMessage.builder().responses(responses).build())
				.build());
		}
		catch (RuntimeException ex) {
			logger.warn("Could not store the tool results of session [{}]: {}", sessionId, ex.getMessage());
		}
	}

	/** One answer per call, in the order of the calls. */
	private static List<String> answers(ChatResponse response, int calls) {
		List<String> answers = new ArrayList<>(calls);
		for (Generation generation : response.getResults()) {
			ChatGenerationMetadata metadata = generation.getMetadata();
			if (ToolExecutionResult.FINISH_REASON.equals(metadata.getFinishReason())) {
				answers.add(Objects.requireNonNullElse(generation.getOutput().getText(), ""));
			}
			else if (ToolCallLimitExceededException.FINISH_REASON.equals(metadata.getFinishReason())) {
				if (metadata
					.get(ToolCallLimitExceededException.METADATA_PARTIAL_TOOL_RESPONSES) instanceof List<?> ran) {
					ran.stream()
						.filter(ToolResponseMessage.ToolResponse.class::isInstance)
						.map(result -> ((ToolResponseMessage.ToolResponse) result).responseData())
						.forEach(answers::add);
				}
				answers.add(Objects.requireNonNullElse(generation.getOutput().getText(), ""));
			}
		}
		while (answers.size() < calls) {
			answers.add(NOT_RUN_TOOL_RESULT);
		}
		return answers.subList(0, calls);
	}

	/**
	 * Compacts the session if the trigger fires: now, or on the scheduler when compaction
	 * is asynchronous and none runs on the session yet.
	 */
	private void compactIfTriggered(String sessionId) {
		if (this.compactionTrigger == null || this.compactionStrategy == null) {
			return;
		}
		if (!this.asyncCompaction) {
			compact(sessionId, this.compactionTrigger, this.compactionStrategy);
		}
		else if (this.compacting.add(sessionId)) {
			CompactionTrigger trigger = this.compactionTrigger;
			CompactionStrategy strategy = this.compactionStrategy;
			// The caller's thread-local context goes with it: an authorizing
			// SessionService
			// takes the caller from the security context, and tracing keeps its parent
			Runnable task = CONTEXT_SNAPSHOTS.captureAll().wrap(() -> {
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

	/** Whether an asynchronous compaction of this advisor runs on the session. */
	boolean isCompacting(String sessionId) {
		return this.compacting.contains(sessionId);
	}

	/** Compacts the session if the trigger fires; a failure is logged, not thrown. */
	private void compact(String sessionId, CompactionTrigger trigger, CompactionStrategy strategy) {
		try {
			this.sessionService.compact(sessionId, trigger, strategy);
		}
		catch (RuntimeException ex) {
			logger.warn("Compaction failed for session [{}]; the event log is unchanged and compaction will be "
					+ "retried after the next turn", sessionId, ex);
		}
	}

	/**
	 * The newest message of the active view that is not a system message, or
	 * {@code null}.
	 */
	private @Nullable SessionEvent newestConversationEvent(String sessionId) {
		List<SessionEvent> events = this.sessionService.getEvents(sessionId,
				EventFilter.active().toBuilder().lastN(1).build());
		for (int i = events.size() - 1; i >= 0; i--) {
			if (events.get(i).hasMessage() && events.get(i).getMessageType() != MessageType.SYSTEM) {
				return events.get(i);
			}
		}
		return null;
	}

	public static final class Builder {

		private final SessionService sessionService;

		private int order = DEFAULT_ORDER;

		private Scheduler scheduler = BaseAdvisor.DEFAULT_SCHEDULER;

		@Nullable private CompactionTrigger compactionTrigger;

		@Nullable private CompactionStrategy compactionStrategy;

		private boolean asyncCompaction;

		private Builder(SessionService sessionService) {
			Assert.notNull(sessionService, "sessionService must not be null");
			this.sessionService = sessionService;
		}

		/**
		 * Keep it outside the tool-calling advisor (a lower value than its order);
		 * defaults to {@link #DEFAULT_ORDER}.
		 */
		public Builder order(int order) {
			this.order = order;
			return this;
		}

		/** The scheduler the session writes of a streamed call run on. */
		public Builder scheduler(Scheduler scheduler) {
			Assert.notNull(scheduler, "scheduler must not be null");
			this.scheduler = scheduler;
			return this;
		}

		/**
		 * When to compact once a turn has ended; set together with
		 * {@link #compactionStrategy}.
		 */
		public Builder compactionTrigger(CompactionTrigger trigger) {
			Assert.notNull(trigger, "trigger must not be null");
			this.compactionTrigger = trigger;
			return this;
		}

		/** How to compact; set together with {@link #compactionTrigger}. */
		public Builder compactionStrategy(CompactionStrategy strategy) {
			Assert.notNull(strategy, "strategy must not be null");
			this.compactionStrategy = strategy;
			return this;
		}

		/**
		 * Whether compaction runs after the call returns, on the {@link #scheduler},
		 * rather than before it: a summarizing strategy then adds no model call to the
		 * reply's latency. For a {@code call()} it runs with the caller's thread-local
		 * context (the security context, the current observation), captured through
		 * Micrometer's context propagation; a {@code stream()} ends on the scheduler,
		 * which has none to carry. This advisor runs one compaction per session at a
		 * time; across advisors, the compare-and-swap keeps only one. A new turn that
		 * writes to the session while it runs makes it skip, and it is retried after that
		 * turn. With an immediate scheduler it runs before the call returns after all.
		 * Defaults to {@code false}.
		 */
		public Builder asyncCompaction(boolean asyncCompaction) {
			this.asyncCompaction = asyncCompaction;
			return this;
		}

		public SessionTurnAdvisor build() {
			if ((this.compactionTrigger == null) != (this.compactionStrategy == null)) {
				throw new IllegalArgumentException(
						"compactionTrigger and compactionStrategy must be set together — set both or neither");
			}
			return new SessionTurnAdvisor(this);
		}

	}

}
