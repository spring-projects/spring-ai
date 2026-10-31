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

package org.springframework.ai.tool.toolsearch.index.decision;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.decision.DecisionModel;
import org.springframework.ai.decision.DecisionOptions;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.answer.ChoiceAnswer;
import org.springframework.ai.decision.answer.YesNoAnswer;
import org.springframework.ai.decision.question.ChoiceQuestion;
import org.springframework.ai.decision.question.YesNoQuestion;
import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.ai.tool.toolsearch.ToolReference;
import org.springframework.ai.tool.toolsearch.ToolSearchRequest;
import org.springframework.ai.tool.toolsearch.ToolSearchResponse;
import org.springframework.ai.tool.toolsearch.ToolSearchResponse.SearchMetadata;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A {@link ToolIndex} that uses a {@link DecisionModel} to pick the tools that fit a
 * request, instead of matching keywords or embeddings.
 * <p>
 * Keyword and vector indexes always return their closest matches, even when none of the
 * tools can handle the request. This index can return nothing. For each search it asks
 * the model two questions in a single call:
 * <ul>
 * <li>Which tool best fits the request?</li>
 * <li>Does any of the tools fit the request at all?</li>
 * </ul>
 * If the model's confidence in the second answer is below the
 * {@link Builder#applicabilityThreshold(double) applicability threshold}, the search
 * returns no tools.
 * <p>
 * Each returned tool has the probability the model gave it as its relevance score. The
 * tool the model picked comes first, and the others follow in order of probability. When
 * the session has only one tool, the model is asked only whether that tool fits, and that
 * probability becomes its score.
 * <p>
 * All the tools in the session are sent to the model on every search. This works well for
 * tens of tools, but the prompt grows with each tool you add. For hundreds of tools,
 * shortlist them first with a cheaper index, such as a vector index, and let this index
 * choose from the shortlist.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class DecisionToolIndex implements ToolIndex {

	/** Name of the question that asks which tool best fits the request. */
	public static final String SELECTION_QUESTION = "best_tool";

	/** Name of the question that asks whether any tool fits the request. */
	public static final String APPLICABILITY_QUESTION = "any_tool_applies";

	/** Name of the input field that holds the user request. */
	public static final String REQUEST_FIELD = "user_request";

	/**
	 * Name of the input field that lists the tools with their names and descriptions. The
	 * model needs this list to tell whether any tool fits the request.
	 */
	public static final String TOOLS_FIELD = "available_tools";

	/**
	 * Name of the input field that holds the tool, with its name and description, when
	 * the session has only one tool.
	 */
	public static final String TOOL_FIELD = "tool";

	/** Maximum number of tools returned when the request does not set one. */
	private static final int DEFAULT_MAX_RESULTS = 10;

	private static final Log logger = LogFactory.getLog(DecisionToolIndex.class);

	private static final YesNoQuestion ANY_TOOL_APPLIES = YesNoQuestion.builder()
		.text("Does any tool listed in `" + TOOLS_FIELD + "` do what the `" + REQUEST_FIELD + "` needs? "
				+ "Judge whether some listed tool performs the action the request calls for. A request that is "
				+ "merely about a similar subject is not the same as one a listed tool can carry out.")
		.whenYes("Some tool in the list performs what the request asks for")
		.whenNo("The request needs an action that no tool in the list performs, or needs no tool at all")
		.build();

	private static final YesNoQuestion THE_TOOL_APPLIES = YesNoQuestion.builder()
		.text("Does the `" + TOOL_FIELD + "` serve the `" + REQUEST_FIELD + "`?")
		.whenYes("The tool does what the request needs")
		.whenNo("The request needs something this tool does not do")
		.build();

	private final DecisionModel decisionModel;

	private final double applicabilityThreshold;

	private final double minimumRelevance;

	private final @Nullable DecisionOptions options;

	private final Map<String, Map<String, ToolReference>> sessions = new ConcurrentHashMap<>();

	private DecisionToolIndex(DecisionModel decisionModel, double applicabilityThreshold, double minimumRelevance,
			@Nullable DecisionOptions options) {
		this.decisionModel = decisionModel;
		this.applicabilityThreshold = applicabilityThreshold;
		this.minimumRelevance = minimumRelevance;
		this.options = options;
	}

	@Override
	public void indexTool(String sessionId, ToolReference toolReference) {
		Assert.hasText(sessionId, "sessionId must not be empty");
		Assert.notNull(toolReference, "toolReference must not be null");
		Assert.hasText(toolReference.toolName(), "toolReference must name a tool");
		Assert.notNull(toolReference.summary(), "toolReference must have a summary");
		// A ToolIndex is a shared singleton: the advisor re-indexes a session while the
		// search tool reads it, possibly on another thread. The inner map is synchronized
		// rather than concurrent so it keeps the tools in the order they were indexed:
		// that order becomes the catalogue and the choice's options, which the model
		// reads positionally. Re-indexing a tool keeps its place.
		// Put inside compute, so a concurrent clearIndex cannot remove the session map
		// between creating it and filling it.
		this.sessions.compute(sessionId, (key, tools) -> {
			Map<String, ToolReference> session = (tools != null) ? tools
					: Collections.synchronizedMap(new LinkedHashMap<>());
			session.put(toolReference.toolName(), toolReference);
			return session;
		});
	}

	@Override
	public void clearIndex(String sessionId) {
		Assert.hasText(sessionId, "sessionId must not be empty");
		this.sessions.remove(sessionId);
	}

	@Override
	public ToolSearchResponse search(ToolSearchRequest toolSearchRequest) {
		Assert.notNull(toolSearchRequest, "toolSearchRequest must not be null");
		long start = System.currentTimeMillis();
		List<ToolReference> matches = matches(toolSearchRequest);
		return ToolSearchResponse.builder()
			.toolReferences(matches)
			.totalMatches(matches.size())
			.searchMetadata(SearchMetadata.builder()
				.searchType(getClass().getSimpleName())
				.query(toolSearchRequest.query())
				.searchTimeMs(System.currentTimeMillis() - start)
				.build())
			.build();
	}

	private List<ToolReference> matches(ToolSearchRequest toolSearchRequest) {
		List<ToolReference> candidates = candidates(toolSearchRequest);
		if (candidates.isEmpty()) {
			return List.of();
		}
		if (candidates.size() == 1) {
			// A choice needs something to choose between, so with one candidate the only
			// meaningful question is whether it applies at all.
			return singleCandidate(toolSearchRequest, candidates.get(0));
		}
		DecisionResponse response = select(toolSearchRequest, candidates);
		if (response.isRefused(APPLICABILITY_QUESTION) || response.isRefused(SELECTION_QUESTION)) {
			logger.debug("The decision model refused to pick a tool for '" + toolSearchRequest.query() + "'");
			return List.of();
		}
		if (!applies(response.getYesNo(APPLICABILITY_QUESTION), toolSearchRequest)) {
			return List.of();
		}
		ChoiceAnswer bestTool = response.getChoice(SELECTION_QUESTION);
		Map<String, ToolReference> byName = new LinkedHashMap<>();
		candidates.forEach(candidate -> byName.put(candidate.toolName(), candidate));
		List<ToolReference> matches = new ArrayList<>();
		int limit = limit(toolSearchRequest);
		for (String name : ranking(bestTool)) {
			if (matches.size() >= limit) {
				break;
			}
			ToolReference reference = byName.get(name);
			if (reference != null) {
				matches.add(scored(reference, probability(bestTool, name)));
			}
		}
		return matches;
	}

	/**
	 * Asks the model, in a single call, which tool best fits the request and whether any
	 * tool fits at all.
	 */
	private DecisionResponse select(ToolSearchRequest toolSearchRequest, List<ToolReference> candidates) {
		ChoiceQuestion.Builder selection = ChoiceQuestion.builder()
			.text("Which of these tools best serves the `" + REQUEST_FIELD + "`?");
		for (ToolReference candidate : candidates) {
			selection.option(candidate.toolName(), describe(candidate));
		}
		// Request first: the model reads the input in order.
		Map<String, @Nullable Object> fields = new LinkedHashMap<>();
		fields.put(REQUEST_FIELD, toolSearchRequest.query());
		fields.put(TOOLS_FIELD, catalogue(candidates));
		return this.decisionModel.call(DecisionPrompt.builder()
			.fields(fields)
			.question(SELECTION_QUESTION, selection.build())
			.question(APPLICABILITY_QUESTION, ANY_TOOL_APPLIES)
			.options(this.options)
			.build());
	}

	/**
	 * Returns whether the model is confident enough that some tool fits the request.
	 */
	private boolean applies(YesNoAnswer applicability, ToolSearchRequest toolSearchRequest) {
		if (applicability.isYes(this.applicabilityThreshold)) {
			return true;
		}
		if (logger.isDebugEnabled()) {
			logger.debug("No tool applies to '" + toolSearchRequest.query() + "' (applicability "
					+ applicability.getProbability() + " < " + this.applicabilityThreshold + ")");
		}
		return false;
	}

	/**
	 * Returns the names of the tools to return, in order.
	 * <p>
	 * The tool the model picked comes first. It is dropped only when the model gave it a
	 * probability below {@code minimumRelevance}. The other tools follow in order of
	 * probability, and only those with a probability above zero are kept. Without that
	 * check, a search would fill up to {@code maxResults} with tools the model ruled out.
	 */
	private List<String> ranking(ChoiceAnswer bestTool) {
		List<String> ranking = new ArrayList<>();
		Double chosenProbability = bestTool.getProbability();
		if (chosenProbability == null || chosenProbability >= this.minimumRelevance) {
			ranking.add(bestTool.getValue());
		}
		for (String name : bestTool.getRankedOptions()) {
			double probability = probability(bestTool, name);
			if (!name.equals(bestTool.getValue()) && probability > 0.0d && probability >= this.minimumRelevance) {
				ranking.add(name);
			}
		}
		return ranking;
	}

	private List<ToolReference> singleCandidate(ToolSearchRequest toolSearchRequest, ToolReference only) {
		Map<String, @Nullable Object> fields = new LinkedHashMap<>();
		fields.put(REQUEST_FIELD, toolSearchRequest.query());
		fields.put(TOOL_FIELD, entry(only));
		DecisionResponse response = this.decisionModel.call(DecisionPrompt.builder()
			.fields(fields)
			.question(APPLICABILITY_QUESTION, THE_TOOL_APPLIES)
			.options(this.options)
			.build());
		if (response.isRefused(APPLICABILITY_QUESTION)) {
			logger.debug("The decision model refused to judge the only tool for '" + toolSearchRequest.query() + "'");
			return List.of();
		}
		YesNoAnswer applicability = response.getYesNo(APPLICABILITY_QUESTION);
		if (!applies(applicability, toolSearchRequest)) {
			return List.of();
		}
		return List.of(scored(only, applicability.getProbability()));
	}

	private List<ToolReference> candidates(ToolSearchRequest toolSearchRequest) {
		String sessionId = toolSearchRequest.sessionId();
		if (!StringUtils.hasText(sessionId)) {
			logger.warn("No sessionId provided in ToolSearchRequest, returning empty results");
			return List.of();
		}
		Map<String, ToolReference> session = this.sessions.get(sessionId);
		if (session == null || session.isEmpty()) {
			return List.of();
		}
		List<ToolReference> candidates;
		synchronized (session) {
			// A synchronized map must be locked while it is iterated.
			candidates = new ArrayList<>(session.values());
		}
		String category = toolSearchRequest.categoryFilter();
		if (StringUtils.hasText(category)) {
			List<ToolReference> inCategory = candidates.stream()
				.filter(candidate -> matchesCategory(candidate, category))
				.toList();
			// The category is free text the model writes. It narrows the candidates when
			// it matches some; one that matches none must not hide every tool, so it is
			// ignored and the question is asked over the whole session.
			if (!inCategory.isEmpty()) {
				return inCategory;
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Ignoring category '" + category + "', which matches no indexed tool");
			}
		}
		return candidates;
	}

	private static boolean matchesCategory(ToolReference candidate, String category) {
		String term = category.toLowerCase(Locale.ROOT);
		return candidate.toolName().toLowerCase(Locale.ROOT).contains(term)
				|| candidate.summary().toLowerCase(Locale.ROOT).contains(term);
	}

	private static double probability(ChoiceAnswer answer, String option) {
		Double probability = answer.getProbability(option);
		return (probability != null) ? probability : 0.0d;
	}

	private static int limit(ToolSearchRequest toolSearchRequest) {
		Integer maxResults = toolSearchRequest.maxResults();
		return (maxResults == null || maxResults <= 0) ? DEFAULT_MAX_RESULTS : maxResults;
	}

	private static String describe(ToolReference reference) {
		String summary = reference.summary();
		return StringUtils.hasText(summary) ? summary : reference.toolName();
	}

	private static ToolReference scored(ToolReference reference, double relevance) {
		return ToolReference.builder()
			.toolName(reference.toolName())
			.summary(reference.summary())
			.relevanceScore(relevance)
			.build();
	}

	/**
	 * Lists the tools for the model input, so the model can tell whether any of them fits
	 * the request.
	 */
	private static List<Map<String, String>> catalogue(List<ToolReference> candidates) {
		List<Map<String, String>> catalogue = new ArrayList<>(candidates.size());
		for (ToolReference candidate : candidates) {
			catalogue.add(entry(candidate));
		}
		return catalogue;
	}

	/**
	 * Returns a tool as a model input entry: its name and what it does.
	 */
	private static Map<String, String> entry(ToolReference reference) {
		Map<String, String> entry = new LinkedHashMap<>();
		entry.put("name", reference.toolName());
		entry.put("does", describe(reference));
		return entry;
	}

	/**
	 * Creates a builder for a {@link DecisionToolIndex}.
	 * @param decisionModel the model that picks the tools
	 * @return a new builder
	 */
	public static Builder builder(DecisionModel decisionModel) {
		return new Builder(decisionModel);
	}

	/**
	 * Builder for {@link DecisionToolIndex}.
	 *
	 * @since 2.1.0
	 */
	public static final class Builder {

		/**
		 * Default applicability threshold.
		 */
		public static final double DEFAULT_APPLICABILITY_THRESHOLD = 0.5d;

		/**
		 * Default minimum relevance. At zero, every tool the model gave a probability
		 * above zero is returned.
		 */
		public static final double DEFAULT_MINIMUM_RELEVANCE = 0.0d;

		private final DecisionModel decisionModel;

		private double applicabilityThreshold = DEFAULT_APPLICABILITY_THRESHOLD;

		private double minimumRelevance = DEFAULT_MINIMUM_RELEVANCE;

		private @Nullable DecisionOptions options;

		private Builder(DecisionModel decisionModel) {
			Assert.notNull(decisionModel, "decisionModel must not be null");
			this.decisionModel = decisionModel;
		}

		/**
		 * Sets how confident the model must be that at least one tool fits the request.
		 * Below this, the search returns no tools. Raise it when calling the wrong tool
		 * is worse than calling none.
		 * @param applicabilityThreshold a value between 0 and 1
		 * @return this builder
		 */
		public Builder applicabilityThreshold(double applicabilityThreshold) {
			Assert.isTrue(applicabilityThreshold >= 0.0d && applicabilityThreshold <= 1.0d,
					"applicabilityThreshold must be between 0 and 1");
			this.applicabilityThreshold = applicabilityThreshold;
			return this;
		}

		/**
		 * Sets the lowest probability a tool needs to be returned. At zero, the tool the
		 * model picked and every other tool with a probability above zero are returned.
		 * @param minimumRelevance a value between 0 and 1
		 * @return this builder
		 */
		public Builder minimumRelevance(double minimumRelevance) {
			Assert.isTrue(minimumRelevance >= 0.0d && minimumRelevance <= 1.0d,
					"minimumRelevance must be between 0 and 1");
			this.minimumRelevance = minimumRelevance;
			return this;
		}

		/**
		 * Sets the options sent with every call to the model, such as the model name.
		 * @param options the options, or {@code null} to use the model's defaults
		 * @return this builder
		 */
		public Builder options(@Nullable DecisionOptions options) {
			this.options = options;
			return this;
		}

		public DecisionToolIndex build() {
			return new DecisionToolIndex(this.decisionModel, this.applicabilityThreshold, this.minimumRelevance,
					this.options);
		}

	}

}
