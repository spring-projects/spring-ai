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
 * A {@link ToolIndex} that picks tools by asking a {@link DecisionModel}, rather than by
 * keyword or embedding proximity.
 *
 * <p>
 * The interesting difference is not accuracy, it is being able to say <em>no</em>. A
 * Lucene or vector index ranks whatever it holds and hands back a best match; nothing in
 * either mechanism can report that the query is about something the toolset does not do.
 * The same is true of a choice question on its own, since choice probabilities are a
 * distribution over the options and therefore always sum to one: something is always
 * ranked first. So this index asks a second, independent question: <em>does any of these
 * tools serve the request at all?</em> Below
 * {@link Builder#applicabilityThreshold(double)} the result is empty, and the model is
 * told about no tools instead of a plausible wrong one.
 *
 * <p>
 * Both questions ride in a single prompt, so a search costs one call regardless of how
 * many tools are indexed.
 *
 * <p>
 * Each match carries its share of the selection distribution as its relevance score, the
 * chosen tool first. A session holding a single tool has no distribution to share, so it
 * is asked only whether that tool applies, and the applicability probability is the
 * score.
 *
 * <p>
 * Every indexed tool becomes an option on the choice, and the whole set is sent on each
 * search. That is fine for the tens of tools a session realistically holds and wrong for
 * thousands; past a few hundred, put a cheap index in front of this one and let the
 * decision model choose among its shortlist.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class DecisionToolIndex implements ToolIndex {

	/** The question name carrying the tool selection. */
	public static final String SELECTION_QUESTION = "best_tool";

	/** The question name carrying the "any tool at all" check. */
	public static final String APPLICABILITY_QUESTION = "any_tool_applies";

	/** The input field carrying the request. */
	public static final String REQUEST_FIELD = "user_request";

	/**
	 * The input field listing what the tools do. The applicability question cannot be
	 * answered without it: "does any tool serve this request" is meaningless unless the
	 * tools are in the input, and a model asked it blind will guess.
	 */
	public static final String TOOLS_FIELD = "available_tools";

	/** The input field carrying the only tool, when the session holds just one. */
	public static final String TOOL_FIELD = "tool";

	/** How many tools a search returns at most when the request does not say. */
	private static final int DEFAULT_MAX_RESULTS = 10;

	private static final Log logger = LogFactory.getLog(DecisionToolIndex.class);

	private static final YesNoQuestion ANY_TOOL_APPLIES = YesNoQuestion.builder()
		.text("Does any tool listed in `available_tools` do what the `user_request` needs? Judge whether some "
				+ "listed tool performs the action the request calls for. A request that is merely about a "
				+ "similar subject is not the same as one a listed tool can carry out.")
		.whenYes("Some tool in the list performs what the request asks for")
		.whenNo("The request needs an action that no tool in the list performs, or needs no tool at all")
		.build();

	private static final YesNoQuestion THE_TOOL_APPLIES = YesNoQuestion.builder()
		.text("Does the `tool` serve the `user_request`?")
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

		ChoiceQuestion.Builder selection = ChoiceQuestion.builder()
			.text("Which of these tools best serves the `user_request`?");
		for (ToolReference candidate : candidates) {
			selection.option(candidate.toolName(), describe(candidate));
		}
		// Request first: the model reads the input in order.
		Map<String, @Nullable Object> fields = new LinkedHashMap<>();
		fields.put(REQUEST_FIELD, toolSearchRequest.query());
		fields.put(TOOLS_FIELD, catalogue(candidates));
		DecisionResponse response = this.decisionModel.call(DecisionPrompt.builder()
			.fields(fields)
			.question(SELECTION_QUESTION, selection.build())
			.question(APPLICABILITY_QUESTION, ANY_TOOL_APPLIES)
			.options(this.options)
			.build());

		if (response.isRefused(APPLICABILITY_QUESTION) || response.isRefused(SELECTION_QUESTION)) {
			logger.debug("The decision model refused to pick a tool for '" + toolSearchRequest.query() + "'");
			return List.of();
		}
		double applicability = response.getYesNo(APPLICABILITY_QUESTION).getProbability();
		if (applicability < this.applicabilityThreshold) {
			if (logger.isDebugEnabled()) {
				logger.debug("No tool applies to '" + toolSearchRequest.query() + "' (applicability " + applicability
						+ " < " + this.applicabilityThreshold + ")");
			}
			return List.of();
		}

		// The chosen tool is the model's answer, so it always leads the ranking, and the
		// floor applies to it only when the model reported its probability. The other
		// tools follow in descending probability. One the model gave no probability at
		// all is never a match, even with minimumRelevance at zero: otherwise maxResults
		// is filled up with tools the model ruled out, in whatever order the response
		// lists the ties.
		ChoiceAnswer bestTool = response.getChoice(SELECTION_QUESTION);
		List<String> ranked = new ArrayList<>();
		Double chosenProbability = bestTool.getProbability();
		if (chosenProbability == null || chosenProbability >= this.minimumRelevance) {
			ranked.add(bestTool.getValue());
		}
		for (String name : bestTool.getRankedOptions()) {
			double probability = probability(bestTool, name);
			if (!name.equals(bestTool.getValue()) && probability > 0.0d && probability >= this.minimumRelevance) {
				ranked.add(name);
			}
		}

		Map<String, ToolReference> byName = new LinkedHashMap<>();
		candidates.forEach(candidate -> byName.put(candidate.toolName(), candidate));
		List<ToolReference> matches = new ArrayList<>();
		int limit = limit(toolSearchRequest);
		for (String name : ranked) {
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

	private List<ToolReference> singleCandidate(ToolSearchRequest toolSearchRequest, ToolReference only) {
		Map<String, @Nullable Object> fields = new LinkedHashMap<>();
		fields.put(REQUEST_FIELD, toolSearchRequest.query());
		fields.put(TOOL_FIELD, describe(only));
		DecisionResponse response = this.decisionModel.call(DecisionPrompt.builder()
			.fields(fields)
			.question(APPLICABILITY_QUESTION, THE_TOOL_APPLIES)
			.options(this.options)
			.build());
		if (response.isRefused(APPLICABILITY_QUESTION)) {
			logger.debug("The decision model refused to judge the only tool for '" + toolSearchRequest.query() + "'");
			return List.of();
		}
		double applicability = response.getYesNo(APPLICABILITY_QUESTION).getProbability();
		return (applicability < this.applicabilityThreshold) ? List.of() : List.of(scored(only, applicability));
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
		String needle = category.toLowerCase(Locale.ROOT);
		return candidate.toolName().toLowerCase(Locale.ROOT).contains(needle)
				|| candidate.summary().toLowerCase(Locale.ROOT).contains(needle);
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
	 * The tools as data in the input, so the applicability question has something to
	 * judge the request against.
	 */
	private static List<Map<String, String>> catalogue(List<ToolReference> candidates) {
		List<Map<String, String>> catalogue = new ArrayList<>(candidates.size());
		for (ToolReference candidate : candidates) {
			Map<String, String> entry = new LinkedHashMap<>();
			entry.put("name", candidate.toolName());
			entry.put("does", describe(candidate));
			catalogue.add(entry);
		}
		return catalogue;
	}

	/**
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
		 * How sure the model must be that some tool fits, unless configured otherwise.
		 */
		public static final double DEFAULT_APPLICABILITY_THRESHOLD = 0.5d;

		private final DecisionModel decisionModel;

		private double applicabilityThreshold = DEFAULT_APPLICABILITY_THRESHOLD;

		private double minimumRelevance = 0.0d;

		private @Nullable DecisionOptions options;

		private Builder(DecisionModel decisionModel) {
			Assert.notNull(decisionModel, "decisionModel must not be null");
			this.decisionModel = decisionModel;
		}

		/**
		 * How sure the model must be that <em>some</em> tool fits before any is returned.
		 * Raise it where calling the wrong tool is worse than calling none.
		 * @param applicabilityThreshold the threshold, between 0 and 1
		 * @return this builder
		 */
		public Builder applicabilityThreshold(double applicabilityThreshold) {
			Assert.isTrue(applicabilityThreshold >= 0.0d && applicabilityThreshold <= 1.0d,
					"applicabilityThreshold must be between 0 and 1");
			this.applicabilityThreshold = applicabilityThreshold;
			return this;
		}

		/**
		 * Drops tools whose share of the selection distribution is below this. Left at
		 * zero, every candidate the model gives any probability is returned in ranked
		 * order; a tool with no probability is never returned.
		 * @param minimumRelevance the threshold, between 0 and 1
		 * @return this builder
		 */
		public Builder minimumRelevance(double minimumRelevance) {
			Assert.isTrue(minimumRelevance >= 0.0d && minimumRelevance <= 1.0d,
					"minimumRelevance must be between 0 and 1");
			this.minimumRelevance = minimumRelevance;
			return this;
		}

		/**
		 * Options sent with every prompt, such as the model to use.
		 * @param options the options, or {@code null} for the model's defaults
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
