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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.session.EventType;
import org.springframework.ai.session.SessionEvent;
import org.springframework.util.Assert;

/**
 * What a {@link CompactionStrategy} decided for the active view it was given: which
 * events leave the view, which are kept, and the summary that stands in for the ones that
 * leave.
 *
 * <p>
 * The three event lists partition the view the strategy received: every event of the view
 * is covered, pinned or preserved, and none is in two lists. Nothing here changes the
 * log. {@link org.springframework.ai.session.SessionService#compact} turns a result into
 * one appended {@link CompactionBoundary} event plus the summary events; the boundary
 * then defines the view (see {@link CompactionBoundary#project(List)}).
 *
 * @param coveredEvents the events that leave the view, in view order: summarized by the
 * summary, or simply dropped by a window. Empty for an unchanged result
 * @param pinnedEvents the events kept at the front of the view, out of log order: the
 * latest stored system message, and for a plain window the prior summary it carries
 * forward
 * @param preservedEvents the recent events kept verbatim, in log order
 * @param summary the messages that stand in for the covered events, in order (typically a
 * synthetic shadow prompt and a summary); empty for a plain window
 * @param tokensEstimatedSaved the estimated tokens removed from the prompt, net of the
 * summary
 * @param strategy the name of the strategy, recorded on the boundary and the summary
 * events
 * @author Christian Tzolov
 * @since 2.1.0
 */
public record CompactionResult(List<SessionEvent> coveredEvents, List<SessionEvent> pinnedEvents,
		List<SessionEvent> preservedEvents, List<Message> summary, int tokensEstimatedSaved, String strategy) {

	private static final CompactionResult UNCHANGED = new CompactionResult(List.of(), List.of(), List.of(), List.of(),
			0, "");

	public CompactionResult {
		Assert.notNull(coveredEvents, "coveredEvents must not be null");
		Assert.notNull(pinnedEvents, "pinnedEvents must not be null");
		Assert.notNull(preservedEvents, "preservedEvents must not be null");
		Assert.notNull(summary, "summary must not be null");
		Assert.notNull(strategy, "strategy must not be null");
		Assert.isTrue(summary.isEmpty() || !coveredEvents.isEmpty(), "a summary needs covered events to stand in for");
		coveredEvents = List.copyOf(coveredEvents);
		pinnedEvents = List.copyOf(pinnedEvents);
		preservedEvents = List.copyOf(preservedEvents);
		summary = List.copyOf(summary);
	}

	/** The result of a pass that leaves the view as it is: nothing is written. */
	public static CompactionResult unchanged() {
		return UNCHANGED;
	}

	/** Whether no event leaves the view, so there is nothing to record. */
	public boolean isUnchanged() {
		return this.coveredEvents.isEmpty();
	}

	/** The number of events that leave the view. */
	public int coveredEventCount() {
		return this.coveredEvents.size();
	}

	/**
	 * The events {@link org.springframework.ai.session.SessionService#compact} appends
	 * for this result: the {@link org.springframework.ai.session.EventType#COMPACTION}
	 * boundary, then one synthetic event per summary message, all with one timestamp. The
	 * boundary names the pinned, summary and preserved events by id, so the view is
	 * rebuilt from the log alone; see {@link CompactionBoundary#project(List)}.
	 * @param sessionId the session the events belong to
	 * @return the events to append, in order; empty for an unchanged result
	 */
	public List<SessionEvent> toEvents(String sessionId) {
		Assert.hasText(sessionId, "sessionId must not be null or empty");
		if (isUnchanged()) {
			return List.of();
		}
		String boundaryId = UUID.randomUUID().toString();
		Instant now = Instant.now();
		List<SessionEvent> summaryEvents = this.summary.stream()
			.map(message -> SessionEvent.builder()
				.sessionId(sessionId)
				.timestamp(now)
				.message(message)
				.metadata(SessionEvent.METADATA_SYNTHETIC, true)
				.metadata(SessionEvent.METADATA_COMPACTION_SOURCE, this.strategy)
				.metadata(SessionEvent.METADATA_COMPACTION_ID, boundaryId)
				.build())
			.toList();
		CompactionBoundary boundary = new CompactionBoundary(boundaryId, ids(this.pinnedEvents), ids(summaryEvents),
				ids(this.preservedEvents), coveredEventCount(), this.tokensEstimatedSaved, this.strategy);
		List<SessionEvent> events = new ArrayList<>(1 + summaryEvents.size());
		events.add(SessionEvent.builder()
			.id(boundaryId)
			.sessionId(sessionId)
			.timestamp(now)
			.type(EventType.COMPACTION)
			.payload(boundary.toPayload())
			.metadata(SessionEvent.METADATA_COMPACTION_SOURCE, this.strategy)
			.build());
		events.addAll(summaryEvents);
		return events;
	}

	private static List<String> ids(List<SessionEvent> events) {
		return events.stream().map(SessionEvent::getId).toList();
	}

}
