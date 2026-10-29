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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.decision.DecisionModel;
import org.springframework.ai.decision.DecisionOptions;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.answer.ChoiceAnswer;
import org.springframework.ai.decision.answer.RefusalAnswer;
import org.springframework.ai.decision.answer.YesNoAnswer;
import org.springframework.ai.decision.input.FieldsInput;
import org.springframework.ai.decision.question.ChoiceQuestion;
import org.springframework.ai.decision.question.YesNoQuestion;
import org.springframework.ai.tool.toolsearch.ToolReference;
import org.springframework.ai.tool.toolsearch.ToolSearchRequest;
import org.springframework.ai.tool.toolsearch.ToolSearchResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DecisionToolIndex}. The model is a stub that records the prompt
 * and answers with the probabilities each test sets.
 *
 * @author Christian Tzolov
 */
class DecisionToolIndexTests {

	private static final String SESSION = "session-1";

	private final List<DecisionPrompt> prompts = new ArrayList<>();

	private DecisionToolIndex index;

	@BeforeEach
	void indexThreeTools() {
		this.index = DecisionToolIndex.builder(answering(0.95d, 0.90d, 0.07d, 0.03d)).build();
		this.index.indexTools(SESSION,
				List.of(tool("currentWeather", "Returns the current weather for a place"),
						tool("sendEmail", "Sends an email to a recipient"),
						tool("createInvoice", "Creates an invoice for a customer")));
	}

	@Test
	void ranksTheToolsBySelectionProbability() {
		ToolSearchResponse response = this.index
			.search(new ToolSearchRequest(SESSION, "what is it like outside in Paris", null, null));

		assertThat(response.toolReferences()).extracting(ToolReference::toolName)
			.containsExactly("currentWeather", "sendEmail", "createInvoice");
		assertThat(response.toolReferences().get(0).relevanceScore()).isEqualTo(0.90d);
		assertThat(response.toolReferences().get(0).summary()).isEqualTo("Returns the current weather for a place");
		assertThat(response.totalMatches()).isEqualTo(3);
		assertThat(response.searchMetadata()).isNotNull();
		assertThat(response.searchMetadata().searchType()).isEqualTo("DecisionToolIndex");
		assertThat(response.searchMetadata().query()).isEqualTo("what is it like outside in Paris");
		assertThat(response.searchMetadata().searchTimeMs()).isNotNull();
	}

	@Test
	void sendsTheRequestThenTheCatalogueAndAsksBothQuestions() {
		this.index.search(new ToolSearchRequest(SESSION, "what is it like outside in Paris", null, null));

		assertThat(this.prompts).hasSize(1);
		DecisionPrompt prompt = this.prompts.get(0);
		assertThat(prompt.getInstructions()).isInstanceOfSatisfying(FieldsInput.class, input -> {
			assertThat(input.getFields().keySet()).containsExactly(DecisionToolIndex.REQUEST_FIELD,
					DecisionToolIndex.TOOLS_FIELD);
			assertThat(input.getFields()).containsEntry(DecisionToolIndex.REQUEST_FIELD,
					"what is it like outside in Paris");
			// The tools in the order they were indexed, as a catalogue the applicability
			// question can read.
			assertThat(input.getFields().get(DecisionToolIndex.TOOLS_FIELD))
				.isEqualTo(List.of(catalogueEntry("currentWeather", "Returns the current weather for a place"),
						catalogueEntry("sendEmail", "Sends an email to a recipient"),
						catalogueEntry("createInvoice", "Creates an invoice for a customer")));
		});
		// Both questions in one prompt, the selection first, and the tools as its options
		// in the same order.
		assertThat(prompt.getQuestions().keySet()).containsExactly(DecisionToolIndex.SELECTION_QUESTION,
				DecisionToolIndex.APPLICABILITY_QUESTION);
		assertThat(prompt.getQuestions().get(DecisionToolIndex.SELECTION_QUESTION))
			.isInstanceOfSatisfying(ChoiceQuestion.class, choice -> {
				assertThat(choice.getOptionNames()).containsExactly("currentWeather", "sendEmail", "createInvoice");
				assertThat(choice.getOptions()).containsEntry("sendEmail", "Sends an email to a recipient");
			});
		assertThat(prompt.getQuestions().get(DecisionToolIndex.APPLICABILITY_QUESTION))
			.isInstanceOf(YesNoQuestion.class);
		assertThat(prompt.getOptions()).isNull();
	}

	@Test
	void returnsNothingWhenNoToolAppliesEvenThoughOneStillRanksFirst() {
		// This is the whole argument for the class. Choice probabilities sum to 1, so
		// createInvoice "wins" a query about none of these tools; only the independent
		// applicability question can say that the winner should not be used.
		DecisionToolIndex index = indexed(answering(0.04d, 0.20d, 0.25d, 0.55d));

		ToolSearchResponse response = index
			.search(new ToolSearchRequest(SESSION, "translate this into Welsh", null, null));

		assertThat(response.toolReferences()).isEmpty();
		assertThat(response.totalMatches()).isZero();
	}

	@Test
	void honoursMaxResults() {
		ToolSearchResponse response = this.index.search(new ToolSearchRequest(SESSION, "weather in Paris", 1, null));

		assertThat(response.toolReferences()).extracting(ToolReference::toolName).containsExactly("currentWeather");
	}

	@Test
	void neverPadsMaxResultsWithToolsTheModelGaveNoProbability() {
		// A clear-cut selection: every other tool at zero. With the default relevance
		// floor of zero they must not come back as filler up to maxResults.
		DecisionToolIndex index = indexed(answering(0.95d, 1.0d, 0.0d, 0.0d));

		ToolSearchResponse response = index.search(new ToolSearchRequest(SESSION, "weather in Paris", 5, null));

		assertThat(response.toolReferences()).extracting(ToolReference::toolName).containsExactly("currentWeather");
		assertThat(response.totalMatches()).isEqualTo(1);
	}

	@Test
	void dropsToolsBelowTheRelevanceFloor() {
		DecisionToolIndex picky = indexed(
				DecisionToolIndex.builder(answering(0.95d, 0.90d, 0.07d, 0.03d)).minimumRelevance(0.5d).build());

		ToolSearchResponse response = picky.search(new ToolSearchRequest(SESSION, "weather in Paris", null, null));

		assertThat(response.toolReferences()).extracting(ToolReference::toolName).containsExactly("currentWeather");
	}

	@Test
	void returnsNothingWhenNoToolClearsTheRelevanceFloor() {
		// Some tool applies, but none stands out. The fallback for a response without
		// probabilities must not hand back the top label here.
		DecisionToolIndex picky = indexed(
				DecisionToolIndex.builder(answering(0.95d, 0.40d, 0.35d, 0.25d)).minimumRelevance(0.5d).build());

		ToolSearchResponse response = picky.search(new ToolSearchRequest(SESSION, "do something", null, null));

		assertThat(response.toolReferences()).isEmpty();
	}

	@Test
	void aSelectionWithoutProbabilitiesStillYieldsTheChosenTool() {
		// The ranking comes from the probability map, which a provider may omit. Ranking
		// an empty map returns nothing, so without a fallback a selection the model
		// plainly did make would be reported as "no tool matched".
		DecisionModel bare = prompt -> DecisionResponse.builder(prompt)
			.answer(DecisionToolIndex.SELECTION_QUESTION, ChoiceAnswer.builder().value("currentWeather").build())
			.answer(DecisionToolIndex.APPLICABILITY_QUESTION, yes(0.95d))
			.build();

		ToolSearchResponse response = indexed(bare)
			.search(new ToolSearchRequest(SESSION, "what is it like outside in Paris", null, null));

		assertThat(response.toolReferences()).extracting(ToolReference::toolName).containsExactly("currentWeather");
		assertThat(response.toolReferences().get(0).relevanceScore()).isEqualTo(0.0d);
	}

	@Test
	void theChosenToolLeadsTheRankingWhenTheProbabilitiesSkipIt() {
		// A provider may report only the top probabilities, or leave the pick out of the
		// map. The pick is still the model's answer.
		DecisionModel truncated = prompt -> DecisionResponse.builder(prompt)
			.answer(DecisionToolIndex.SELECTION_QUESTION,
					ChoiceAnswer.builder()
						.value("createInvoice")
						.probabilities(Map.of("currentWeather", 0.6d, "sendEmail", 0.4d))
						.build())
			.answer(DecisionToolIndex.APPLICABILITY_QUESTION, yes(0.9d))
			.build();

		ToolSearchResponse response = indexed(truncated)
			.search(new ToolSearchRequest(SESSION, "bill the customer", null, null));

		assertThat(response.toolReferences()).extracting(ToolReference::toolName)
			.containsExactly("createInvoice", "currentWeather", "sendEmail");
	}

	@Test
	void theChosenToolIsKeptEvenAtZeroProbabilityButNotBelowTheFloor() {
		// A provider may round its own pick's share down to zero. The pick is still the
		// answer, unless a floor is set and the reported share is below it.
		DecisionModel rounding = prompt -> DecisionResponse.builder(prompt)
			.answer(DecisionToolIndex.SELECTION_QUESTION,
					ChoiceAnswer.builder()
						.value("createInvoice")
						.probabilities(Map.of("createInvoice", 0.0d, "currentWeather", 1.0d))
						.build())
			.answer(DecisionToolIndex.APPLICABILITY_QUESTION, yes(0.9d))
			.build();

		assertThat(indexed(rounding).search(new ToolSearchRequest(SESSION, "bill the customer", null, null))
			.toolReferences()).extracting(ToolReference::toolName).containsExactly("createInvoice", "currentWeather");
		assertThat(indexed(DecisionToolIndex.builder(rounding).minimumRelevance(0.5d).build())
			.search(new ToolSearchRequest(SESSION, "bill the customer", null, null))
			.toolReferences()).extracting(ToolReference::toolName).containsExactly("currentWeather");
	}

	@Test
	void returnsAtMostTenToolsWhenTheRequestDoesNotSay() {
		List<ToolReference> many = new ArrayList<>();
		Map<String, Double> probabilities = new LinkedHashMap<>();
		for (int i = 0; i < 12; i++) {
			many.add(tool("tool" + i, "Does thing " + i));
			probabilities.put("tool" + i, 1.0d / 12);
		}
		DecisionModel spread = prompt -> DecisionResponse.builder(prompt)
			.answer(DecisionToolIndex.SELECTION_QUESTION,
					ChoiceAnswer.builder().value("tool0").probabilities(probabilities).build())
			.answer(DecisionToolIndex.APPLICABILITY_QUESTION, yes(0.9d))
			.build();
		DecisionToolIndex index = DecisionToolIndex.builder(spread).build();
		index.indexTools(SESSION, many);

		assertThat(index.search(new ToolSearchRequest(SESSION, "do a thing", null, null)).toolReferences()).hasSize(10);
		assertThat(index.search(new ToolSearchRequest(SESSION, "do a thing", 0, null)).toolReferences()).hasSize(10);
	}

	@Test
	void asksOnlyAboutApplicabilityWhenTheSessionHoldsASingleTool() {
		// A choice between one option is not a question worth asking.
		DecisionToolIndex single = DecisionToolIndex
			.builder(recording(prompt -> DecisionResponse.builder(prompt)
				.answer(DecisionToolIndex.APPLICABILITY_QUESTION, yes(0.93d))
				.build()))
			.build();
		single.indexTool("solo", tool("currentWeather", "Returns the current weather for a place"));

		ToolSearchResponse response = single.search(new ToolSearchRequest("solo", "weather in Paris", null, null));

		assertThat(response.toolReferences()).extracting(ToolReference::toolName).containsExactly("currentWeather");
		assertThat(response.toolReferences().get(0).relevanceScore()).isEqualTo(0.93d);
		DecisionPrompt prompt = this.prompts.get(0);
		assertThat(prompt.getQuestions().keySet()).containsExactly(DecisionToolIndex.APPLICABILITY_QUESTION);
		assertThat(((FieldsInput) prompt.getInstructions()).getFields().keySet())
			.containsExactly(DecisionToolIndex.REQUEST_FIELD, DecisionToolIndex.TOOL_FIELD);
	}

	@Test
	void aSingleToolThatDoesNotApplyIsNotReturned() {
		DecisionToolIndex single = DecisionToolIndex
			.builder(prompt -> DecisionResponse.builder(prompt)
				.answer(DecisionToolIndex.APPLICABILITY_QUESTION, yes(0.2d))
				.build())
			.build();
		single.indexTool("solo", tool("currentWeather", "Returns the current weather for a place"));

		assertThat(single.search(new ToolSearchRequest("solo", "send a mail", null, null)).toolReferences()).isEmpty();
	}

	@Test
	void anUnknownClearedOrBlankSessionCostsNoCall() {
		assertThat(this.index.search(new ToolSearchRequest("no-such-session", "anything", null, null)).toolReferences())
			.isEmpty();
		assertThat(this.index.search(new ToolSearchRequest("", "anything", null, null)).toolReferences()).isEmpty();
		this.index.clearIndex(SESSION);
		assertThat(this.index.search(new ToolSearchRequest(SESSION, "anything", null, null)).toolReferences())
			.isEmpty();
		assertThat(this.prompts).isEmpty();
	}

	@Test
	void narrowsCandidatesByCategoryBeforeAsking() {
		DecisionToolIndex billing = DecisionToolIndex
			.builder(recording(prompt -> DecisionResponse.builder(prompt)
				.answer(DecisionToolIndex.SELECTION_QUESTION,
						ChoiceAnswer.builder()
							.value("createInvoice")
							.probabilities(Map.of("createInvoice", 0.8d, "remindCustomer", 0.2d))
							.build())
				.answer(DecisionToolIndex.APPLICABILITY_QUESTION, yes(0.9d))
				.build()))
			.build();
		billing.indexTools(SESSION,
				List.of(tool("currentWeather", "Returns the current weather for a place"),
						tool("createInvoice", "Creates an invoice for a customer"),
						tool("remindCustomer", "Emails a reminder about an unpaid invoice")));

		ToolSearchResponse response = billing
			.search(new ToolSearchRequest(SESSION, "bill the customer", null, "invoice"));

		assertThat(response.toolReferences()).extracting(ToolReference::toolName)
			.containsExactly("createInvoice", "remindCustomer");
		// Only the two tools matching "invoice" reach the choice.
		ChoiceQuestion choice = (ChoiceQuestion) this.prompts.get(0)
			.getQuestions()
			.get(DecisionToolIndex.SELECTION_QUESTION);
		assertThat(choice.getOptionNames()).containsExactly("createInvoice", "remindCustomer");
	}

	@Test
	void ignoresACategoryThatMatchesNoTool() {
		// The category is free text the model writes; one it invents must not hide every
		// tool.
		DecisionToolIndex index = indexed(answering(0.95d, 0.05d, 0.05d, 0.90d));

		ToolSearchResponse response = index
			.search(new ToolSearchRequest(SESSION, "bill the customer", null, "finance"));

		assertThat(response.toolReferences().get(0).toolName()).isEqualTo("createInvoice");
		ChoiceQuestion choice = (ChoiceQuestion) this.prompts.get(0)
			.getQuestions()
			.get(DecisionToolIndex.SELECTION_QUESTION);
		assertThat(choice.getOptionNames()).containsExactly("currentWeather", "sendEmail", "createInvoice");
	}

	@Test
	void aRefusalReturnsNoTools() {
		DecisionModel refusing = prompt -> DecisionResponse.builder(prompt)
			.answer(DecisionToolIndex.SELECTION_QUESTION, RefusalAnswer.builder().reason("policy").build())
			.answer(DecisionToolIndex.APPLICABILITY_QUESTION, yes(0.9d))
			.build();

		ToolSearchResponse response = indexed(refusing).search(new ToolSearchRequest(SESSION, "anything", null, null));

		assertThat(response.toolReferences()).isEmpty();
		assertThat(response.totalMatches()).isZero();
	}

	@Test
	void reindexingAToolKeepsItsPlaceAndUpdatesItsSummary() {
		this.index.indexTool(SESSION, tool("sendEmail", "Sends an email, with attachments"));

		this.index.search(new ToolSearchRequest(SESSION, "mail the report", null, null));

		ChoiceQuestion choice = (ChoiceQuestion) this.prompts.get(0)
			.getQuestions()
			.get(DecisionToolIndex.SELECTION_QUESTION);
		assertThat(choice.getOptionNames()).containsExactly("currentWeather", "sendEmail", "createInvoice");
		assertThat(choice.getOptions()).containsEntry("sendEmail", "Sends an email, with attachments");
	}

	@Test
	void sendsTheConfiguredOptions() {
		DecisionOptions options = DecisionOptions.builder().model("judge-1").build();
		DecisionToolIndex index = indexed(
				DecisionToolIndex.builder(answering(0.95d, 0.90d, 0.07d, 0.03d)).options(options).build());

		index.search(new ToolSearchRequest(SESSION, "weather", null, null));

		assertThat(this.prompts.get(0).getOptions()).isEqualTo(options);
	}

	@Test
	void rejectsThresholdsOutsideTheUnitInterval() {
		DecisionModel model = answering(0.95d, 0.90d, 0.07d, 0.03d);

		assertThatThrownBy(() -> DecisionToolIndex.builder(model).applicabilityThreshold(1.5d))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> DecisionToolIndex.builder(model).minimumRelevance(-0.1d))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private DecisionToolIndex indexed(DecisionModel model) {
		return indexed(DecisionToolIndex.builder(model).build());
	}

	private static DecisionToolIndex indexed(DecisionToolIndex index) {
		index.indexTools(SESSION,
				List.of(tool("currentWeather", "Returns the current weather for a place"),
						tool("sendEmail", "Sends an email to a recipient"),
						tool("createInvoice", "Creates an invoice for a customer")));
		return index;
	}

	private DecisionModel recording(DecisionModel delegate) {
		return prompt -> {
			this.prompts.add(prompt);
			return delegate.call(prompt);
		};
	}

	/**
	 * A model that always answers with the given applicability and the given share for
	 * each of the three tools, naming the likeliest as the choice.
	 */
	private DecisionModel answering(double applicability, double weather, double email, double invoice) {
		return recording(prompt -> {
			Map<String, Double> probabilities = new LinkedHashMap<>();
			probabilities.put("currentWeather", weather);
			probabilities.put("sendEmail", email);
			probabilities.put("createInvoice", invoice);
			String likeliest = probabilities.entrySet()
				.stream()
				.max(Map.Entry.comparingByValue())
				.map(Map.Entry::getKey)
				.orElseThrow();
			return DecisionResponse.builder(prompt)
				.answer(DecisionToolIndex.SELECTION_QUESTION,
						ChoiceAnswer.builder().value(likeliest).probabilities(probabilities).build())
				.answer(DecisionToolIndex.APPLICABILITY_QUESTION, yes(applicability))
				.build();
		});
	}

	private static YesNoAnswer yes(double probability) {
		return YesNoAnswer.builder().probability(probability).build();
	}

	private static ToolReference tool(String name, String summary) {
		return ToolReference.builder().toolName(name).summary(summary).build();
	}

	private static Map<String, String> catalogueEntry(String name, String does) {
		Map<String, String> entry = new LinkedHashMap<>();
		entry.put("name", name);
		entry.put("does", does);
		return entry;
	}

}
