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

package org.springframework.ai.session.tool;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.session.EventQuery;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.util.JsonHelper;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Lets a model search the conversation of its session: the {@code conversation_search}
 * tool. A compaction keeps the prompt small by taking older messages out of it; they stay
 * in the log, and this tool is how the model finds them again, by keyword (see
 * {@link EventQuery}).
 * <p>
 * The tool searches one session, the one named in the {@link ToolContext} under
 * {@link #SESSION_ID_CONTEXT_KEY}. The model never names the session. The key is the one
 * the session advisors read, but advisor parameters do not reach a tool, so the caller
 * passes the session id to both: <pre>{@code
 * ChatClient client = ChatClient.builder(chatModel)
 *     .defaultAdvisors(SessionAdvisors.builder(sessionService).build())
 *     .defaultTools(SessionRecallTools.builder(sessionService).build())
 *     .build();
 *
 * client.prompt()
 *     .user(text)
 *     .advisors(SessionAdvisors.session(sessionId))
 *     .toolContext(Map.of(SessionRecallTools.SESSION_ID_CONTEXT_KEY, sessionId))
 *     .call();
 * }</pre> With a tool context that holds no session id the tool searches nothing and
 * tells the model so. With no tool context at all the tool is not called: the
 * tool-calling machinery fails the call, because the tool takes a {@link ToolContext}.
 * The session advisors can put the session id into the tool context themselves, with
 * {@code SessionAdvisors.builder(sessionService).sessionIdInToolContext(true)}; every
 * tool of the call then sees it.
 * <p>
 * Read-only: the tool appends, compacts and deletes nothing.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class SessionRecallTools {

	/**
	 * The {@link ToolContext} key of the session to search. The same key as the session
	 * advisors' {@code SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY}.
	 */
	public static final String SESSION_ID_CONTEXT_KEY = "chat_memory_conversation_id";

	static final String MISSING_SESSION_ID_RESULT = "Error: conversation_search is unavailable, no session id was "
			+ "provided in the tool context.";

	static final String MISSING_KEYWORD_RESULT = "Error: conversation_search needs at least one keyword.";

	static final String UNKNOWN_MATCH_RESULT = "Error: match must be 'any' or 'all'.";

	static final String NO_RESULTS = "No results found.";

	static final String NO_MORE_RESULTS = "No more results.";

	/** The longest text a result carries by default, in characters. */
	public static final int DEFAULT_MAX_TEXT_LENGTH = 1000;

	private static final JsonHelper JSON = new JsonHelper();

	private final SessionService sessionService;

	private final int pageSize;

	private final int maxTextLength;

	private SessionRecallTools(SessionService sessionService, int pageSize, int maxTextLength) {
		this.sessionService = sessionService;
		this.pageSize = pageSize;
		this.maxTextLength = maxTextLength;
	}

	public static Builder builder(SessionService sessionService) {
		return new Builder(sessionService);
	}

	/**
	 * Searches the session's conversation for messages that contain the keywords. Returns
	 * a page of matches, oldest first, as JSON: {@code results}, each with a
	 * {@code timestamp}, a {@code type} and a {@code text}, and {@code nextPage} when the
	 * page is full and more matches may follow. A long text is cut to an excerpt around
	 * the first keyword it holds. Returns a short text when nothing matches or the search
	 * cannot run.
	 */
	@Tool(name = "conversation_search",
			description = "Search the earlier conversation of this session for messages that contain the given "
					+ "keywords, ignoring case. It also finds messages that are no longer in your context. Returns "
					+ "one page of matches, oldest first, as JSON: 'results' with the timestamp, the type and the "
					+ "text of each message, and 'nextPage' when more matches may follow. A long text is cut to "
					+ "an excerpt around the keyword.")
	public String conversationSearch(@ToolParam(
			description = "One or more keywords, separated by commas. Each keyword is matched as literal text, so 'invoice, refund' looks for two words and 'invoice refund' for that exact phrase.") String query,
			@ToolParam(
					description = "'any' (the default): a message matches when it contains one of the keywords. 'all': it must contain every keyword.",
					required = false) @Nullable String match,
			@ToolParam(description = "The page of matches, starting at 0. Omit it for the first page.",
					required = false) @Nullable Integer page,
			ToolContext toolContext) {

		// A session id of another type, a UUID for example, is taken by its text
		Object sessionIdValue = toolContext.getContext().get(SESSION_ID_CONTEXT_KEY);
		String sessionId = (sessionIdValue != null) ? sessionIdValue.toString() : null;
		if (!StringUtils.hasText(sessionId)) {
			return MISSING_SESSION_ID_RESULT;
		}

		List<String> keywords = (query != null)
				? Arrays.stream(query.split(",")).filter(StringUtils::hasText).map(String::strip).toList() : List.of();

		if (keywords.isEmpty()) {
			return MISSING_KEYWORD_RESULT;
		}

		String matchValue = StringUtils.hasText(match) ? match.strip() : "any";
		if (!"any".equalsIgnoreCase(matchValue) && !"all".equalsIgnoreCase(matchValue)) {
			return UNKNOWN_MATCH_RESULT;
		}

		int pageNumber = (page != null) ? Math.max(0, page) : 0;
		EventQuery eventQuery;
		try {
			eventQuery = EventQuery.builder()
				.keywords(keywords)
				.matchMode("all".equalsIgnoreCase(matchValue) ? EventQuery.MatchMode.ALL : EventQuery.MatchMode.ANY)
				.page(pageNumber)
				.pageSize(this.pageSize)
				.build();
		}
		catch (IllegalArgumentException ex) {
			// Too many keywords, a keyword too long, a page too far: the model's to fix
			return "Error: " + ex.getMessage() + ".";
		}

		List<SessionEvent> found = this.sessionService.searchEvents(sessionId, eventQuery);

		if (found.isEmpty()) {
			return (pageNumber == 0) ? NO_RESULTS : NO_MORE_RESULTS;
		}

		Map<String, Object> answer = new LinkedHashMap<>();

		answer.put("results", found.stream().map(event -> result(event, eventQuery.keywords())).toList());

		// A full page may have a next one, as long as the search can still read it
		if (found.size() == this.pageSize && ((long) pageNumber + 2) * this.pageSize <= EventQuery.MAX_MATCHES) {
			answer.put("nextPage", pageNumber + 1);
		}

		return JSON.toJson(answer);
	}

	private Map<String, String> result(SessionEvent event, List<String> keywords) {
		Map<String, String> result = new LinkedHashMap<>();
		result.put("timestamp", event.getTimestamp().toString());
		result.put("type", event.getMessageType().getValue());
		result.put("text", excerpt(String.valueOf(event.getMessage().getText()), keywords));
		return result;
	}

	/**
	 * The text, or when it is longer than the limit, that many characters of it around
	 * the first keyword it holds, with {@code ...} where it was cut.
	 */
	private String excerpt(String text, List<String> keywords) {
		if (text.length() <= this.maxTextLength) {
			return text;
		}
		// The store may match what this comparison does not, by its collation: then the
		// excerpt is the start of the text
		int match = keywords.stream()
			.mapToInt(keyword -> EventQuery.indexOfIgnoreCase(text, keyword))
			.filter(index -> index >= 0)
			.min()
			.orElse(0);
		// A quarter of the excerpt before the keyword, the rest from it on
		int start = Math.max(0, Math.min(match - this.maxTextLength / 4, text.length() - this.maxTextLength));
		int end = start + this.maxTextLength;
		// Never cut between the two halves of one character
		if (start > 0 && Character.isLowSurrogate(text.charAt(start))) {
			start++;
		}
		if (end < text.length() && Character.isLowSurrogate(text.charAt(end))) {
			end--;
		}
		return ((start > 0) ? "..." : "") + text.substring(start, end) + ((end < text.length()) ? "..." : "");
	}

	/** Builds {@link SessionRecallTools}. */
	public static final class Builder {

		private final SessionService sessionService;

		private int pageSize = EventQuery.DEFAULT_PAGE_SIZE;

		private int maxTextLength = DEFAULT_MAX_TEXT_LENGTH;

		private Builder(SessionService sessionService) {
			Assert.notNull(sessionService, "sessionService must not be null");
			this.sessionService = sessionService;
		}

		/**
		 * The matches the tool returns on a page; {@link EventQuery#DEFAULT_PAGE_SIZE} by
		 * default, at most {@link EventQuery#MAX_PAGE_SIZE}.
		 */
		public Builder pageSize(int pageSize) {
			Assert.isTrue(pageSize > 0 && pageSize <= EventQuery.MAX_PAGE_SIZE,
					"pageSize must be between 1 and " + EventQuery.MAX_PAGE_SIZE);
			this.pageSize = pageSize;
			return this;
		}

		/**
		 * The longest text a result carries, in characters; a longer message is cut to an
		 * excerpt around the keyword, so that a search does not put back into the prompt
		 * what a compaction took out. {@link #DEFAULT_MAX_TEXT_LENGTH} by default.
		 */
		public Builder maxTextLength(int maxTextLength) {
			Assert.isTrue(maxTextLength > 0, "maxTextLength must be positive");
			this.maxTextLength = maxTextLength;
			return this;
		}

		public SessionRecallTools build() {
			return new SessionRecallTools(this.sessionService, this.pageSize, this.maxTextLength);
		}

	}

}
