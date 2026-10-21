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

import java.util.List;

import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionEvent;
import org.springframework.util.Assert;

/**
 * What a {@link CompactionSummarizer} is asked to summarize.
 *
 * @param session the session being compacted
 * @param priorSummary the synthetic events of the summary a previous pass left, oldest
 * first, which the new summary replaces and should build on; empty before the first
 * summary
 * @param covered the conversation events that leave the view, in log order: never a
 * stored system message, which compaction pins or supersedes but does not summarize
 * @param preserved the conversation events that stay in the view after the cut, in log
 * order, for continuity: a summarizer may quote the first few, never summarize them
 * @author Christian Tzolov
 * @since 2.1.0
 */
public record SummaryRequest(Session session, List<SessionEvent> priorSummary, List<SessionEvent> covered,
		List<SessionEvent> preserved) {

	public SummaryRequest {
		Assert.notNull(session, "session must not be null");
		Assert.notNull(priorSummary, "priorSummary must not be null");
		Assert.notEmpty(covered, "covered must not be empty");
		Assert.notNull(preserved, "preserved must not be null");
		priorSummary = List.copyOf(priorSummary);
		covered = List.copyOf(covered);
		preserved = List.copyOf(preserved);
	}

}
