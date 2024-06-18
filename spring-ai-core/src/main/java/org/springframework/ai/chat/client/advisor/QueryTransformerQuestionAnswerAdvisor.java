package org.springframework.ai.chat.client.advisor;

import org.springframework.ai.chat.client.AdvisedRequest;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Context for the question is retrieved from a Vector Store with Query Transformer and
 * added to the prompt's user text.
 *
 * @author Zhiyong Li
 * @since 1.0.0
 */
public class QueryTransformerQuestionAnswerAdvisor extends QuestionAnswerAdvisor {

	private static final String QUERY_TRANSFER = """
			Genereate 1 query according to customer input,it will be used to retrieve relevant documents.
			The query should follow the below requirements:
			{query_requirement}
			Without enumerations, hyphens, or any additional formatting!
			""";

	private static final String QUERY_REQUIREMENT = "query_requirement";

	protected ChatModel chatModel;

	protected String queryPrompt;

	public QueryTransformerQuestionAnswerAdvisor(VectorStore vectorStore, ChatModel chatModel,
			String queryRequirement) {
		super(vectorStore);
		this.chatModel = chatModel;
		if (StringUtils.hasLength(queryRequirement)) {
			this.queryPrompt = new PromptTemplate(QUERY_TRANSFER).render(Map.of(QUERY_REQUIREMENT, queryRequirement));
		}
	}

	public QueryTransformerQuestionAnswerAdvisor(VectorStore vectorStore, SearchRequest searchRequest,
			ChatModel chatModel, String queryRequirement) {
		super(vectorStore, searchRequest);
		this.chatModel = chatModel;
		if (StringUtils.hasLength(queryRequirement)) {
			this.queryPrompt = new PromptTemplate(QUERY_TRANSFER).render(Map.of(QUERY_REQUIREMENT, queryRequirement));
		}
	}

	public QueryTransformerQuestionAnswerAdvisor(VectorStore vectorStore, SearchRequest searchRequest,
			String userTextAdvise, ChatModel chatModel, String queryRequirement) {
		super(vectorStore, searchRequest, userTextAdvise);
		this.chatModel = chatModel;
		if (StringUtils.hasLength(queryRequirement)) {
			this.queryPrompt = new PromptTemplate(QUERY_TRANSFER).render(Map.of(QUERY_REQUIREMENT, queryRequirement));
		}
	}

	@Override
	public AdvisedRequest adviseRequest(AdvisedRequest request, Map<String, Object> context) {
		String originalUserText = request.userText();
		String queryRequirement = (String) context.get(QUERY_REQUIREMENT);
		if (StringUtils.hasLength(queryRequirement)) {
			queryPrompt = new PromptTemplate(QUERY_TRANSFER).render(Map.of(QUERY_REQUIREMENT, queryRequirement));
		}
		if (StringUtils.hasLength(queryPrompt)) {
			String processedMessage = chatModel.call(queryPrompt);
			AdvisedRequest processedRequest = AdvisedRequest.from(request).withUserText(processedMessage).build();
			request = super.adviseRequest(processedRequest, context);
			return AdvisedRequest.from(request).withUserText(originalUserText).build();
		}
		else {
			return request;
		}
	}

}
