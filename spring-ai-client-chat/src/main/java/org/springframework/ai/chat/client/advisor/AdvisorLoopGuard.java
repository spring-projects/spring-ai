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

package org.springframework.ai.chat.client.advisor;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.util.Assert;

/**
 * Strategy for detecting and breaking out of infinite loops in a recursive advisor, such
 * as a tool-calling loop or a structured-output validation retry loop. A guard governs a
 * single advisor execution: one non-streaming call or one stream subscription.
 * <p>
 * {@link #begin()} creates a fresh {@link LoopState} for every loop, so any state an
 * implementation accumulates (e.g. per-round counts) is isolated to a single conversation
 * turn. Before acting on each round (e.g. before executing tool calls), the advisor calls
 * {@link LoopState#check(ChatResponse)}, which returns a {@link Decision} to
 * {@link Decision.Action#CONTINUE continue}, {@link Decision.Action#STOP stop gracefully}
 * (returning the latest response without acting on the round), or
 * {@link Decision.Action#ERROR abort with an error}.
 * <p>
 * {@link MaxIdenticalToolCallLoopGuard} is the default, tool-call-oriented
 * implementation. Provide a custom implementation for other policies, e.g. a maximum
 * round count or a time budget.
 *
 * @author Christian Tzolov
 * @since 2.0.0
 * @see MaxIdenticalToolCallLoopGuard
 */
@FunctionalInterface
public interface AdvisorLoopGuard {

	/**
	 * Begins a new, isolated guard state for a single recursive advisor loop.
	 * @return a stateful {@link LoopState} bound to one loop
	 */
	LoopState begin();

	/**
	 * Stateful, single-loop view of an {@link AdvisorLoopGuard}. Implementations inspect
	 * each round's response and return a {@link Decision} that controls whether the loop
	 * continues, stops gracefully or aborts with an error.
	 */
	@FunctionalInterface
	interface LoopState {

		/**
		 * Inspects the given response for the current loop and decides how the loop
		 * should proceed.
		 * @param chatResponse the aggregated model response for the round about to be
		 * acted upon
		 * @return the {@link Decision} describing how the loop should proceed
		 */
		Decision check(ChatResponse chatResponse);

	}

	/**
	 * The outcome of a {@link LoopState#check(ChatResponse)} invocation.
	 */
	final class Decision {

		/**
		 * The action the recursive advisor should take for the current round.
		 */
		public enum Action {

			/**
			 * Proceed to act on the current round (e.g. execute its tool calls).
			 */
			CONTINUE,

			/**
			 * Stop the loop gracefully without acting on the current round, returning the
			 * latest response to the caller.
			 */
			STOP,

			/**
			 * Abort the loop by failing the request.
			 */
			ERROR

		}

		private static final Decision CONTINUE = new Decision(Action.CONTINUE, null);

		private static final Decision STOP = new Decision(Action.STOP, null);

		private final Action action;

		@Nullable private final String message;

		private Decision(Action action, @Nullable String message) {
			this.action = action;
			this.message = message;
		}

		/**
		 * Returns a decision to continue acting on the current round.
		 * @return a {@link Action#CONTINUE} decision
		 */
		public static Decision continueLoop() {
			return CONTINUE;
		}

		/**
		 * Returns a decision to stop the loop gracefully, returning the latest response
		 * to the caller without acting on the current round.
		 * @return a {@link Action#STOP} decision
		 */
		public static Decision stop() {
			return STOP;
		}

		/**
		 * Returns a decision to stop the loop gracefully with an explanatory message.
		 * @param message the reason the loop is being stopped
		 * @return a {@link Action#STOP} decision carrying the given message
		 */
		public static Decision stop(String message) {
			Assert.hasText(message, "message must not be empty");
			return new Decision(Action.STOP, message);
		}

		/**
		 * Returns a decision to abort the loop by failing the request.
		 * @param message the error message describing why the loop was aborted
		 * @return an {@link Action#ERROR} decision carrying the given message
		 */
		public static Decision error(String message) {
			Assert.hasText(message, "message must not be empty");
			return new Decision(Action.ERROR, message);
		}

		/**
		 * Returns the action the advisor should take.
		 * @return the {@link Action}
		 */
		public Action action() {
			return this.action;
		}

		/**
		 * Returns the message associated with this decision, if any.
		 * @return the message, or {@code null} for a plain {@link Action#CONTINUE} or
		 * {@link Action#STOP} decision
		 */
		@Nullable public String message() {
			return this.message;
		}

	}

}
