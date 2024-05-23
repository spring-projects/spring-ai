package org.springframework.ai.evaluation;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.ChatOptionsBuilder;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.model.Content;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RelevancyEvaluator implements Evaluator {

	private static final String DEFAULT_EVALUATION_PROMPT_TEXT = """
			    Your task is to evaluate if the response for the query
			    is in line with the context information provided.\\n
			    You have two options to answer. Either YES/ NO.\\n
			    Answer - YES, if the response for the query
			    is in line with context information otherwise NO.\\n
			    Query: \\n {query}\\n
			    Response: \\n {response}\\n
			    Context: \\n {context}\\n
			    Answer: "
			""";

	private final ChatOptions chatOptions;

	private ChatModel chatModel;

	public RelevancyEvaluator(ChatModel chatModel) {
		this(chatModel, ChatOptionsBuilder.builder().build());
	}

	public RelevancyEvaluator(ChatModel chatModel, ChatOptions chatOptions) {
		this.chatModel = chatModel;
		this.chatOptions = chatOptions;
	}

	@Override
	public EvaluationResponse evaluate(EvaluationRequest evaluationRequest) {
		var query = doGetUserQuestion(evaluationRequest);
		var response = doGetResponse(evaluationRequest);
		var context = doGetSupportingData(evaluationRequest);

		var promptTemplate = new PromptTemplate(DEFAULT_EVALUATION_PROMPT_TEXT);
		Message message = promptTemplate
			.createMessage(Map.of("query", query, "response", response, "context", context));

		ChatResponse chatResponse = this.chatModel.call(new Prompt(message, this.chatOptions));

		var evaluationResponse = chatResponse.getResult().getOutput().getContent();
		boolean passing = false;
		float score = 0;
		if (evaluationResponse.toLowerCase().contains("yes")) {
			passing = true;
			score = 1;
		}

		return new EvaluationResponse(passing, score, "", Collections.emptyMap());
	}

	protected String doGetResponse(EvaluationRequest evaluationRequest) {
		return evaluationRequest.getChatResponse().getResult().getOutput().getContent();
	}

	protected String doGetSupportingData(EvaluationRequest evaluationRequest) {
		List<Content> data = evaluationRequest.getDataList();
		String supportingData = data.stream()
			.filter(node -> node != null && node.getContent() instanceof String)
			.map(node -> (Content) node)
			.map(Content::getContent)
			.collect(Collectors.joining(System.lineSeparator()));
		return supportingData;
	}

	protected String doGetUserQuestion(EvaluationRequest evaluationRequest) {
		List<Message> instructions = evaluationRequest.getPrompt().getInstructions();
		String userMessage = instructions.stream()
			.filter(m -> m.getMessageType() == MessageType.USER)
			.map(m -> m.getContent())
			.collect(Collectors.joining(System.lineSeparator()));
		return userMessage;
	}

}
