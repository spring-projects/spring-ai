package org.springframework.ai.wenxin.testutils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class AbstractIT {

	private static final Logger logger = LoggerFactory.getLogger(AbstractIT.class);

	@Autowired
	protected ChatModel chatModel;

	@Value("classpath:/prompts/eval/qa-evaluator-accurate-answer.st")
	protected Resource qaEvaluatorAccurateAnswerResource;

	@Value("classpath:/prompts/eval/qa-evaluator-not-related-message.st")
	protected Resource qaEvaluatorNotRelatedResource;

	@Value("classpath:/prompts/eval/qa-evaluator-fact-based-answer.st")
	protected Resource qaEvaluatorFactBasedAnswerResource;

	@Value("classpath:/prompts/eval/user-evaluator-message.st")
	protected Resource userEvaluatorResource;

	protected void evaluateQuestionAndAnswer(String question, ChatResponse response, boolean factBased)
			throws IOException {
		assertThat(response).isNotNull();

		String answer = response.getResult().getOutput().getContent();
		logger.info("Question: {}", question);
		logger.info("Answer: {}", answer);

		PromptTemplate userPromptTemplate = new PromptTemplate(userEvaluatorResource,
				Map.of("question", question, "answer", answer));
		AssistantMessage systemMessage;
		if (factBased) {
			systemMessage = new AssistantMessage(
					qaEvaluatorFactBasedAnswerResource.getContentAsString(StandardCharsets.UTF_8));
		}
		else {
			systemMessage = new AssistantMessage(
					qaEvaluatorAccurateAnswerResource.getContentAsString(StandardCharsets.UTF_8));
		}
		Message userMessage = userPromptTemplate.createMessage();
		Prompt prompt = new Prompt(List.of(userMessage, systemMessage, userMessage));
		String yesOrNo = chatModel.call(prompt).getResult().getOutput().getContent();
		logger.info("Is Answer related to question: {}", yesOrNo);
		if (yesOrNo.equalsIgnoreCase("no")) {
			AssistantMessage notRelatedSysMessage = new AssistantMessage(
					qaEvaluatorNotRelatedResource.getContentAsString(StandardCharsets.UTF_8));
			prompt = new Prompt(List.of(userMessage, notRelatedSysMessage));
			String reasonForFailure = chatModel.call(prompt).getResult().getOutput().getContent();
			fail(reasonForFailure);
		}
		else {
			logger.info("Answer is related to question.");
			assertThat(yesOrNo).isEqualTo("YES");
		}

	}

}
