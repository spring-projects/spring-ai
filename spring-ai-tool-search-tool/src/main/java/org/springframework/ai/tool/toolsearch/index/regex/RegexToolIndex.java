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

package org.springframework.ai.tool.toolsearch.index.regex;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.ai.tool.toolsearch.ToolReference;
import org.springframework.ai.tool.toolsearch.ToolSearchRequest;
import org.springframework.ai.tool.toolsearch.ToolSearchResponse;
import org.springframework.ai.tool.toolsearch.ToolSearchResponse.SearchMetadata;

/**
 * Regex-based tool searcher that converts natural-language queries into case-insensitive
 * OR patterns and matches them against tool names and descriptions.
 * <p>
 * Stop words are filtered out and remaining tokens are escaped and joined into a pattern
 * of the form {@code (?i)(token1|token2|...)}. Generated patterns are capped at 200
 * characters. Tool-name matches are weighted 2× higher than description matches.
 *
 * @author Christian Tzolov
 * @since 2.0.0
 */
public class RegexToolIndex implements Closeable, ToolIndex {

	private static final Log logger = LogFactory.getLog(RegexToolIndex.class);

	private static final int DEFAULT_MAX_RESULTS = 10;

	private static final int MAX_PATTERN_LENGTH = 200;

	private final AtomicInteger counter = new AtomicInteger(0);

	/**
	 * Map of sessionId to their respective tool indexes.
	 */
	private final Map<String, SessionIndex> sessionIndexes = new ConcurrentHashMap<>();

	public RegexToolIndex() {
	}

	/**
	 * Gets or creates a SessionIndex for the given sessionId.
	 * @param sessionId the session identifier
	 * @return the SessionIndex for the session
	 */
	private SessionIndex getOrCreateSessionIndex(String sessionId) {
		return this.sessionIndexes.computeIfAbsent(sessionId, key -> new SessionIndex());
	}

	@Override
	public void clearIndex(String sessionId) {
		SessionIndex sessionIndex = this.sessionIndexes.remove(sessionId);
		if (sessionIndex != null) {
			sessionIndex.clear();
			if (logger.isDebugEnabled()) {
				logger.debug("Cleared index for session: " + sessionId);
			}
		}
	}

	@Override
	public void indexTool(String sessionId, ToolReference toolReference) {
		String id = String.valueOf(this.counter.getAndIncrement());
		SessionIndex sessionIndex = getOrCreateSessionIndex(sessionId);
		sessionIndex.addTool(new ToolEntry(id, toolReference.toolName(), toolReference.summary()));
		if (logger.isDebugEnabled()) {
			logger.debug("Added tool '" + toolReference.toolName() + "' to session '" + sessionId + "' with id '" + id
					+ "'");
		}
	}

	@Override
	public void indexTools(String sessionId, List<ToolReference> toolReferences) {
		SessionIndex sessionIndex = getOrCreateSessionIndex(sessionId);
		for (ToolReference ref : toolReferences) {
			String id = String.valueOf(this.counter.getAndIncrement());
			sessionIndex.addTool(new ToolEntry(id, ref.toolName(), ref.summary()));
		}
		if (logger.isDebugEnabled()) {
			logger.debug("Added " + toolReferences.size() + " tools to session '" + sessionId + "'");
		}
	}

	@Override
	public ToolSearchResponse search(ToolSearchRequest toolSearchRequest) {
		String sessionId = toolSearchRequest.sessionId();
		if (sessionId == null || sessionId.isBlank()) {
			logger.warn("No sessionId provided in ToolSearchRequest, returning empty results");
			return ToolSearchResponse.builder().build();
		}

		SessionIndex sessionIndex = this.sessionIndexes.get(sessionId);
		if (sessionIndex == null) {
			logger.debug("No index found for session: " + sessionId);
			return ToolSearchResponse.builder().build();
		}

		String query = toolSearchRequest.query();
		int maxResults = toolSearchRequest.maxResults() != null ? toolSearchRequest.maxResults() : DEFAULT_MAX_RESULTS;

		// Convert natural language query to regex pattern
		String regexPattern = convertQueryToRegexPattern(query);
		if (logger.isDebugEnabled()) {
			logger.debug("Converted query '" + query + "' to regex pattern '" + regexPattern + "'");
		}

		return this.doSearch(sessionIndex, query, regexPattern, maxResults);
	}

	/**
	 * Converts a natural-language query into a case-insensitive OR regex pattern.
	 * <p>
	 * Tokens are lowercased, stop words removed, special regex characters escaped, and
	 * joined as {@code (?i)(token1|token2|...)}. The pattern is truncated if it would
	 * exceed 200 characters. Subclasses may override to apply a different conversion
	 * strategy.
	 * @param query the natural-language query
	 * @return a valid regex pattern string; never {@code null}
	 */
	protected String convertQueryToRegexPattern(String query) {
		if (query == null || query.isBlank()) {
			return ".*"; // Match everything if no query
		}

		// Common English stop words to filter out
		Set<String> stopWords = Set.of("a", "an", "the", "is", "are", "was", "were", "be", "been", "being", "have",
				"has", "had", "do", "does", "did", "will", "would", "could", "should", "may", "might", "must", "shall",
				"can", "need", "dare", "ought", "used", "to", "of", "in", "for", "on", "with", "at", "by", "from", "as",
				"into", "through", "during", "before", "after", "above", "below", "between", "and", "or", "but", "if",
				"because", "until", "while", "although", "this", "that", "these", "those", "what", "which", "who",
				"whom", "whose", "i", "me", "my", "we", "our", "you", "your", "he", "him", "his", "she", "her", "it",
				"its", "they", "them", "their", "all", "any", "both", "each", "every", "some", "no", "not", "only",
				"just", "also", "very", "too", "so");

		// Tokenize: split by whitespace and common delimiters
		String[] tokens = query.toLowerCase(Locale.ROOT).split("[\\s,;:.!?()\\[\\]{}\"']+");

		// Filter and process tokens
		List<String> processedTokens = Arrays.stream(tokens)
			.map(String::trim)
			.filter(token -> !token.isEmpty())
			.filter(token -> token.length() >= 2) // Filter out single characters
			.filter(token -> !stopWords.contains(token))
			.map(this::escapeRegexSpecialChars) // Escape special regex characters
			.distinct()
			.collect(Collectors.toList());

		if (processedTokens.isEmpty()) {
			// If all tokens were filtered, use original query words
			processedTokens = Arrays.stream(tokens)
				.map(String::trim)
				.filter(token -> !token.isEmpty())
				.map(this::escapeRegexSpecialChars)
				.distinct()
				.collect(Collectors.toList());
		}

		if (processedTokens.isEmpty()) {
			return ".*"; // Match everything if no valid tokens
		}

		// Build the regex pattern: (?i)(token1|token2|token3)
		String pattern = "(?i)(" + String.join("|", processedTokens) + ")";

		// Enforce max pattern length
		if (pattern.length() > MAX_PATTERN_LENGTH) {
			// Truncate by removing tokens from the end until within limit
			while (pattern.length() > MAX_PATTERN_LENGTH && processedTokens.size() > 1) {
				processedTokens.remove(processedTokens.size() - 1);
				pattern = "(?i)(" + String.join("|", processedTokens) + ")";
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Pattern truncated to " + processedTokens.size() + " tokens to fit max length");
			}
		}

		return pattern;
	}

	/**
	 * Escapes special regex characters in a string.
	 * @param input the input string
	 * @return the escaped string safe for use in regex patterns
	 */
	private String escapeRegexSpecialChars(String input) {
		// Characters that have special meaning in regex: . * + ? ^ $ { } [ ] \ | ( )
		return input.replaceAll("([\\\\.*+?^${}\\[\\]|()])", "\\\\$1");
	}

	/**
	 * Searches for tools matching the regex pattern.
	 * @param sessionIndex the session index to search
	 * @param originalQuery the original natural language query (for metadata)
	 * @param regexPattern the regex pattern to match against tool names and descriptions
	 * @param maxResults maximum number of results to return
	 * @return the search response with matching tools
	 */
	private ToolSearchResponse doSearch(SessionIndex sessionIndex, String originalQuery, String regexPattern,
			int maxResults) {
		long startTime = System.currentTimeMillis();

		Pattern pattern;
		try {
			// Pattern flags (like case-insensitivity) are controlled via inline flags in
			// the pattern
			// e.g., "(?i)weather" for case-insensitive matching
			pattern = Pattern.compile(regexPattern);
		}
		catch (PatternSyntaxException e) {
			if (logger.isErrorEnabled()) {
				logger.error("Invalid regex pattern: '" + regexPattern + "'. Error: " + e.getMessage());
			}
			return ToolSearchResponse.builder()
				.searchMetadata(SearchMetadata.builder()
					.searchType(this.getClass().getSimpleName())
					.query(originalQuery)
					.searchTimeMs(System.currentTimeMillis() - startTime)
					.build())
				.build();
		}

		List<MatchedTool> matchedTools = new ArrayList<>();

		for (ToolEntry entry : sessionIndex.getTools()) {
			double score = calculateMatchScore(pattern, entry);
			if (score > 0) {
				matchedTools.add(new MatchedTool(entry, score));
			}
		}

		// Sort by score descending and limit results
		matchedTools.sort((a, b) -> Double.compare(b.score(), a.score()));

		List<ToolReference> toolReferences = matchedTools.stream()
			.limit(maxResults)
			.map(matched -> ToolReference.builder()
				.toolName(matched.entry().toolName())
				.relevanceScore(matched.score())
				.summary(matched.entry().toolDescription())
				.build())
			.toList();

		long searchTimeMs = System.currentTimeMillis() - startTime;

		return ToolSearchResponse.builder()
			.toolReferences(toolReferences)
			.totalMatches(toolReferences.size())
			.searchMetadata(SearchMetadata.builder()
				.searchType(this.getClass().getSimpleName())
				.query(originalQuery)
				.searchTimeMs(searchTimeMs)
				.build())
			.build();
	}

	/**
	 * Calculates a match score for a tool entry against the given pattern. The score is
	 * based on:
	 * <ul>
	 * <li>Number of matches found in tool name and description</li>
	 * <li>Higher weight for tool name matches (2x)</li>
	 * <li>Match position (earlier matches score higher)</li>
	 * </ul>
	 * @param pattern the compiled regex pattern
	 * @param entry the tool entry to match
	 * @return the calculated score, or 0 if no match
	 */
	private double calculateMatchScore(Pattern pattern, ToolEntry entry) {
		double score = 0.0;

		// Match against tool name (higher weight)
		Matcher nameMatcher = pattern.matcher(entry.toolName());
		int nameMatchCount = 0;
		int earliestNameMatchPos = Integer.MAX_VALUE;
		while (nameMatcher.find()) {
			nameMatchCount++;
			if (nameMatcher.start() < earliestNameMatchPos) {
				earliestNameMatchPos = nameMatcher.start();
			}
		}
		if (nameMatchCount > 0) {
			// Base score for name match + bonus for multiple matches + position bonus
			double positionBonus = 1.0 / (1.0 + earliestNameMatchPos * 0.1);
			score += 2.0 * nameMatchCount + positionBonus;
		}

		// Match against tool description (lower weight)
		Matcher descMatcher = pattern.matcher(entry.toolDescription());
		int descMatchCount = 0;
		int earliestDescMatchPos = Integer.MAX_VALUE;
		while (descMatcher.find()) {
			descMatchCount++;
			if (descMatcher.start() < earliestDescMatchPos) {
				earliestDescMatchPos = descMatcher.start();
			}
		}
		if (descMatchCount > 0) {
			// Base score for description match + bonus for multiple matches + position
			// bonus
			double positionBonus = 1.0 / (1.0 + earliestDescMatchPos * 0.1);
			score += 1.0 * descMatchCount + positionBonus * 0.5;
		}

		return score;
	}

	/**
	 * Returns the number of tools in the index for the specified session.
	 * @param sessionId the session ID
	 * @return tool count, or 0 if session not found
	 */
	public int size(String sessionId) {
		SessionIndex sessionIndex = this.sessionIndexes.get(sessionId);
		if (sessionIndex == null) {
			return 0;
		}
		return sessionIndex.size();
	}

	/**
	 * Returns the total number of tools across all session indexes.
	 * @return total tool count
	 */
	public int totalSize() {
		int total = 0;
		for (SessionIndex sessionIndex : this.sessionIndexes.values()) {
			total += sessionIndex.size();
		}
		return total;
	}

	/**
	 * Validates a regex pattern without performing a search.
	 * @param regexPattern the pattern to validate
	 * @return true if the pattern is valid, false otherwise
	 */
	public boolean isValidPattern(String regexPattern) {
		if (regexPattern == null || regexPattern.isBlank()) {
			return false;
		}
		if (regexPattern.length() > MAX_PATTERN_LENGTH) {
			return false;
		}
		try {
			Pattern.compile(regexPattern);
			return true;
		}
		catch (PatternSyntaxException e) {
			return false;
		}
	}

	@Override
	public void close() throws IOException {
		for (SessionIndex sessionIndex : this.sessionIndexes.values()) {
			sessionIndex.clear();
		}
		this.sessionIndexes.clear();
		logger.debug("Closed RegexToolIndex and cleared all session indexes");
	}

	/**
	 * Represents a tool entry stored in the index.
	 */
	private record ToolEntry(String id, String toolName, String toolDescription) {
	}

	/**
	 * Represents a matched tool with its match score.
	 */
	private record MatchedTool(ToolEntry entry, double score) {
	}

	/**
	 * Holds the tool entries for a specific session.
	 */
	private static class SessionIndex {

		final List<ToolEntry> tools = new CopyOnWriteArrayList<>();

		void addTool(ToolEntry entry) {
			this.tools.add(entry);
		}

		List<ToolEntry> getTools() {
			return Collections.unmodifiableList(this.tools);
		}

		void clear() {
			this.tools.clear();
		}

		int size() {
			return this.tools.size();
		}

	}

}
