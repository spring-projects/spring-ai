/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.chat.client.advisor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.AdvisedRequest;
import org.springframework.ai.chat.client.RequestResponseAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.Content;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import reactor.core.publisher.Flux;

/**
 * Context for the question is retrieved from a Vector Store and added to the prompt's
 * user text.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class QuestionAnswerAdvisor implements RequestResponseAdvisor {

	private static final String DEFAULT_USER_TEXT_ADVISE = """
			Context information is below.
			---------------------
			{question_answer_context}
			---------------------
			Given the context and provided history information and not prior knowledge,
			reply to the user comment. If the answer is not in the context, inform
			the user that you can't answer the question.
			""";

	private final VectorStore vectorStore;

	private final String userTextAdvise;

	private final SearchRequest searchRequest;

	public static final String RETRIEVED_DOCUMENTS = "qa_retrieved_documents";

	public static final String FILTER_EXPRESSION = "qa_filter_expression";

	public QuestionAnswerAdvisor(VectorStore vectorStore) {
		this(vectorStore, SearchRequest.defaults(), DEFAULT_USER_TEXT_ADVISE);
	}

	public QuestionAnswerAdvisor(VectorStore vectorStore, SearchRequest searchRequest) {
		this(vectorStore, searchRequest, DEFAULT_USER_TEXT_ADVISE);
	}

	/**
	 * The QuestionAnswerAdvisor retrieves context information from a Vector Store and
	 * combines it with the user's text.
	 * @param vectorStore The vector store to use
	 * @param searchRequest The search request defined using the portable filter
	 * expression syntax
	 * @param userTextAdvise the user text to append to the existing user prompt. The text
	 * should contain a placeholder named "question_answer_context".
	 *
	 */
	public QuestionAnswerAdvisor(VectorStore vectorStore, SearchRequest searchRequest, String userTextAdvise) {

		Assert.notNull(vectorStore, "The vectorStore must not be null!");
		Assert.notNull(searchRequest, "The searchRequest must not be null!");
		Assert.hasText(userTextAdvise, "The userTextAdvise must not be empty!");

		this.vectorStore = vectorStore;
		this.searchRequest = searchRequest;
		this.userTextAdvise = userTextAdvise;
	}

	@Override
	public AdvisedRequest adviseRequest(AdvisedRequest request, Map<String, Object> context) {

		// 1. Advise the system text.
		String advisedUserText = request.userText() + System.lineSeparator() + this.userTextAdvise;

		var searchRequestToUse = SearchRequest.from(this.searchRequest)
			.withQuery(request.userText())
			.withFilterExpression(doGetFilterExpression(context));

		// 2. Search for similar documents in the vector store.
		List<Document> documents = this.vectorStore.similaritySearch(searchRequestToUse);

		context.put(RETRIEVED_DOCUMENTS, documents);

		// 3. Create the context from the documents.
		String documentContext = documents.stream()
			.map(Content::getContent)
			.collect(Collectors.joining(System.lineSeparator()));

		// 4. Advise the user parameters.
		Map<String, Object> advisedUserParams = new HashMap<>(request.userParams());
		advisedUserParams.put("question_answer_context", documentContext);

		AdvisedRequest advisedRequest = AdvisedRequest.from(request)
			.withUserText(advisedUserText)
			.withUserParams(advisedUserParams)
			.build();

		return advisedRequest;
	}

	@Override
	public ChatResponse adviseResponse(ChatResponse response, Map<String, Object> context) {
		response.getMetadata().put(RETRIEVED_DOCUMENTS, context.get(RETRIEVED_DOCUMENTS));
		return response;
	}

	@Override
	public Flux<ChatResponse> adviseResponse(Flux<ChatResponse> fluxResponse, Map<String, Object> context) {
		return fluxResponse.map(cr -> {
			cr.getMetadata().put(RETRIEVED_DOCUMENTS, context.get(RETRIEVED_DOCUMENTS));
			return cr;
		});
	}

	protected Filter.Expression doGetFilterExpression(Map<String, Object> context) {

		if (!context.containsKey(FILTER_EXPRESSION) || !StringUtils.hasText(context.get(FILTER_EXPRESSION).toString())) {
			return this.searchRequest.getFilterExpression();
		}
		return new FilterExpressionTextParser().parse(context.get(FILTER_EXPRESSION).toString());

	}

}
