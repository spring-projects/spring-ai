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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.MessageType;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A keyword search over the messages of a session: what
 * {@link SessionService#searchEvents} looks for. It is the read for recall, finding
 * something that was said, where {@link EventFilter} is the read for the prompt.
 * <p>
 * The search covers the whole log, the events a compaction covered included: they are no
 * longer sent to the model, and a search is how it finds them again. Only
 * {@link EventType#MESSAGE} events are searched, by the text of their message, and
 * synthetic events never: a summary repeats what the log still holds. A tool result
 * carries its data outside the message text, so it is not found.
 * <p>
 * A keyword matches when the text contains it, ignoring case. With several keywords,
 * {@link MatchMode#ANY} matches a text that contains one of them and
 * {@link MatchMode#ALL} a text that contains every one. The matches come oldest first, a
 * {@linkplain #page() page} at a time.
 * <p>
 * Letters are compared the same way everywhere for ASCII text. For other text the store
 * decides: a database may ignore accents or fold case differently, by its collation.
 * <p>
 * A query is bounded, so that one search costs a bounded read: at most
 * {@link #MAX_KEYWORDS} keywords of at most {@link #MAX_KEYWORD_LENGTH} characters, and
 * pages only within the first {@link #MAX_MATCHES} matches.
 * <p>
 * {@link #apply(List)} is the reference implementation for a log held in memory; a store
 * that can push the search down to a query does so and agrees with it (see
 * {@link SessionRepository#searchEvents}).
 *
 * <pre>{@code
 * EventQuery first = EventQuery.keyword("invoice");
 * EventQuery both = EventQuery.builder().keywords("invoice", "refund").matchMode(MatchMode.ALL).build();
 * EventQuery recent = EventQuery.builder().keywords("invoice").since(lastWeek).page(1).build();
 * }</pre>
 *
 * @param keywords the keywords to look for, at least one, none blank
 * @param matchMode how several keywords combine
 * @param messageTypes the message types to search, or {@code null} for every type
 * @param since only events at or after this instant, to the microsecond, or {@code null}
 * for no lower bound
 * @param page the zero-based page of matches to return, within the first
 * {@link #MAX_MATCHES} matches
 * @param pageSize the number of matches on a page
 * @author Christian Tzolov
 * @since 2.1.0
 */
public record EventQuery(List<String> keywords, MatchMode matchMode, @Nullable Set<MessageType> messageTypes,
		@Nullable Instant since, int page, int pageSize) {

	/** The page size of a query that sets none. */
	public static final int DEFAULT_PAGE_SIZE = 10;

	/** The largest page size, so that one search cannot return a whole log. */
	public static final int MAX_PAGE_SIZE = 100;

	/**
	 * How far a search pages: the page and the pages before it hold at most this many
	 * matches. A store reads the matches from the first one on, so this bounds what one
	 * search reads.
	 */
	public static final int MAX_MATCHES = 1000;

	/** The most keywords a query takes; each becomes a predicate of the store's query. */
	public static final int MAX_KEYWORDS = 20;

	/** The longest keyword, in characters. */
	public static final int MAX_KEYWORD_LENGTH = 200;

	/** How several keywords combine. */
	public enum MatchMode {

		/** The text contains at least one of the keywords. */
		ANY,

		/** The text contains every keyword. */
		ALL

	}

	public EventQuery {
		Assert.notNull(keywords, "keywords must not be null");
		keywords = keywords.stream().filter(StringUtils::hasText).map(String::strip).distinct().toList();
		Assert.notEmpty(keywords, "keywords must hold at least one keyword that is not blank");
		Assert.isTrue(keywords.size() <= MAX_KEYWORDS, "keywords must hold at most " + MAX_KEYWORDS + " keywords");
		Assert.isTrue(keywords.stream().allMatch(keyword -> keyword.length() <= MAX_KEYWORD_LENGTH),
				"a keyword must be at most " + MAX_KEYWORD_LENGTH + " characters long");
		Assert.isTrue(keywords.stream().noneMatch(keyword -> keyword.indexOf('\u0000') >= 0),
				"a keyword must not hold a NUL character");
		Assert.notNull(matchMode, "matchMode must not be null");

		messageTypes = (messageTypes != null && !messageTypes.isEmpty()) ? Set.copyOf(messageTypes) : null;
		// The stores keep timestamps to the microsecond
		since = (since != null) ? since.truncatedTo(ChronoUnit.MICROS) : null;

		Assert.isTrue(page >= 0, "page must not be negative");
		Assert.isTrue(pageSize > 0 && pageSize <= MAX_PAGE_SIZE, "pageSize must be between 1 and " + MAX_PAGE_SIZE);
		Assert.isTrue(((long) page + 1) * pageSize <= MAX_MATCHES,
				"page " + page + " is beyond the first " + MAX_MATCHES + " matches a search reads");
	}

	/** Returns a query for the first page of the events that contain the keyword. */
	public static EventQuery keyword(String keyword) {
		return builder().keywords(keyword).build();
	}

	/** Returns a new {@link Builder}. */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * How many matches a store reads to return this page: the page and every page before
	 * it. A store that cannot skip rows in its query reads this many and
	 * {@linkplain #pageOf(List) keeps the page}.
	 */
	public int limit() {
		return (this.page + 1) * this.pageSize;
	}

	/**
	 * Whether the event is one this query finds: a message event that is not synthetic,
	 * of a searched type, not older than {@link #since()}, whose text contains the
	 * keywords.
	 */
	public boolean matches(SessionEvent event) {
		if (!EventType.MESSAGE.equals(event.getType()) || !event.hasMessage() || event.isSynthetic()) {
			return false;
		}

		if (this.messageTypes != null && !this.messageTypes.contains(event.getMessageType())) {
			return false;
		}

		if (this.since != null && event.getTimestamp().isBefore(this.since)) {
			return false;
		}

		String text = event.getMessage().getText();
		if (text == null) {
			return false;
		}

		return (this.matchMode == MatchMode.ALL)
				? this.keywords.stream().allMatch(keyword -> indexOfIgnoreCase(text, keyword) >= 0)
				: this.keywords.stream().anyMatch(keyword -> indexOfIgnoreCase(text, keyword) >= 0);
	}

	/**
	 * The index of the first place the text holds the keyword, ignoring case, or
	 * {@code -1}. The characters are compared one by one, so the index is one of the text
	 * as given, and a letter matches its other case wherever it stands in a word.
	 */
	public static int indexOfIgnoreCase(String text, String keyword) {
		int last = text.length() - keyword.length();
		for (int index = 0; index <= last; index++) {
			if (text.regionMatches(true, index, keyword, 0, keyword.length())) {
				return index;
			}
		}
		return -1;
	}

	/**
	 * Runs the query over the events of a log, given oldest first: the matching events,
	 * oldest first, cut to the page.
	 */
	public List<SessionEvent> apply(List<SessionEvent> events) {
		List<SessionEvent> matches = new ArrayList<>();
		int limit = limit();
		for (SessionEvent event : events) {
			if (matches(event)) {
				matches.add(event);
				if (matches.size() == limit) {
					break;
				}
			}
		}
		return pageOf(matches);
	}

	/**
	 * Keeps this query's page of the matches a store read from the first one on, up to
	 * {@link #limit()} of them.
	 */
	public List<SessionEvent> pageOf(List<SessionEvent> matches) {
		long from = (long) this.page * this.pageSize;
		if (from >= matches.size()) {
			return List.of();
		}
		return List.copyOf(matches.subList((int) from, (int) Math.min(from + this.pageSize, matches.size())));
	}

	/** Builds an {@link EventQuery}. */
	public static final class Builder {

		private List<String> keywords = List.of();

		private MatchMode matchMode = MatchMode.ANY;

		private @Nullable Set<MessageType> messageTypes;

		private @Nullable Instant since;

		private int page = 0;

		private int pageSize = DEFAULT_PAGE_SIZE;

		private Builder() {
		}

		/** The keywords to look for; at least one. */
		public Builder keywords(String... keywords) {
			Assert.notNull(keywords, "keywords must not be null");
			return keywords(Arrays.asList(keywords));
		}

		/** The keywords to look for; at least one. */
		public Builder keywords(List<String> keywords) {
			Assert.notNull(keywords, "keywords must not be null");
			// A null keyword is dropped with the blank ones
			this.keywords = Collections.unmodifiableList(new ArrayList<>(keywords));
			return this;
		}

		/** How several keywords combine; {@link MatchMode#ANY} by default. */
		public Builder matchMode(MatchMode matchMode) {
			this.matchMode = matchMode;
			return this;
		}

		/** The message types to search; every type by default. */
		public Builder messageTypes(@Nullable Set<MessageType> messageTypes) {
			this.messageTypes = messageTypes;
			return this;
		}

		/** Only events at or after this instant; no lower bound by default. */
		public Builder since(@Nullable Instant since) {
			this.since = since;
			return this;
		}

		/** The zero-based page of matches; the first by default. */
		public Builder page(int page) {
			this.page = page;
			return this;
		}

		/** The matches on a page; {@link #DEFAULT_PAGE_SIZE} by default. */
		public Builder pageSize(int pageSize) {
			this.pageSize = pageSize;
			return this;
		}

		public EventQuery build() {
			return new EventQuery(this.keywords, this.matchMode, this.messageTypes, this.since, this.page,
					this.pageSize);
		}

	}

}
