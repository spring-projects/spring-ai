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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.util.Assert;

/**
 * Tracks token consumption per session and enforces an optional per-session token budget.
 *
 * <p>
 * Token counts are incremented by calling {@link #recordUsage(String, ChatResponse)}
 * after every model response. The total consumed tokens for a session can be retrieved
 * with {@link #getTokenCount(String)}. Sessions can be cleared with
 * {@link #clearSession(String)}.
 *
 * <p>
 * When a {@code tokenLimit} greater than zero is configured, a call to
 * {@link #checkBudget(String)} throws {@link BudgetExceededException} if the accumulated
 * token count for that session has reached or exceeded the limit.
 *
 * @author Spring AI Contributors
 * @since 2.0.0
 */
public final class SessionCostTracker {

	private static final Log logger = LogFactory.getLog(SessionCostTracker.class);

	/**
	 * Sentinel value meaning "no budget limit is enforced".
	 */
	public static final long UNLIMITED = 0L;

	private final long tokenLimit;

	private final ConcurrentHashMap<String, LongAdder> sessionTokens = new ConcurrentHashMap<>();

	/**
	 * Creates a tracker with no token limit.
	 */
	public SessionCostTracker() {
		this(UNLIMITED);
	}

	/**
	 * Creates a tracker with the given per-session token limit.
	 * @param tokenLimit maximum tokens allowed per session; use {@link #UNLIMITED} (or
	 * any value {@code <= 0}) to disable enforcement
	 */
	public SessionCostTracker(long tokenLimit) {
		this.tokenLimit = tokenLimit;
	}

	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------

	/**
	 * Records token usage reported in {@code chatResponse} for {@code sessionId}.
	 * <p>
	 * If the response contains no usage metadata the call is a no-op.
	 * @param sessionId the session to charge; must not be {@code null}
	 * @param chatResponse the response from the model; may be {@code null}
	 */
	public void recordUsage(String sessionId, ChatResponse chatResponse) {
		Assert.hasText(sessionId, "sessionId must not be blank");
		if (chatResponse == null || chatResponse.getMetadata() == null) {
			return;
		}
		Usage usage = chatResponse.getMetadata().getUsage();
		if (usage == null) {
			return;
		}
		long tokens = safeTokenCount(usage);
		if (tokens > 0) {
			this.sessionTokens.computeIfAbsent(sessionId, id -> new LongAdder()).add(tokens);
			if (logger.isDebugEnabled()) {
				logger.debug("Session '" + sessionId + "' consumed " + tokens + " tokens (total: "
						+ this.sessionTokens.get(sessionId).sum() + ")");
			}
		}
	}

	/**
	 * Returns the total number of tokens consumed by {@code sessionId} so far.
	 * @param sessionId the session identifier; must not be {@code null}
	 * @return token count; {@code 0} if no usage has been recorded
	 */
	public long getTokenCount(String sessionId) {
		Assert.hasText(sessionId, "sessionId must not be blank");
		LongAdder adder = this.sessionTokens.get(sessionId);
		return adder != null ? adder.sum() : 0L;
	}

	/**
	 * Checks whether {@code sessionId} has exceeded its token budget and throws
	 * {@link BudgetExceededException} if it has.
	 * <p>
	 * When {@link #tokenLimit} is {@link #UNLIMITED} this is always a no-op.
	 * @param sessionId the session to check; must not be {@code null}
	 * @throws BudgetExceededException when the session token count &ge; the configured
	 * limit
	 */
	public void checkBudget(String sessionId) {
		Assert.hasText(sessionId, "sessionId must not be blank");
		if (this.tokenLimit <= UNLIMITED) {
			return;
		}
		long current = getTokenCount(sessionId);
		if (current >= this.tokenLimit) {
			throw new BudgetExceededException(sessionId, this.tokenLimit, current);
		}
	}

	/**
	 * Removes all recorded token usage for {@code sessionId}.
	 * @param sessionId the session to clear; must not be {@code null}
	 */
	public void clearSession(String sessionId) {
		Assert.hasText(sessionId, "sessionId must not be blank");
		this.sessionTokens.remove(sessionId);
	}

	/**
	 * Returns whether a token limit is configured.
	 */
	public boolean hasLimit() {
		return this.tokenLimit > UNLIMITED;
	}

	/**
	 * Returns the configured token limit, or {@link #UNLIMITED} if none is set.
	 */
	public long getTokenLimit() {
		return this.tokenLimit;
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private static long safeTokenCount(Usage usage) {
		// Prefer total tokens; fall back to prompt + completion sum when total is absent.
		try {
			Integer total = usage.getTotalTokens();
			if (total != null && total > 0) {
				return total.longValue();
			}
			long prompt = usage.getPromptTokens() != null ? usage.getPromptTokens().longValue() : 0L;
			long completion = usage.getCompletionTokens() != null ? usage.getCompletionTokens().longValue() : 0L;
			return prompt + completion;
		}
		catch (UnsupportedOperationException ex) {
			return 0L;
		}
	}

}
