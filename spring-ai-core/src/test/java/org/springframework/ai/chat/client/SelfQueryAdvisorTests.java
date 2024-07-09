package org.springframework.ai.chat.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.advisor.SelfQueryAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SelfQueryAdvisorTests {
	@Mock
	ChatModel chatModel;

	@Captor
	ArgumentCaptor<SearchRequest> vectorSearchCaptor;

	@Captor
	ArgumentCaptor<Prompt> promptCaptor;

	@Mock
	VectorStore vectorStore;

	@Test
	public void selfQueryAdvisorWithExtractedQuery() {
		String extractedJsonQuery = "{\"query\": \"joyful\", \"filter\": \"name == 'Joe' AND age == 30\"}";
		when(vectorStore.similaritySearch(vectorSearchCaptor.capture()))
				.thenReturn(List.of(new Document("doc1"), new Document("doc2")));
		when(chatModel.call(promptCaptor.capture()))
				.thenReturn(new ChatResponse(List.of(new Generation(extractedJsonQuery))));

		var selfQueryAdvisor = new SelfQueryAdvisor(
				List.of(
						new SelfQueryAdvisor.AttributeInfo("name", "string", "description1"),
						new SelfQueryAdvisor.AttributeInfo("age", "integer", "description2")),
				vectorStore,
				SearchRequest.query("Look for a user named Joe aged 30").withTopK(11),
				chatModel);
		var chatClient = ChatClient.builder(chatModel)
				.defaultSystem("You are a helpful assistant")
				.build();

		chatClient.prompt()
				.advisors(selfQueryAdvisor)
				.user("Look for a user named Joe aged 30")
				.call().chatResponse();

		FilterExpressionBuilder b = new FilterExpressionBuilder();
		assertThat(vectorSearchCaptor.getValue().getFilterExpression()).isEqualTo(b.and(b.eq("name", "Joe"), b.eq("age", 30)).build());
		assertThat(vectorSearchCaptor.getValue().getQuery()).isEqualTo("joyful");
	}
}
