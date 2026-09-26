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

import java.util.function.Predicate;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import reactor.util.context.ContextView;

/**
 * Propagates the ThreadLocal state of the accessors registered in Micrometer's
 * {@link ContextRegistry} across the thread boundary used for streaming tool execution.
 * <p>
 * {@link ToolCallingAdvisor} executes blocking tool calls on a
 * {@code Schedulers.boundedElastic()} worker, so ThreadLocal state set on the thread that
 * subscribes to the stream would otherwise be invisible to the tools. The state of the
 * subscribing thread is captured into the Reactor context, and restored on the thread
 * executing the tool call. ThreadLocals that were not captured are cleared while the tool
 * executes, so a reused worker thread cannot leak state from a previous request.
 * <p>
 * Only the accessors already present in Micrometer's {@link ContextRegistry} take part;
 * this class registers none of them. Accessor registration is handled by the
 * corresponding integration layer.
 * <p>
 * Observation state is deliberately excluded because Spring AI propagates the observation
 * hierarchy explicitly through Reactor context.
 *
 * @author luyu0425
 */
final class ToolCallContextPropagation {

	/**
	 * Selects the ThreadLocal keys this bridge manages. Observation is excluded on both
	 * the capture and the restore side so that the {@code clearMissing} behavior below
	 * never clears the observation ThreadLocal, which Spring AI propagates separately.
	 */
	private static final Predicate<Object> CONTEXT_KEY_PREDICATE = key -> !ObservationThreadLocalAccessor.KEY
		.equals(key);

	private static final ContextSnapshotFactory SNAPSHOT_FACTORY = ContextSnapshotFactory.builder()
		.contextRegistry(ContextRegistry.getInstance())
		.captureKeyPredicate(CONTEXT_KEY_PREDICATE)
		.clearMissing(true)
		.build();

	private ToolCallContextPropagation() {
	}

	/**
	 * Captures the ThreadLocal state currently set on the calling thread.
	 * @return the captured state, to be written into the Reactor context
	 */
	static ContextSnapshot captureThreadLocals() {
		return SNAPSHOT_FACTORY.captureAll();
	}

	/**
	 * Restores the ThreadLocal state carried by the given Reactor context on the calling
	 * thread, clearing any matching ThreadLocal that the context does not carry.
	 * @param contextView the Reactor context holding the captured state
	 * @return a scope that restores the previous ThreadLocal state when closed
	 */
	static ContextSnapshot.Scope restoreThreadLocals(ContextView contextView) {
		return SNAPSHOT_FACTORY.captureFrom(contextView).setThreadLocals(CONTEXT_KEY_PREDICATE);
	}

}
