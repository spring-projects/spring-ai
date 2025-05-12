/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.integration.tests.client.advisor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.integration.tests.TestApplication;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link QuestionAnswerAdvisor}.
 *
 * @author Thomas Vitale
 */
@SpringBootTest(classes = TestApplication.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
public class QuestionAnswerAdvisorIT {

	private List<Document> knowledgeBaseDocuments;

	@Autowired
	OpenAiChatModel openAiChatModel;

	@Autowired
	PgVectorStore pgVectorStore;

	@Value("${classpath:documents/knowledge-base.md}")
	Resource knowledgeBaseResource;

	@BeforeEach
	void setUp() {
		DocumentReader markdownReader = new MarkdownDocumentReader(this.knowledgeBaseResource,
				MarkdownDocumentReaderConfig.defaultConfig());
		this.knowledgeBaseDocuments = markdownReader.read();
		this.pgVectorStore.add(this.knowledgeBaseDocuments);
	}

	@AfterEach
	void tearDown() {
		this.pgVectorStore.delete(this.knowledgeBaseDocuments.stream().map(Document::getId).toList());
	}

	@Test
	void qaBasic() {
		String question = "Where does the adventure of Anacletus and Birba take place?";

		QuestionAnswerAdvisor qaAdvisor = QuestionAnswerAdvisor.builder(this.pgVectorStore).build();

		ChatResponse chatResponse = ChatClient.builder(this.openAiChatModel)
			.build()
			.prompt(question)
			.advisors(qaAdvisor)
			.call()
			.chatResponse();

		assertThat(chatResponse).isNotNull();

		String response = chatResponse.getResult().getOutput().getText();
		System.out.println(response);
		assertThat(response).containsIgnoringCase("Highlands");

		evaluateRelevancy(question, chatResponse);
	}

	@Test
	void qaCustomTemplateRenderer() {
		QuestionAnswerAdvisor qaAdvisor = QuestionAnswerAdvisor.builder(this.pgVectorStore).build();

		ChatResponse chatResponse = ChatClient.builder(this.openAiChatModel)
			.build()
			.prompt()
			.user(user -> user.text("Where does the adventure of <character1> and <character2> take place?")
				.param("character1", "Anacletus")
				.param("character2", "Birba"))
			.advisors(qaAdvisor)
			.templateRenderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
			.call()
			.chatResponse();

		assertThat(chatResponse).isNotNull();

		String response = chatResponse.getResult().getOutput().getText();
		System.out.println(response);
		assertThat(response).containsIgnoringCase("Highlands");

		evaluateRelevancy("Where does the adventure of Anacletus and Birba take place?", chatResponse);
	}

	@Test
	void qaCustomPromptTemplate() {
		PromptTemplate customPromptTemplate = PromptTemplate.builder()
			.renderer(StTemplateRenderer.builder().startDelimiterToken('$').endDelimiterToken('$').build())
			.template("""
					$query$

					Context information is below, surrounded by ---------------------

					---------------------
					$question_answer_context$
					---------------------

					Given the context and provided history information and not prior knowledge,
					reply to the user comment. If the answer is not in the context, inform
					the user that you can't answer the question.
					""")
			.build();

		String question = "Where does the adventure of Anacletus and Birba take place?";

		QuestionAnswerAdvisor qaAdvisor = QuestionAnswerAdvisor.builder(this.pgVectorStore)
			.promptTemplate(customPromptTemplate)
			.build();

		ChatResponse chatResponse = ChatClient.builder(this.openAiChatModel)
			.build()
			.prompt(question)
			.advisors(qaAdvisor)
			.call()
			.chatResponse();

		assertThat(chatResponse).isNotNull();

		String response = chatResponse.getResult().getOutput().getText();
		System.out.println(response);
		assertThat(response).containsIgnoringCase("Highlands");

		evaluateRelevancy(question, chatResponse);
	}

	@Test
	void qaOutputConverter() {
		String question = "Where does the adventure of Anacletus and Birba take place?";

		QuestionAnswerAdvisor qaAdvisor = QuestionAnswerAdvisor.builder(this.pgVectorStore).build();

		Answer answer = ChatClient.builder(this.openAiChatModel)
			.build()
			.prompt(question)
			.advisors(qaAdvisor)
			.call()
			.entity(Answer.class);

		assertThat(answer).isNotNull();

		System.out.println(answer);
		assertThat(answer.content()).containsIgnoringCase("Highlands");
	}

	private record Answer(String content) {
	}

	private void evaluateRelevancy(String question, ChatResponse chatResponse) {
		EvaluationRequest evaluationRequest = new EvaluationRequest(question,
				chatResponse.getMetadata().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS),
				chatResponse.getResult().getOutput().getText());
		RelevancyEvaluator evaluator = new RelevancyEvaluator(ChatClient.builder(this.openAiChatModel));
		EvaluationResponse evaluationResponse = evaluator.evaluate(evaluationRequest);
		assertThat(evaluationResponse.isPass()).isTrue();
	}

}
