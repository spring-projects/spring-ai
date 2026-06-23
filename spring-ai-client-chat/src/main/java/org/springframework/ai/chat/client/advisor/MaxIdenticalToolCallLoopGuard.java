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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.util.Assert;

/**
 * {@link AdvisorLoopGuard} implementation that breaks a tool-calling loop when a tool is
 * repeatedly invoked with identical arguments.
 * <p>
 * Within a single loop the guard tracks each distinct {@code (tool name, arguments)}
 * combination and returns an {@link AdvisorLoopGuard.Decision.Action#ERROR error}
 * decision once the same combination is seen more than
 * {@link #getMaxIdenticalToolCallCount()} times. This prevents infinite loops where a
 * model keeps calling the same tool with the same arguments after receiving an error or
 * otherwise unhelpful response.
 * <p>
 * Because the count accumulates across the entire loop within a single conversation turn,
 * a model that legitimately re-invokes an idempotent tool with identical arguments (e.g.
 * polling a {@code get_status} or {@code list_files} tool) more than
 * {@link #getMaxIdenticalToolCallCount()} times could trip the guard. Such tools can be
 * exempted from the check via {@link Builder#excludedToolNames(java.util.Collection)};
 * alternatively, increase the limit via {@link Builder#maxIdenticalToolCallCount(int)}.
 *
 * @author Christian Tzolov
 * @since 2.0.0
 */
public class MaxIdenticalToolCallLoopGuard implements AdvisorLoopGuard {

	/**
	 * Default maximum number of times a tool may be called with identical arguments
	 * within a single advisor loop. A value of 15 is tolerant of legitimate repeats (e.g.
	 * a model polling an idempotent tool or retrying after transient errors) while still
	 * breaking infinite loops where the model never changes the tool arguments. Since the
	 * guard keys on the tool name <em>and</em> identical arguments, seeing the same call
	 * 15 times is already a strong infinite-loop signal; the limit mainly governs how
	 * many identical retries are tolerated before aborting.
	 */
	public static final int DEFAULT_MAX_IDENTICAL_TOOL_CALL_COUNT = 15;

	private final int maxIdenticalToolCallCount;

	private final Set<String> excludedToolNames;

	/**
	 * Creates a guard with the given limit and excluded tool names.
	 * @param maxIdenticalToolCallCount the maximum number of identical tool calls allowed
	 * within a single loop (must be positive)
	 * @param excludedToolNames the names of tools that are exempt from the identical
	 * tool-call check (must not be null)
	 */
	protected MaxIdenticalToolCallLoopGuard(int maxIdenticalToolCallCount, Collection<String> excludedToolNames) {
		Assert.isTrue(maxIdenticalToolCallCount > 0, "maxIdenticalToolCallCount must be positive");
		Assert.notNull(excludedToolNames, "excludedToolNames must not be null");
		this.maxIdenticalToolCallCount = maxIdenticalToolCallCount;
		this.excludedToolNames = new HashSet<>(excludedToolNames);
	}

	/**
	 * Returns the configured maximum number of identical tool calls allowed within a
	 * single loop.
	 * @return the maximum identical tool call count
	 */
	public int getMaxIdenticalToolCallCount() {
		return this.maxIdenticalToolCallCount;
	}

	/**
	 * Returns an unmodifiable view of the tool names that are exempt from the identical
	 * tool-call check.
	 * @return the excluded tool names
	 */
	public Set<String> getExcludedToolNames() {
		return Set.copyOf(this.excludedToolNames);
	}

	@Override
	public LoopState begin() {
		// Loop-local tracker so identical-call detection is isolated to a single loop and
		// never shared across requests or subscriptions.
		Map<String, Integer> seenToolCalls = new HashMap<>();
		return chatResponse -> checkForRepeatedToolCalls(chatResponse, seenToolCalls);
	}

	/**
	 * Inspects the tool calls in the given response, updating the per-loop invocation
	 * counts and returning an {@link Decision.Action#ERROR error} decision once any
	 * {@code (tool name, arguments)} combination exceeds
	 * {@link #getMaxIdenticalToolCallCount()}. Otherwise returns a
	 * {@link Decision.Action#CONTINUE continue} decision.
	 * <p>
	 * Subclasses may override this method to customize how repeated tool calls are keyed,
	 * counted or reported.
	 * @param chatResponse the aggregated model response containing the tool calls about
	 * to be executed in this round
	 * @param seenToolCalls the loop-local map tracking how many times each
	 * {@code (tool name, arguments)} combination has been seen
	 * @return the {@link Decision} describing how the loop should proceed
	 */
	protected Decision checkForRepeatedToolCalls(ChatResponse chatResponse, Map<String, Integer> seenToolCalls) {
		return chatResponse.getResults()
			.stream()
			.filter(g -> g.getOutput().getToolCalls() != null && !g.getOutput().getToolCalls().isEmpty())
			.flatMap(g -> g.getOutput().getToolCalls().stream())
			.filter(toolCall -> !this.excludedToolNames.contains(toolCall.name()))
			.map(toolCall -> {
				String args = toolCall.arguments() != null ? toolCall.arguments() : "";
				String key = toolCall.name() + "::" + args;
				int count = seenToolCalls.merge(key, 1, Integer::sum);
				if (count > this.maxIdenticalToolCallCount) {
					return Decision.error("Identical tool call detected: tool '" + toolCall.name() + "' was called "
							+ count
							+ " time(s) with identical arguments. This may indicate an infinite tool-call loop. "
							+ "Adjust the tool response to guide the model, or increase maxIdenticalToolCallCount.");
				}
				return Decision.continueLoop();
			})
			.filter(decision -> decision.action() == Decision.Action.ERROR)
			.findFirst()
			.orElseGet(Decision::continueLoop);
	}

	/**
	 * Creates a new Builder instance for constructing a
	 * {@link MaxIdenticalToolCallLoopGuard}.
	 * @return a new Builder instance
	 */
	public static Builder<?> builder() {
		return new Builder<>();
	}

	/**
	 * Builder for creating instances of {@link MaxIdenticalToolCallLoopGuard}.
	 * <p>
	 * This builder uses the self-referential generic pattern to support extensibility, so
	 * that subclasses can add their own configuration while preserving fluent method
	 * chaining.
	 *
	 * @param <T> the builder type, used for self-referential generics to support method
	 * chaining in subclasses
	 */
	public static class Builder<T extends Builder<T>> {

		private int maxIdenticalToolCallCount = DEFAULT_MAX_IDENTICAL_TOOL_CALL_COUNT;

		private final Set<String> excludedToolNames = new HashSet<>();

		protected Builder() {
		}

		/**
		 * Returns this builder cast to the appropriate type for method chaining.
		 * Subclasses should override this method to return the correct type.
		 * @return this builder instance
		 */
		@SuppressWarnings("unchecked")
		protected T self() {
			return (T) this;
		}

		/**
		 * Sets the maximum number of times a tool may be called with identical arguments
		 * within a single loop. Defaults to
		 * {@link #DEFAULT_MAX_IDENTICAL_TOOL_CALL_COUNT}.
		 * @param maxIdenticalToolCallCount the maximum count (must be positive)
		 * @return this Builder instance for method chaining
		 */
		public T maxIdenticalToolCallCount(int maxIdenticalToolCallCount) {
			this.maxIdenticalToolCallCount = maxIdenticalToolCallCount;
			return self();
		}

		/**
		 * Sets the names of tools that are exempt from the identical tool-call check.
		 * Replaces any previously configured names. Useful for idempotent tools that a
		 * model may legitimately poll several times with identical arguments within a
		 * single turn (e.g. {@code get_status}, {@code list_files}). Empty by default.
		 * @param excludedToolNames the tool names to exclude (must not be null)
		 * @return this Builder instance for method chaining
		 */
		public T excludedToolNames(Collection<String> excludedToolNames) {
			Assert.notNull(excludedToolNames, "excludedToolNames must not be null");
			this.excludedToolNames.clear();
			this.excludedToolNames.addAll(excludedToolNames);
			return self();
		}

		/**
		 * Adds the given tool names to the set exempt from the identical tool-call check.
		 * @param toolNames the tool names to exclude (must not be null)
		 * @return this Builder instance for method chaining
		 */
		public T excludeToolNames(String... toolNames) {
			Assert.notNull(toolNames, "toolNames must not be null");
			this.excludedToolNames.addAll(Set.of(toolNames));
			return self();
		}

		/**
		 * Returns the configured maximum identical tool call count.
		 * @return the maximum identical tool call count
		 */
		protected int getMaxIdenticalToolCallCount() {
			return this.maxIdenticalToolCallCount;
		}

		/**
		 * Returns the configured excluded tool names.
		 * @return the excluded tool names
		 */
		protected Set<String> getExcludedToolNames() {
			return this.excludedToolNames;
		}

		/**
		 * Builds and returns a new {@link MaxIdenticalToolCallLoopGuard} instance with
		 * the configured properties.
		 * @return a new {@link MaxIdenticalToolCallLoopGuard} instance
		 * @throws IllegalArgumentException if {@code maxIdenticalToolCallCount} is not
		 * positive
		 */
		public MaxIdenticalToolCallLoopGuard build() {
			return new MaxIdenticalToolCallLoopGuard(this.maxIdenticalToolCallCount, this.excludedToolNames);
		}

	}

}
