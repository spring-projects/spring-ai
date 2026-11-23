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

package org.springframework.ai.session;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.session.compaction.CompactionBoundary;
import org.springframework.util.Assert;

/**
 * Which events of a session's log a read returns: the view (the whole log or its
 * {@linkplain Builder#activeView(boolean) active view}), the per-event criteria (event
 * and message types, synthetic events) and the retrieval {@link Window}.
 * <p>
 * The window is applied after the per-event criteria, to the matching events in log
 * order. {@link Window.All} returns every matching event. {@link Window.Last} returns the
 * newest N, extended back to the start of the turn the window lands in (see
 * {@link SessionEvent#isTurnStart()}), so a prompt never begins with an assistant reply
 * or a tool result whose user message or tool call was cut off; when no turn start
 * precedes the window the plain window is returned.
 * <p>
 * {@link #apply(List)} is the reference implementation of this contract for a log held in
 * memory; a store that can push the criteria down to a query does so and agrees with it
 * (see {@link SessionRepository#findEvents}).
 *
 * <pre>{@code
 * EventFilter prompt = EventFilter.active();                        // the conversation to send
 * EventFilter recent = EventFilter.active().toBuilder().lastN(20).build();
 * EventFilter replies = EventFilter.builder().messageTypes(Set.of(MessageType.ASSISTANT)).build();
 * }</pre>
 *
 * @param messageTypes the message types to include, or {@code null} for every type
 * @param eventTypes the event types to include, or {@code null} for every type
 * @param excludeSynthetic whether synthetic events (compaction summaries) are left out
 * @param activeView whether the filter applies to the active view rather than the whole
 * log
 * @param window the retrieval window
 * @author Christian Tzolov
 * @since 2.1.0
 */
public record EventFilter(@Nullable Set<MessageType> messageTypes, @Nullable Set<EventType> eventTypes,
		boolean excludeSynthetic, boolean activeView, Window window) {

	public EventFilter {
		messageTypes = (messageTypes != null && !messageTypes.isEmpty()) ? Set.copyOf(messageTypes) : null;
		eventTypes = (eventTypes != null && !eventTypes.isEmpty()) ? Set.copyOf(eventTypes) : null;
		Assert.notNull(window, "window must not be null");
	}

	/**
	 * Returns the whole log with no filtering, <em>including</em> the events a compaction
	 * covered and the compaction boundaries themselves. For the conversation to send to
	 * the model use {@link #active()}.
	 */
	public static EventFilter all() {
		return builder().build();
	}

	/**
	 * Returns the active conversation: the {@link EventType#MESSAGE} events of the
	 * {@linkplain Builder#activeView(boolean) active view}, in prompt order. This is the
	 * view that goes into the prompt as the active context window, and the input of
	 * compaction. Events of any other type record runtime state, not conversation, and
	 * are left out.
	 */
	public static EventFilter active() {
		return builder().activeView(true).eventTypes(Set.of(EventType.MESSAGE)).build();
	}

	/**
	 * Returns the last {@code n} events of the whole log, extended to the start of their
	 * turn.
	 */
	public static EventFilter lastN(int n) {
		return builder().lastN(n).build();
	}

	/** Returns a new {@link Builder}. */
	public static Builder builder() {
		return new Builder();
	}

	/** Returns a builder initialised with every criterion of this filter. */
	public Builder toBuilder() {
		return new Builder(this);
	}

	/**
	 * Combines this filter with {@code other}, a configured filter with a per-request one
	 * over it: the types of {@code other} replace this filter's, its window too unless it
	 * is {@link Window#ALL}, and the {@code excludeSynthetic} and {@code activeView}
	 * flags are OR-ed, so either side can narrow the result.
	 */
	public EventFilter merge(EventFilter other) {
		Assert.notNull(other, "other must not be null");
		return toBuilder().messageTypes(other.messageTypes != null ? other.messageTypes : this.messageTypes)
			.eventTypes(other.eventTypes != null ? other.eventTypes : this.eventTypes)
			.excludeSynthetic(other.excludeSynthetic || this.excludeSynthetic)
			.activeView(other.activeView || this.activeView)
			.window(!(other.window instanceof Window.All) ? other.window : this.window)
			.build();
	}

	/**
	 * Whether the event passes the per-event criteria: the event and message types and
	 * the synthetic flag. The view and the window apply to the collection.
	 */
	public boolean matches(SessionEvent event) {
		if (this.excludeSynthetic && event.isSynthetic()) {
			return false;
		}
		if (this.eventTypes != null && !this.eventTypes.contains(event.getType())) {
			return false;
		}
		return this.messageTypes == null || (event.hasMessage() && this.messageTypes.contains(event.getMessageType()));
	}

	/**
	 * Applies this filter to the events of one session held in memory: with
	 * {@link #activeView()} first reduces the log to its active view
	 * ({@link CompactionBoundary#project(List)}), then keeps the events that
	 * {@link #matches(SessionEvent)}, then applies the window keeping turns whole.
	 * @param events the events of one session, oldest first: the whole log, or for an
	 * active view any superset of the view that includes the newest boundary
	 * @return the matching events, never the input list itself
	 */
	public List<SessionEvent> apply(List<SessionEvent> events) {
		Assert.notNull(events, "events must not be null");
		List<SessionEvent> base = this.activeView ? CompactionBoundary.project(events) : events;
		return applyWindow(base.stream().filter(this::matches).toList());
	}

	/**
	 * Applies the window to the filtered events of one session, oldest first, keeping
	 * turns whole: a {@link Window.Last} window whose first event is not a
	 * {@linkplain SessionEvent#isTurnStart() turn start} is extended back to the nearest
	 * turn start before it. The plain window is returned when no turn start precedes it:
	 * a preamble before the first turn, a {@link #messageTypes()} filter that hides user
	 * messages, or a filter that admits no {@link EventType#MESSAGE} event. The reference
	 * implementation of the {@link SessionRepository#findEvents} window.
	 * @param matched the filtered events of one session, oldest first
	 * @return the windowed events, a copy
	 */
	public List<SessionEvent> applyWindow(List<SessionEvent> matched) {
		Assert.notNull(matched, "matched must not be null");
		if (!(this.window instanceof Window.Last last) || matched.size() <= last.n()) {
			return List.copyOf(matched);
		}
		int from = matched.size() - last.n();
		if (!matched.get(from).isTurnStart()
				&& (this.eventTypes == null || this.eventTypes.contains(EventType.MESSAGE))) {
			for (int i = from - 1; i >= 0; i--) {
				if (matched.get(i).isTurnStart()) {
					from = i;
					break;
				}
			}
		}
		return List.copyOf(matched.subList(from, matched.size()));
	}

	/** The retrieval window: which of the matching events are returned. */
	public sealed interface Window permits Window.All, Window.Last {

		/** Every matching event. */
		Window ALL = new All();

		/** The newest {@code n} matching events, extended to the start of their turn. */
		static Window last(int n) {
			return new Last(n);
		}

		/** Every matching event. */
		record All() implements Window {
		}

		/** The newest {@code n} matching events. */
		record Last(int n) implements Window {
			public Last {
				if (n <= 0) {
					throw new IllegalArgumentException("lastN must be greater than 0");
				}
			}
		}

	}

	/** Builds an {@link EventFilter}; nothing set is {@link EventFilter#all()}. */
	public static final class Builder {

		private @Nullable Set<MessageType> messageTypes;

		private @Nullable Set<EventType> eventTypes;

		private boolean excludeSynthetic = false;

		private boolean activeView = false;

		private Window window = Window.ALL;

		private Builder() {
		}

		private Builder(EventFilter source) {
			this.messageTypes = source.messageTypes;
			this.eventTypes = source.eventTypes;
			this.excludeSynthetic = source.excludeSynthetic;
			this.activeView = source.activeView;
			this.window = source.window;
		}

		/**
		 * Only include events whose {@link SessionEvent#getType()} is in this set. Unset
		 * (the default) includes every type; {@link EventFilter#active()} selects
		 * {@link EventType#MESSAGE} only.
		 */
		public Builder eventTypes(@Nullable Set<EventType> eventTypes) {
			this.eventTypes = eventTypes;
			return this;
		}

		/**
		 * Keeps only the events whose message is of one of these types; an event without
		 * a message never matches. Unset, or an empty set, keeps every type.
		 */
		public Builder messageTypes(@Nullable Set<MessageType> messageTypes) {
			this.messageTypes = messageTypes;
			return this;
		}

		/** When {@code true}, the synthetic events a compaction wrote are left out. */
		public Builder excludeSynthetic(boolean excludeSynthetic) {
			this.excludeSynthetic = excludeSynthetic;
			return this;
		}

		/**
		 * When {@code true}, the filter applies to the active view the newest compaction
		 * boundary defines, the pinned events, the summary, the preserved events and
		 * everything appended since, in that order (see
		 * {@link CompactionBoundary#project(List)}), instead of to the whole log;
		 * {@code false} by default.
		 */
		public Builder activeView(boolean activeView) {
			this.activeView = activeView;
			return this;
		}

		/** The retrieval window; defaults to {@link Window#ALL}. */
		public Builder window(Window window) {
			Assert.notNull(window, "window must not be null");
			this.window = window;
			return this;
		}

		/**
		 * Keeps the newest {@code n} matching events, extended to the start of their
		 * turn: {@link Window#last(int)}. {@code null} clears the window.
		 */
		public Builder lastN(@Nullable Integer lastN) {
			return window(lastN != null ? Window.last(lastN) : Window.ALL);
		}

		public EventFilter build() {
			return new EventFilter(this.messageTypes, this.eventTypes, this.excludeSynthetic, this.activeView,
					this.window);
		}

	}

}
