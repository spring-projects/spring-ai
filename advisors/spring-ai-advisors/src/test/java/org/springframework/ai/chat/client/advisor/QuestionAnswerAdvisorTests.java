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

package org.springframework.ai.chat.client.advisor;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * @author Christian Tzolov
 * @author Timo Salm
 * @author Alexandros Pappas
 */
@ExtendWith(MockitoExtension.class)
public class QuestionAnswerAdvisorTests {

	@Mock
	ChatModel chatModel;

	@Captor
	ArgumentCaptor<Prompt> promptCaptor;

	@Captor
	ArgumentCaptor<SearchRequest> vectorSearchCaptor;

	@Mock
	VectorStore vectorStore;

	@Test
	public void qaAdvisorWithDynamicFilterExpressions() {

		// @formatter:off
		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Your answer is ZXY"))),
				ChatResponseMetadata.builder().id("678").model("model1").keyValue("key6", "value6").metadata(Map.of("key1", "value1")).promptMetadata(null).rateLimit(new RateLimit() {

						@Override
						public Long getRequestsLimit() {
							return 5L;
						}

						@Override
						public Long getRequestsRemaining() {
							return 6L;
						}

						@Override
						public Duration getRequestsReset() {
							return Duration.ofSeconds(7);
						}

						@Override
						public Long getTokensLimit() {
							return 8L;
						}

						@Override
						public Long getTokensRemaining() {
							return 8L;
						}

						@Override
						public Duration getTokensReset() {
							return Duration.ofSeconds(9);
						}
					}).usage(new DefaultUsage(6, 7))
					.build()));
		// @formatter:on

		given(this.vectorStore.similaritySearch(this.vectorSearchCaptor.capture()))
			.willReturn(List.of(new Document("doc1"), new Document("doc2")));

		var qaAdvisor = new QuestionAnswerAdvisor(this.vectorStore,
				SearchRequest.builder().similarityThreshold(0.99d).topK(6).build());

		var chatClient = ChatClient.builder(this.chatModel)
			.defaultSystem("Default system text.")
			.defaultAdvisors(qaAdvisor)
			.build();

		// @formatter:off
		var response = chatClient.prompt()
			.user("Please answer my question XYZ")
			.advisors(a -> a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, "type == 'Spring'"))
			.call()
			.chatResponse();
		//formatter:on

		// Ensure the metadata is correctly copied over
		Assertions.assertThat(response.getMetadata().getModel()).isEqualTo("model1");
		Assertions.assertThat(response.getMetadata().getId()).isEqualTo("678");
		Assertions.assertThat(response.getMetadata().getRateLimit().getRequestsLimit()).isEqualTo(5L);
		Assertions.assertThat(response.getMetadata().getRateLimit().getRequestsRemaining()).isEqualTo(6L);
		Assertions.assertThat(response.getMetadata().getRateLimit().getRequestsReset()).isEqualTo(Duration.ofSeconds(7));
		Assertions.assertThat(response.getMetadata().getRateLimit().getTokensLimit()).isEqualTo(8L);
		Assertions.assertThat(response.getMetadata().getRateLimit().getTokensRemaining()).isEqualTo(8L);
		Assertions.assertThat(response.getMetadata().getRateLimit().getTokensReset()).isEqualTo(Duration.ofSeconds(9));
		Assertions.assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(6L);
		Assertions.assertThat(response.getMetadata().getUsage().getCompletionTokens()).isEqualTo(7L);
		Assertions.assertThat(response.getMetadata().getUsage().getTotalTokens()).isEqualTo(6L + 7L);
		Assertions.assertThat(response.getMetadata().get("key6").toString()).isEqualTo("value6");
		Assertions.assertThat(response.getMetadata().get("key1").toString()).isEqualTo("value1");

		String content = response.getResult().getOutput().getText();

		assertThat(content).isEqualTo("Your answer is ZXY");

		Message systemMessage = this.promptCaptor.getValue().getInstructions().get(0);

		System.out.println(systemMessage.getText());

		assertThat(systemMessage.getText()).isEqualToIgnoringWhitespace("""
				Default system text.
				""");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);

		Message userMessage = this.promptCaptor.getValue().getInstructions().get(1);

		assertThat(userMessage.getText()).isEqualToIgnoringWhitespace("""
			Please answer my question XYZ
			Context information is below, surrounded by ---------------------

			---------------------
			doc1
			doc2
			---------------------

			Given the context and provided history information and not prior knowledge,
			reply to the user comment. If the answer is not in the context, inform
			the user that you can't answer the question.
			""");

		Assertions.assertThat(this.vectorSearchCaptor.getValue().getFilterExpression()).isEqualTo(new FilterExpressionBuilder().eq("type", "Spring").build());
		Assertions.assertThat(this.vectorSearchCaptor.getValue().getSimilarityThreshold()).isEqualTo(0.99d);
		Assertions.assertThat(this.vectorSearchCaptor.getValue().getTopK()).isEqualTo(6);
	}

	@Test
	public void qaAdvisorTakesUserTextParametersIntoAccountForSimilaritySearch() {
		given(this.chatModel.call(this.promptCaptor.capture()))
				.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Your answer is ZXY"))),
						ChatResponseMetadata.builder().build()));

		given(this.vectorStore.similaritySearch(this.vectorSearchCaptor.capture()))
				.willReturn(List.of(new Document("doc1"), new Document("doc2")));

		var chatClient = ChatClient.builder(this.chatModel).build();
		var qaAdvisor = new QuestionAnswerAdvisor(this.vectorStore, SearchRequest.builder().build());

		var userTextTemplate = "Please answer my question {question}";
		// @formatter:off
		chatClient.prompt()
				.user(u -> u.text(userTextTemplate).param("question", "XYZ"))
				.advisors(qaAdvisor)
				.call()
				.chatResponse();
		//formatter:on

		var expectedQuery = "Please answer my question XYZ";
		var userPrompt = this.promptCaptor.getValue().getInstructions().get(0).getText();
		assertThat(userPrompt).doesNotContain(userTextTemplate);
		assertThat(userPrompt).contains(expectedQuery);
		Assertions.assertThat(this.vectorSearchCaptor.getValue().getQuery()).isEqualTo(expectedQuery);
	}

	@Test
	public void qaAdvisorTakesUserParameterizedUserMessagesIntoAccountForSimilaritySearch() {
		given(this.chatModel.call(this.promptCaptor.capture()))
				.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Your answer is ZXY"))),
						ChatResponseMetadata.builder().build()));

		given(this.vectorStore.similaritySearch(this.vectorSearchCaptor.capture()))
				.willReturn(List.of(new Document("doc1"), new Document("doc2")));

		var chatClient = ChatClient.builder(this.chatModel).build();
		var qaAdvisor = new QuestionAnswerAdvisor(this.vectorStore, SearchRequest.builder().build());

		var userTextTemplate = "Please answer my question {question}";
		var userPromptTemplate = new PromptTemplate(userTextTemplate, Map.of("question", "XYZ"));
		var userMessage = userPromptTemplate.createMessage();
		// @formatter:off
		chatClient.prompt(new Prompt(userMessage))
				.advisors(qaAdvisor)
				.call()
				.chatResponse();
		//formatter:on

		var expectedQuery = "Please answer my question XYZ";
		var userPrompt = this.promptCaptor.getValue().getInstructions().get(0).getText();
		assertThat(userPrompt).doesNotContain(userTextTemplate);
		assertThat(userPrompt).contains(expectedQuery);
		Assertions.assertThat(this.vectorSearchCaptor.getValue().getQuery()).isEqualTo(expectedQuery);
	}

}
