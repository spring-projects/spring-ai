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
 * The record of one compaction: a {@link EventType#COMPACTION} event's payload, typed.
 *
 * <p>
 * Compaction never changes the events already in the log. It appends one boundary event
 * and, right after it, the synthetic summary events. The boundary names, by id, the
 * events that make up the active view from then on:
 * <ul>
 * <li>{@link #pinnedEventIds()} — events kept out of order at the front, such as the
 * latest stored system message;</li>
 * <li>{@link #summaryEventIds()} — the summary events appended after the boundary (a
 * shadow prompt and a summary, or none for a plain window);</li>
 * <li>{@link #preservedEventIds()} — the recent conversation kept verbatim, in log
 * order.</li>
 * </ul>
 * Every other event that was in the view before the compaction is <em>covered</em>: it
 * stays in the log, readable through {@code EventFilter.all()} and the recall tools, but
 * is no longer part of the prompt. {@link #project(List)} builds the view from a log:
 * pinned, then summary, then preserved, then everything appended after the summary. The
 * newest boundary wins; a later compaction covers the previous summary and starts a new
 * view.
 *
 * <p>
 * The id lists make a boundary self-contained: a view can be rebuilt from the log alone,
 * whatever earlier compactions did, and an older boundary can still be read to see what
 * the model was told at that time.
 *
 * @param id the id of the boundary event
 * @param pinnedEventIds ids of the events kept at the front of the view, in view order
 * @param summaryEventIds ids of the summary events appended after the boundary, in order
 * @param preservedEventIds ids of the events kept verbatim, in log order
 * @param coveredEventCount how many events left the view
 * @param tokensEstimatedSaved the estimated tokens removed from the prompt, net of the
 * summary
 * @param strategy the name of the strategy that produced the compaction
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
		Assert.isTrue(event.getType() == EventType.COMPACTION,
				() -> "Event '" + event.getId() + "' is a " + event.getType() + " event, not a COMPACTION event");
		Map<String, Object> payload = event.getPayload();
		return new CompactionBoundary(event.getId(), ids(payload.get(PAYLOAD_PINNED)),
				ids(payload.get(PAYLOAD_SUMMARY)), ids(payload.get(PAYLOAD_PRESERVED)),
				toInt(payload.get(PAYLOAD_COVERED_COUNT)), toInt(payload.get(PAYLOAD_TOKENS_SAVED)),
				String.valueOf(payload.getOrDefault(PAYLOAD_STRATEGY, "")));
	}

	/**
	 * Returns the newest boundary in a log, or {@code null} when the log was never
	 * compacted.
	 * @param log the events of one session, oldest first
	 */
	public static @Nullable CompactionBoundary newest(List<SessionEvent> log) {
		Assert.notNull(log, "log must not be null");
		int index = newestBoundaryIndex(log);
		return (index < 0) ? null : from(log.get(index));
	}

	private static int newestBoundaryIndex(List<SessionEvent> log) {
		for (int i = log.size() - 1; i >= 0; i--) {
			if (log.get(i).getType() == EventType.COMPACTION) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Builds the active view of a log from its newest boundary: the pinned events, then
	 * the summary events, then the preserved events, then every event appended after the
	 * summary. The boundary event itself is not part of the view. A log without a
	 * boundary is its own view. This is the reference implementation of the view every
	 * {@link org.springframework.ai.session.SessionRepository} returns for
	 * {@link org.springframework.ai.session.EventFilter#active()}; a store may compute
	 * the same thing in a query.
	 * @param log the events of one session, oldest first; it may be the whole log or any
	 * superset of the view that includes the newest boundary
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
		Set<String> summaryIds = new HashSet<>(boundary.summaryEventIds());
		List<SessionEvent> view = new ArrayList<>();
		for (String id : boundary.pinnedEventIds()) {
			SessionEvent event = byId.get(id);
			if (event != null) {
				view.add(event);
			}
		}
		for (String id : boundary.summaryEventIds()) {
			SessionEvent event = byId.get(id);
			if (event != null) {
				view.add(event);
			}
		}
		for (String id : boundary.preservedEventIds()) {
			SessionEvent event = byId.get(id);
			if (event != null) {
				view.add(event);
			}
		}
		for (int i = boundaryIndex + 1; i < log.size(); i++) {
			SessionEvent event = log.get(i);
			if (!summaryIds.contains(event.getId())) {
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
