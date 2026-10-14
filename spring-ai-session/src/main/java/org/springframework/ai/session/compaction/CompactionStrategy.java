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

package org.springframework.ai.session.compaction;

/**
 * Strategy for compacting a session's active view to manage context window size.
 *
 * <p>
 * A strategy is policy, not storage: given the active view it decides which events leave
 * the view (covered), which stay at the front (pinned), which recent events are kept
 * verbatim (preserved), and what summary stands in for the covered ones. It never writes.
 * {@link org.springframework.ai.session.SessionService#compact} records the decision by
 * appending a {@link CompactionBoundary} event and the summary events; the log itself is
 * never changed.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
@FunctionalInterface
public interface CompactionStrategy {

	/**
	 * Decides how to compact the given view.
	 * @param request the session, its active view and counts
	 * @return the decision; {@link CompactionResult#unchanged()} when nothing should
	 * leave the view
	 */
	CompactionResult compact(CompactionRequest request);

}
