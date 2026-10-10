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

package org.springframework.ai.chat.client.advisor.governance;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;

import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.util.Assert;

/**
 * A governance advisor that intercepts every {@link ChatClientRequest} and applies
 * configurable policy checks before forwarding the request to the rest of the advisor
 * chain.
 *
 * <p>
 * <strong>Checks performed (in order):</strong>
 * <ol>
 * <li><em>Session budget</em> — verifies that the session has not exceeded its token
 * budget (requires a non-{@code null} session ID in the request context under
 * {@value #DEFAULT_SESSION_ID_CONTEXT_KEY}).</li>
 * <li><em>PII scan</em> — inspects the full prompt text for personally identifiable
 * information using {@link PiiScanner}.</li>
 * </ol>
 *
 * <p>
 * Each check produces a {@link GovernanceDecision} that is forwarded to all registered
 * {@link #auditListeners}. The behaviour when a violation is detected is controlled by
 * {@link GovernanceMode}:
 * <ul>
 * <li>{@link GovernanceMode#OBSERVE} — log only; the request continues.</li>
 * <li>{@link GovernanceMode#MONITOR} — log + emit audit event; the request
 * continues.</li>
 * <li>{@link GovernanceMode#ENFORCE} — throw {@link GovernanceViolationException}; the
 * request is blocked.</li>
 * </ul>
 *
 * <p>
 * After a successful model call the advisor records the response token usage against the
 * session via {@link SessionCostTracker#recordUsage}.
 *
 * @author Spring AI Contributors
 * @since 2.0.0
 * @see GovernanceMode
 * @see GovernanceDecision
 * @see PiiScanner
 * @see SessionCostTracker
 */
public final class GovernanceAdvisor implements CallAdvisor, StreamAdvisor {

	private static final Log logger = LogFactory.getLog(GovernanceAdvisor.class);

	/**
	 * Default key used to look up the session / conversation identifier from the request
	 * context.
	 */
	public static final String DEFAULT_SESSION_ID_CONTEXT_KEY = "conversationId";

	/**
	 * Context key under which the latest {@link GovernanceDecision} is stored in the
	 * response context so downstream code can inspect it.
	 */
	public static final String GOVERNANCE_DECISION_CONTEXT_KEY = GovernanceDecision.class.getName();

	private static final int DEFAULT_ORDER = 0;

	private final GovernanceMode mode;

	private final PiiScanner piiScanner;

	private final boolean piiEnabled;

	private final SessionCostTracker costTracker;

	private final boolean budgetEnabled;

	private final String sessionIdContextKey;

	private final List<Consumer<GovernanceDecision>> auditListeners;

	private final int order;

	private final Scheduler scheduler;

	// -------------------------------------------------------------------------
	// Constructor (package-private — use Builder)
	// -------------------------------------------------------------------------

	private GovernanceAdvisor(GovernanceMode mode, PiiScanner piiScanner, boolean piiEnabled,
			SessionCostTracker costTracker, boolean budgetEnabled, String sessionIdContextKey,
			List<Consumer<GovernanceDecision>> auditListeners, int order, Scheduler scheduler) {
		this.mode = mode;
		this.piiScanner = piiScanner;
		this.piiEnabled = piiEnabled;
		this.costTracker = costTracker;
		this.budgetEnabled = budgetEnabled;
		this.sessionIdContextKey = sessionIdContextKey;
		this.auditListeners = List.copyOf(auditListeners);
		this.order = order;
		this.scheduler = scheduler;
	}

	// -------------------------------------------------------------------------
	// Advisor identity
	// -------------------------------------------------------------------------

	@Override
	public String getName() {
		return this.getClass().getSimpleName();
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	// -------------------------------------------------------------------------
	// CallAdvisor
	// -------------------------------------------------------------------------

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
		GovernanceDecision decision = evaluate(chatClientRequest);
		if (decision.isDenied() && this.mode == GovernanceMode.ENFORCE) {
			throw new GovernanceViolationException(decision);
		}

		ChatClientResponse response = callAdvisorChain.nextCall(chatClientRequest);

		// Record token usage for the session after a successful call.
		recordUsageIfPresent(chatClientRequest, response.chatResponse());

		return response.mutate().context(GOVERNANCE_DECISION_CONTEXT_KEY, decision).build();
	}

	// -------------------------------------------------------------------------
	// StreamAdvisor
	// -------------------------------------------------------------------------

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
			StreamAdvisorChain streamAdvisorChain) {

		GovernanceDecision decision = evaluate(chatClientRequest);
		if (decision.isDenied() && this.mode == GovernanceMode.ENFORCE) {
			return Flux.error(new GovernanceViolationException(decision));
		}

		Flux<ChatClientResponse> responseFlux = streamAdvisorChain.nextStream(chatClientRequest);

		// Aggregate to record usage after the stream completes.
		return new ChatClientMessageAggregator()
			.aggregateChatClientResponse(responseFlux,
					aggregated -> recordUsageIfPresent(chatClientRequest, aggregated.chatResponse()))
			.map(r -> r.mutate().context(GOVERNANCE_DECISION_CONTEXT_KEY, decision).build());
	}

	// -------------------------------------------------------------------------
	// Evaluation logic
	// -------------------------------------------------------------------------

	/**
	 * Runs all enabled governance checks against {@code request} and returns a
	 * {@link GovernanceDecision}. Also notifies audit listeners and logs accordingly.
	 */
	private GovernanceDecision evaluate(ChatClientRequest request) {
		long start = System.currentTimeMillis();
		String correlationId = UUID.randomUUID().toString();

		// 1. Budget check
		if (this.budgetEnabled && this.costTracker.hasLimit()) {
			String sessionId = resolveSessionId(request);
			if (sessionId != null) {
				try {
					this.costTracker.checkBudget(sessionId);
				}
				catch (BudgetExceededException ex) {
					return makeDecision(correlationId, GovernanceDecision.ACTION_DENY,
							"Token budget exceeded for session '" + sessionId + "'", 1.0, start);
				}
			}
		}

		// 2. PII scan
		if (this.piiEnabled) {
			String promptText = request.prompt().getContents();
			String piiType = this.piiScanner.firstMatchDescription(promptText);
			if (piiType != null) {
				return makeDecision(correlationId, GovernanceDecision.ACTION_DENY,
						"PII detected in prompt (" + piiType + ")", 0.9, start);
			}
		}

		// All checks passed
		return makeDecision(correlationId, GovernanceDecision.ACTION_ALLOW, "All governance checks passed", 0.0, start);
	}

	private GovernanceDecision makeDecision(String correlationId, String action, String reason, double riskScore,
			long startMs) {
		long elapsed = System.currentTimeMillis() - startMs;
		GovernanceDecision decision = new GovernanceDecision(correlationId, action, reason, riskScore, elapsed);

		notifyAuditListeners(decision);
		logDecision(decision);

		return decision;
	}

	private void notifyAuditListeners(GovernanceDecision decision) {
		for (Consumer<GovernanceDecision> listener : this.auditListeners) {
			try {
				listener.accept(decision);
			}
			catch (Exception ex) {
				logger.warn("Audit listener threw an exception for decision " + decision.correlationId(), ex);
			}
		}
	}

	private void logDecision(GovernanceDecision decision) {
		if (decision.isDenied()) {
			if (logger.isWarnEnabled()) {
				logger.warn("Governance [" + this.mode + "] DENY — " + decision.reason() + " (correlationId="
						+ decision.correlationId() + ", riskScore=" + decision.riskScore() + ")");
			}
		}
		else {
			if (logger.isDebugEnabled()) {
				logger.debug("Governance [" + this.mode + "] ALLOW — " + decision.reason() + " (correlationId="
						+ decision.correlationId() + ")");
			}
		}
	}

	private void recordUsageIfPresent(ChatClientRequest request, @Nullable ChatResponse chatResponse) {
		if (!this.budgetEnabled || chatResponse == null) {
			return;
		}
		String sessionId = resolveSessionId(request);
		if (sessionId != null) {
			this.costTracker.recordUsage(sessionId, chatResponse);
		}
	}

	private @Nullable String resolveSessionId(ChatClientRequest request) {
		Object value = request.context().get(this.sessionIdContextKey);
		return value != null ? value.toString() : null;
	}

	// -------------------------------------------------------------------------
	// Builder
	// -------------------------------------------------------------------------

	/**
	 * Returns a new {@link Builder} with sensible defaults: {@link GovernanceMode#ENFORCE
	 * ENFORCE} mode, PII scanning enabled, no token budget.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for {@link GovernanceAdvisor}.
	 */
	public static final class Builder {

		private GovernanceMode mode = GovernanceMode.ENFORCE;

		private PiiScanner piiScanner = PiiScanner.builder().build();

		private boolean piiEnabled = true;

		private SessionCostTracker costTracker = new SessionCostTracker();

		private boolean budgetEnabled = true;

		private String sessionIdContextKey = DEFAULT_SESSION_ID_CONTEXT_KEY;

		private final List<Consumer<GovernanceDecision>> auditListeners = new ArrayList<>();

		private int order = DEFAULT_ORDER;

		private Scheduler scheduler = BaseAdvisor.DEFAULT_SCHEDULER;

		private Builder() {
		}

		/**
		 * Sets the governance mode (default: {@link GovernanceMode#ENFORCE}).
		 */
		public Builder mode(GovernanceMode mode) {
			Assert.notNull(mode, "mode must not be null");
			this.mode = mode;
			return this;
		}

		/**
		 * Replaces the default {@link PiiScanner} with a custom one.
		 */
		public Builder piiScanner(PiiScanner piiScanner) {
			Assert.notNull(piiScanner, "piiScanner must not be null");
			this.piiScanner = piiScanner;
			return this;
		}

		/**
		 * Enables or disables PII scanning (default: {@code true}).
		 */
		public Builder piiEnabled(boolean piiEnabled) {
			this.piiEnabled = piiEnabled;
			return this;
		}

		/**
		 * Replaces the default {@link SessionCostTracker} with a custom one.
		 */
		public Builder costTracker(SessionCostTracker costTracker) {
			Assert.notNull(costTracker, "costTracker must not be null");
			this.costTracker = costTracker;
			return this;
		}

		/**
		 * Enables or disables budget enforcement (default: {@code true}).
		 * <p>
		 * When disabled the {@link SessionCostTracker} is never consulted even if a token
		 * limit is configured.
		 */
		public Builder budgetEnabled(boolean budgetEnabled) {
			this.budgetEnabled = budgetEnabled;
			return this;
		}

		/**
		 * Sets the context key used to look up the session identifier (default:
		 * {@value #DEFAULT_SESSION_ID_CONTEXT_KEY}).
		 */
		public Builder sessionIdContextKey(String sessionIdContextKey) {
			Assert.hasText(sessionIdContextKey, "sessionIdContextKey must not be blank");
			this.sessionIdContextKey = sessionIdContextKey;
			return this;
		}

		/**
		 * Adds an audit listener that is called with every {@link GovernanceDecision}
		 * produced.
		 */
		public Builder auditListener(Consumer<GovernanceDecision> listener) {
			Assert.notNull(listener, "listener must not be null");
			this.auditListeners.add(listener);
			return this;
		}

		/**
		 * Sets the advisor order (default: {@code 0}).
		 */
		public Builder order(int order) {
			this.order = order;
			return this;
		}

		/**
		 * Sets the {@link Scheduler} used for stream processing (default:
		 * {@link BaseAdvisor#DEFAULT_SCHEDULER}).
		 */
		public Builder scheduler(Scheduler scheduler) {
			Assert.notNull(scheduler, "scheduler must not be null");
			this.scheduler = scheduler;
			return this;
		}

		/**
		 * Builds the {@link GovernanceAdvisor}.
		 */
		public GovernanceAdvisor build() {
			return new GovernanceAdvisor(this.mode, this.piiScanner, this.piiEnabled, this.costTracker,
					this.budgetEnabled, this.sessionIdContextKey, this.auditListeners, this.order, this.scheduler);
		}

	}

}
