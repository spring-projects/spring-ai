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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.session.EventType;
import org.springframework.ai.session.SessionEvent;
import org.springframework.util.Assert;

/**
 * The record of one compaction: the payload of a {@link EventType#COMPACTION} event,
 * typed. A compaction never changes the events already in the log; it appends one
 * boundary event and, right after it, the synthetic summary events. The boundary names,
 * by id, the events that make up the active view from then on: the
 * {@link #pinnedEventIds() pinned} events kept out of order at the front, such as the
 * latest stored system message; the {@link #summaryEventIds() summary} events appended
 * after the boundary, or none for a plain window; the {@link #preservedEventIds()
 * preserved} recent conversation, in log order. Every other event that was in the view is
 * covered: it stays in the log, readable through {@code EventFilter.all()}, and is no
 * longer part of the prompt.
 * <p>
 * {@link #project(List)} builds the view from a log: pinned, then summary, then
 * preserved, then everything appended after the summary. The newest boundary wins, so a
 * later compaction covers the previous summary and starts a new view, and the id lists
 * make a boundary self-contained: a view is rebuilt from the log alone, whatever earlier
 * compactions did, and an older boundary still shows what the model was told at the time.
 *
 * @param id the id of the boundary event
 * @param pinnedEventIds the ids of the events kept at the front of the view, in view
 * order
 * @param summaryEventIds the ids of the summary events appended after the boundary, in
 * order
 * @param preservedEventIds the ids of the events kept verbatim, in log order
 * @param coveredEventCount how many events left the view
 * @param tokensEstimatedSaved the estimated tokens removed from the prompt, net of the
 * summary
 * @param strategy the name of the strategy that compacted
 * @author Christian Tzolov
 * @since 2.1.0
 */
public record CompactionBoundary(String id, List<String> pinnedEventIds, List<String> summaryEventIds,
		List<String> preservedEventIds, int coveredEventCount, int tokensEstimatedSaved, String strategy) {

	/** Payload key: {@link #pinnedEventIds()}. */
	public static final String PAYLOAD_PINNED = "pinnedEventIds";

	/** Payload key: {@link #summaryEventIds()}. */
	public static final String PAYLOAD_SUMMARY = "summaryEventIds";

	/** Payload key: {@link #preservedEventIds()}. */
	public static final String PAYLOAD_PRESERVED = "preservedEventIds";

	/** Payload key: {@link #coveredEventCount()}. */
	public static final String PAYLOAD_COVERED_COUNT = "coveredEventCount";

	/** Payload key: {@link #tokensEstimatedSaved()}. */
	public static final String PAYLOAD_TOKENS_SAVED = "tokensEstimatedSaved";

	/** Payload key: {@link #strategy()}. */
	public static final String PAYLOAD_STRATEGY = "strategy";

	public CompactionBoundary {
		Assert.hasText(id, "id must not be null or empty");
		Assert.notNull(pinnedEventIds, "pinnedEventIds must not be null");
		Assert.notNull(summaryEventIds, "summaryEventIds must not be null");
		Assert.notNull(preservedEventIds, "preservedEventIds must not be null");
		Assert.notNull(strategy, "strategy must not be null");
		pinnedEventIds = List.copyOf(pinnedEventIds);
		summaryEventIds = List.copyOf(summaryEventIds);
		preservedEventIds = List.copyOf(preservedEventIds);
	}

	/** The payload of the boundary event, as stored. */
	public Map<String, Object> toPayload() {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put(PAYLOAD_PINNED, this.pinnedEventIds);
		payload.put(PAYLOAD_SUMMARY, this.summaryEventIds);
		payload.put(PAYLOAD_PRESERVED, this.preservedEventIds);
		payload.put(PAYLOAD_COVERED_COUNT, this.coveredEventCount);
		payload.put(PAYLOAD_TOKENS_SAVED, this.tokensEstimatedSaved);
		payload.put(PAYLOAD_STRATEGY, this.strategy);
		return payload;
	}

	/**
	 * Reads the boundary recorded by a {@link EventType#COMPACTION} event.
	 * @param event a compaction event
	 * @return its boundary
	 * @throws IllegalArgumentException if the event is not a compaction event
	 */
	public static CompactionBoundary from(SessionEvent event) {
		Assert.notNull(event, "event must not be null");
		Assert.isTrue(EventType.COMPACTION.equals(event.getType()),
				() -> "Event '" + event.getId() + "' is a " + event.getType() + " event, not a COMPACTION event");
		Map<String, Object> payload = event.getPayload();
		return new CompactionBoundary(event.getId(), ids(payload.get(PAYLOAD_PINNED)),
				ids(payload.get(PAYLOAD_SUMMARY)), ids(payload.get(PAYLOAD_PRESERVED)),
				toInt(payload.get(PAYLOAD_COVERED_COUNT)), toInt(payload.get(PAYLOAD_TOKENS_SAVED)),
				String.valueOf(payload.getOrDefault(PAYLOAD_STRATEGY, "")));
	}

	/**
	 * The newest boundary of a log, or {@code null} when the log was never compacted.
	 * @param log the events of one session, oldest first
	 */
	public static @Nullable CompactionBoundary newest(List<SessionEvent> log) {
		Assert.notNull(log, "log must not be null");
		int index = newestBoundaryIndex(log);
		return (index < 0) ? null : from(log.get(index));
	}

	private static int newestBoundaryIndex(List<SessionEvent> log) {
		for (int i = log.size() - 1; i >= 0; i--) {
			if (EventType.COMPACTION.equals(log.get(i).getType())) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * The active view of a log, from its newest boundary: the pinned events, then the
	 * summary events, then the preserved events, then every event appended after the
	 * summary; a log without a boundary is its own view, and the boundary event is never
	 * in it. The reference implementation of the view every
	 * {@link org.springframework.ai.session.SessionRepository} returns for
	 * {@link org.springframework.ai.session.EventFilter#active()}; a store may compute
	 * the same thing in a query.
	 * @param log the events of one session, oldest first: the whole log, or any superset
	 * of the view that includes the newest boundary
	 * @return the view, never the input list itself
	 */
	public static List<SessionEvent> project(List<SessionEvent> log) {
		Assert.notNull(log, "log must not be null");
		int boundaryIndex = newestBoundaryIndex(log);
		if (boundaryIndex < 0) {
			return new ArrayList<>(log);
		}
		CompactionBoundary boundary = from(log.get(boundaryIndex));
		Map<String, SessionEvent> byId = new LinkedHashMap<>();
		for (SessionEvent event : log) {
			byId.putIfAbsent(event.getId(), event);
		}
		// An id the boundary names twice, or names and also appended after it, is placed
		// once
		Set<String> placed = new HashSet<>();
		List<SessionEvent> view = new ArrayList<>();
		for (List<String> ids : List.of(boundary.pinnedEventIds(), boundary.summaryEventIds(),
				boundary.preservedEventIds())) {
			for (String id : ids) {
				SessionEvent event = byId.get(id);
				if (event != null && placed.add(id)) {
					view.add(event);
				}
			}
		}
		for (int i = boundaryIndex + 1; i < log.size(); i++) {
			SessionEvent event = log.get(i);
			if (placed.add(event.getId())) {
				view.add(event);
			}
		}
		return List.copyOf(view);
	}

	private static List<String> ids(@Nullable Object value) {
		if (!(value instanceof List<?> list)) {
			return List.of();
		}
		List<String> ids = new ArrayList<>(list.size());
		for (Object item : list) {
			if (item != null) {
				ids.add(String.valueOf(item));
			}
		}
		return ids;
	}

	private static int toInt(@Nullable Object value) {
		return (value instanceof Number number) ? number.intValue() : 0;
	}

}
