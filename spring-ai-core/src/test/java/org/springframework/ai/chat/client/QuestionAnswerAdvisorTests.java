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

package org.springframework.ai.chat.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/**
 * @author Christian Tzolov
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

		when(chatModel.call(promptCaptor.capture()))
			.thenReturn(new ChatResponse(List.of(new Generation("Your answer is ZXY"))));

		when(vectorStore.similaritySearch(vectorSearchCaptor.capture()))
			.thenReturn(List.of(new Document("doc1"), new Document("doc2")));

		var qaAdvisor = new QuestionAnswerAdvisor(vectorStore,
				SearchRequest.defaults().withSimilarityThreshold(0.99d).withTopK(6));

		var chatClient = ChatClient.builder(chatModel)
			.defaultSystem("Default system text.")
			.defaultAdvisors(qaAdvisor)
			.build();

		// @formatter:off
		var content = chatClient.prompt()
			.user("Please answer my question XYZ")
			.advisors(a -> a.param(QuestionAnswerAdvisor.FILTER_EXRESSION, "type == 'Spring'"))
			.call()
			.content();
		//formatter:on

		assertThat(content).isEqualTo("Your answer is ZXY");

		Message systemMessage = promptCaptor.getValue().getInstructions().get(0);

		System.out.println(systemMessage.getContent());

		assertThat(systemMessage.getContent()).isEqualToIgnoringWhitespace("""
				Default system text.
				""");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);

		Message userMessage = promptCaptor.getValue().getInstructions().get(1);

		assertThat(userMessage.getContent()).isEqualToIgnoringWhitespace("""
			Please answer my question XYZ
			Context information is below.
			---------------------
			doc1
			doc2
			---------------------
			Given the context and provided history information and not prior knowledge,
			reply to the user comment. If the answer is not in the context, inform
			the user that you can't answer the question.
			""");

		assertThat(vectorSearchCaptor.getValue().getFilterExpression()).isEqualTo(new FilterExpressionBuilder().eq("type", "Spring").build());
		assertThat(vectorSearchCaptor.getValue().getSimilarityThreshold()).isEqualTo(0.99d);
		assertThat(vectorSearchCaptor.getValue().getTopK()).isEqualTo(6);
	}
}
