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

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.ai.chat.client.advisor.SelfQueryAdvisor.QueryFilter.NO_FILTER;

@ExtendWith(MockitoExtension.class)
public class SelfQueryAdvisorTests {

	public static final List<SelfQueryAdvisor.AttributeInfo> METADATA_FIELD_INFO = List.of(
			new SelfQueryAdvisor.AttributeInfo("name", "string", "description1"),
			new SelfQueryAdvisor.AttributeInfo("age", "integer", "description2"));

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
		String query = "joyful";
		String name = "Joe";
		int age = 30;
		String extractedJsonQuery = String.format("""
				{"query": "%s", "filter": "name == '%s' AND age == %d"}
				""", query, name, age);
		List<Document> docsResult = List.of(new Document("doc1"), new Document("doc2"));
		when(vectorStore.similaritySearch(vectorSearchCaptor.capture())).thenReturn(docsResult);
		when(chatModel.call(promptCaptor.capture()))
			.thenReturn(new ChatResponse(List.of(new Generation(extractedJsonQuery))));

		var selfQueryAdvisor = new SelfQueryAdvisor(METADATA_FIELD_INFO, vectorStore, SearchRequest.defaults(),
				chatModel);
		var chatClient = ChatClient.builder(chatModel).defaultSystem("You are a helpful assistant").build();

		chatClient.prompt().advisors(selfQueryAdvisor).user("Look for a user named Joe aged 30").call().chatResponse();

		FilterExpressionBuilder b = new FilterExpressionBuilder();
		assertThat(vectorSearchCaptor.getValue().getFilterExpression())
			.isEqualTo(b.and(b.eq("name", name), b.eq("age", age)).build());
		assertThat(vectorSearchCaptor.getValue().getQuery()).isEqualTo(query);
	}

	@Test
	public void selfQueryAdvisorWithExtractedQueryAndNoFilter() {
		String query = "any query";
		String extractedJsonQuery = String.format("""
				{"query": "%s", "filter": "%s"}
				""", query, NO_FILTER);
		when(vectorStore.similaritySearch(vectorSearchCaptor.capture())).thenReturn(Collections.emptyList());
		when(chatModel.call(promptCaptor.capture()))
			.thenReturn(new ChatResponse(List.of(new Generation(extractedJsonQuery))));

		var selfQueryAdvisor = new SelfQueryAdvisor(METADATA_FIELD_INFO, vectorStore, SearchRequest.defaults(),
				chatModel);
		var chatClient = ChatClient.builder(chatModel).defaultSystem("You are a helpful assistant").build();

		chatClient.prompt().advisors(selfQueryAdvisor).user("Look for a user named Joe aged 30").call().chatResponse();

		assertThat(vectorSearchCaptor.getValue().getFilterExpression()).isNull();
		assertThat(vectorSearchCaptor.getValue().getQuery()).isEqualTo(query);
	}

	@Test
	public void selfQueryWithInvalidQueryExtracted() {
		String extractedJsonQuery = "not a JSON String";
		when(chatModel.call(promptCaptor.capture()))
			.thenReturn(new ChatResponse(List.of(new Generation(extractedJsonQuery))));

		var selfQueryAdvisor = new SelfQueryAdvisor(METADATA_FIELD_INFO, vectorStore, SearchRequest.defaults(),
				chatModel);
		var chatClient = ChatClient.builder(chatModel).defaultSystem("You are a helpful assistant").build();

		String userQuery = "Look for a user named Joe aged 30";
		chatClient.prompt().advisors(selfQueryAdvisor).user(userQuery).call().chatResponse();

		// assert that vectorSearchCaptor was not called
		assertThat(vectorSearchCaptor.getAllValues()).isEqualTo(Collections.emptyList());
	}

}
