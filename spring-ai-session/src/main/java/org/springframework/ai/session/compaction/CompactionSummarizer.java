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

import org.springframework.ai.chat.messages.Message;

/**
 * Writes the summary that stands in for the events a {@link WindowCompactionStrategy}
 * covers. The strategy decides the cut; the summarizer only condenses what leaves the
 * view into the messages of a synthetic summary turn, typically a user "shadow prompt"
 * and an assistant summary, so the model keeps seeing a user/assistant alternation.
 *
 * <p>
 * A summarizer that returns no messages declines: the view stays as it is and nothing is
 * written, so a failed model call never loses history.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
@FunctionalInterface
public interface CompactionSummarizer {

	/**
	 * Summarizes the events that leave the view.
	 * @param request what to summarize, with the prior summary to build on and the kept
	 * events for continuity
	 * @return the messages of the summary turn, in order; empty to leave the view
	 * unchanged
	 */
	List<Message> summarize(SummaryRequest request);

}
