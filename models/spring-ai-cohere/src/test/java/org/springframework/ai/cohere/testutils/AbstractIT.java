/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.cohere.testutils;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.cohere.api.CohereApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public abstract class AbstractIT {

	private static final Logger logger = LoggerFactory.getLogger(AbstractIT.class);

	@Autowired
	protected ChatModel chatModel;

	@Autowired
	protected CohereApi cohereApi;

	@Autowired
	protected StreamingChatModel streamingChatModel;

	@Value("classpath:/prompts/eval/qa-evaluator-accurate-answer.st")
	protected Resource qaEvaluatorAccurateAnswerResource;

	@Value("classpath:/prompts/eval/qa-evaluator-not-related-message.st")
	protected Resource qaEvaluatorNotRelatedResource;

	@Value("classpath:/prompts/eval/qa-evaluator-fact-based-answer.st")
	protected Resource qaEvaluatorFactBasedAnswerResource;

	@Value("classpath:/prompts/eval/user-evaluator-message.st")
	protected Resource userEvaluatorResource;

	@Value("classpath:/prompts/system-message.st")
	protected Resource systemResource;

	protected void evaluateQuestionAndAnswer(String question, ChatResponse response, boolean factBased) {
		assertThat(response).isNotNull();
		String answer = response.getResult().getOutput().getText();
		logger.info("Question: {}", question);
		logger.info("Answer:{}", answer);
		PromptTemplate userPromptTemplate = PromptTemplate.builder()
			.resource(this.userEvaluatorResource)
			.variables(Map.of("question", question, "answer", answer))
			.build();
		SystemMessage systemMessage;
		if (factBased) {
			systemMessage = new SystemMessage(this.qaEvaluatorFactBasedAnswerResource);
		}
		else {
			systemMessage = new SystemMessage(this.qaEvaluatorAccurateAnswerResource);
		}
		Message userMessage = userPromptTemplate.createMessage();
		Prompt prompt = new Prompt(List.of(userMessage, systemMessage));
		String yesOrNo = this.chatModel.call(prompt).getResult().getOutput().getText();
		logger.info("Is Answer related to question: {}", yesOrNo);
		assert yesOrNo != null;
		if (yesOrNo.equalsIgnoreCase("no")) {
			SystemMessage notRelatedSystemMessage = new SystemMessage(this.qaEvaluatorNotRelatedResource);
			prompt = new Prompt(List.of(userMessage, notRelatedSystemMessage));
			String reasonForFailure = this.chatModel.call(prompt).getResult().getOutput().getText();
			fail(reasonForFailure);
		}
		else {
			logger.info("Answer is related to question.");
			assertThat(yesOrNo).isEqualTo("YES");
		}
	}

}
