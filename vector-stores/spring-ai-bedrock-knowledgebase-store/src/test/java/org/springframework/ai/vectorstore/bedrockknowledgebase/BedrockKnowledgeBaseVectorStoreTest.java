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

package org.springframework.ai.vectorstore.bedrockknowledgebase;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.AccessDeniedException;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalResult;
import software.amazon.awssdk.services.bedrockagentruntime.model.ResourceNotFoundException;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultConfluenceLocation;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultContent;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultLocation;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultLocationType;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultS3Location;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultSalesforceLocation;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultSharePointLocation;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultWebLocation;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveResponse;
import software.amazon.awssdk.services.bedrockagentruntime.model.SearchType;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BedrockKnowledgeBaseVectorStore}.
 *
 * @author Yuriy Bezsonov
 */
@ExtendWith(MockitoExtension.class)
class BedrockKnowledgeBaseVectorStoreTest {

	private static final String TEST_KB_ID = "kb-test-12345";

	@Mock
	private BedrockAgentRuntimeClient mockClient;

	private BedrockKnowledgeBaseVectorStore vectorStore;

	@BeforeEach
	void setUp() {
		this.vectorStore = BedrockKnowledgeBaseVectorStore.builder(this.mockClient, TEST_KB_ID)
			.topK(5)
			.similarityThreshold(0.7)
			.build();
	}

	private static RetrieveResponse createRetrieveResponse(KnowledgeBaseRetrievalResult... results) {
		return RetrieveResponse.builder().retrievalResults(List.of(results)).build();
	}

	private static KnowledgeBaseRetrievalResult createResult(String text, double score, String s3Uri) {
		var builder = KnowledgeBaseRetrievalResult.builder()
			.content(RetrievalResultContent.builder().text(text).build())
			.score(score);

		if (s3Uri != null) {
			builder.location(RetrievalResultLocation.builder()
				.type(RetrievalResultLocationType.S3)
				.s3Location(RetrievalResultS3Location.builder().uri(s3Uri).build())
				.build());
		}

		return builder.build();
	}

	@Nested
	@DisplayName("Builder Tests")
	class BuilderTests {

		@Test
		void shouldCreateVectorStoreWithRequiredParameters() {
			BedrockKnowledgeBaseVectorStore store = BedrockKnowledgeBaseVectorStore
				.builder(BedrockKnowledgeBaseVectorStoreTest.this.mockClient, TEST_KB_ID)
				.build();

			assertThat(store.getKnowledgeBaseId()).isEqualTo(TEST_KB_ID);
			assertThat(store.getName()).isEqualTo("BedrockKnowledgeBaseVectorStore");
		}

		@Test
		void shouldRejectNullClient() {
			assertThatThrownBy(() -> BedrockKnowledgeBaseVectorStore.builder(null, TEST_KB_ID))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must not be null");
		}

		@Test
		void shouldRejectEmptyKnowledgeBaseId() {
			assertThatThrownBy(() -> BedrockKnowledgeBaseVectorStore
				.builder(BedrockKnowledgeBaseVectorStoreTest.this.mockClient, ""))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must not be empty");
		}

		@Test
		void shouldRejectInvalidTopK() {
			assertThatThrownBy(() -> BedrockKnowledgeBaseVectorStore
				.builder(BedrockKnowledgeBaseVectorStoreTest.this.mockClient, TEST_KB_ID)
				.topK(0)
				.build()).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("topK must be positive");
		}

		@Test
		void shouldRejectInvalidSimilarityThreshold() {
			assertThatThrownBy(() -> BedrockKnowledgeBaseVectorStore
				.builder(BedrockKnowledgeBaseVectorStoreTest.this.mockClient, TEST_KB_ID)
				.similarityThreshold(1.5)
				.build()).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("similarityThreshold must be between");
		}

		@Test
		void shouldRejectNegativeSimilarityThreshold() {
			assertThatThrownBy(() -> BedrockKnowledgeBaseVectorStore
				.builder(BedrockKnowledgeBaseVectorStoreTest.this.mockClient, TEST_KB_ID)
				.similarityThreshold(-0.1)
				.build()).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("similarityThreshold must be between");
		}

		@Test
		void shouldProvideNativeClient() {
			assertThat(BedrockKnowledgeBaseVectorStoreTest.this.vectorStore.getNativeClient()).isPresent();
			assertThat(BedrockKnowledgeBaseVectorStoreTest.this.vectorStore.getNativeClient().get())
				.isSameAs(BedrockKnowledgeBaseVectorStoreTest.this.mockClient);
		}

	}

	@Nested
	@DisplayName("Similarity Search Tests")
	class SimilaritySearchTests {

		@Test
		void shouldReturnDocumentsFromKnowledgeBase() {
			// Given
			RetrieveResponse response = createRetrieveResponse(
					createResult("Travel policy content", 0.89, "s3://docs/travel.pdf"),
					createResult("Expense guidelines", 0.75, "s3://docs/expense.pdf"));
			when(BedrockKnowledgeBaseVectorStoreTest.this.mockClient.retrieve(any(RetrieveRequest.class)))
				.thenReturn(response);

			// When
			List<Document> results = BedrockKnowledgeBaseVectorStoreTest.this.vectorStore
				.similaritySearch(SearchRequest.builder().query("What is the travel policy?").build());

			// Then
			assertThat(results).hasSize(2);
			assertThat(results.get(0).getText()).isEqualTo("Travel policy content");
			assertThat(results.get(0).getScore()).isEqualTo(0.89);
			assertThat(results.get(1).getText()).isEqualTo("Expense guidelines");
		}

		@Test
		void shouldFilterResultsByThreshold() {
			// Given - one result above threshold (0.7), one below
			RetrieveResponse response = createRetrieveResponse(createResult("High relevance", 0.85, null),
					createResult("Low relevance", 0.5, null));
			when(BedrockKnowledgeBaseVectorStoreTest.this.mockClient.retrieve(any(RetrieveRequest.class)))
				.thenReturn(response);

			// When - use default threshold (0.7) from vectorStore builder
			List<Document> results = BedrockKnowledgeBaseVectorStoreTest.this.vectorStore
				.similaritySearch(SearchRequest.builder().query("test query").similarityThreshold(0.7).build());

			// Then
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getText()).isEqualTo("High relevance");
		}

		@Test
		void shouldHandleEmptyResults() {
			// Given
			when(BedrockKnowledgeBaseVectorStoreTest.this.mockClient.retrieve(any(RetrieveRequest.class)))
				.thenReturn(RetrieveResponse.builder().retrievalResults(List.of()).build());

			// When
			List<Document> results = BedrockKnowledgeBaseVectorStoreTest.this.vectorStore
				.similaritySearch(SearchRequest.builder().query("nonexistent query").build());

			// Then
			assertThat(results).isEmpty();
		}

		@Test
		void shouldPassCorrectParametersToBedrockApi() {
			// Given
			when(BedrockKnowledgeBaseVectorStoreTest.this.mockClient.retrieve(any(RetrieveRequest.class)))
				.thenReturn(RetrieveResponse.builder().retrievalResults(List.of()).build());

			// When
			BedrockKnowledgeBaseVectorStoreTest.this.vectorStore
				.similaritySearch(SearchRequest.builder().query("test query").topK(10).build());

			// Then
			ArgumentCaptor<RetrieveRequest> captor = ArgumentCaptor.forClass(RetrieveRequest.class);
			verify(BedrockKnowledgeBaseVectorStoreTest.this.mockClient).retrieve(captor.capture());

			RetrieveRequest captured = captor.getValue();
			assertThat(captured.knowledgeBaseId()).isEqualTo(TEST_KB_ID);
			assertThat(captured.retrievalQuery().text()).isEqualTo("test query");
			assertThat(captured.retrievalConfiguration().vectorSearchConfiguration().numberOfResults()).isEqualTo(10);
		}

		@Test
		void shouldRejectNullSearchRequest() {
			assertThatThrownBy(
					() -> BedrockKnowledgeBaseVectorStoreTest.this.vectorStore.similaritySearch((SearchRequest) null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must not be null");
		}

		@Test
		void shouldRejectEmptyQuery() {
			assertThatThrownBy(() -> BedrockKnowledgeBaseVectorStoreTest.this.vectorStore
				.similaritySearch(SearchRequest.builder().query("").build()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must not be empty");
		}

		@Test
		void shouldPassFilterExpressionToBedrockApi() {
			// Given
			when(BedrockKnowledgeBaseVectorStoreTest.this.mockClient.retrieve(any(RetrieveRequest.class)))
				.thenReturn(RetrieveResponse.builder().retrievalResults(List.of()).build());

			// When
			BedrockKnowledgeBaseVectorStoreTest.this.vectorStore.similaritySearch(
					SearchRequest.builder().query("test query").topK(5).filterExpression("department == 'HR'").build());

			// Then
			ArgumentCaptor<RetrieveRequest> captor = ArgumentCaptor.forClass(RetrieveRequest.class);
			verify(BedrockKnowledgeBaseVectorStoreTest.this.mockClient).retrieve(captor.capture());

			RetrieveRequest captured = captor.getValue();
			assertThat(captured.retrievalConfiguration().vectorSearchConfiguration().filter()).isNotNull();
			assertThat(captured.retrievalConfiguration().vectorSearchConfiguration().filter().equalsValue())
				.isNotNull();
			assertThat(captured.retrievalConfiguration().vectorSearchConfiguration().filter().equalsValue().key())
				.isEqualTo("department");
		}

	}

	@Nested
	@DisplayName("Document Conversion Tests")
	class DocumentConversionTests {

		@Test
		void shouldMapScoreAndDistance() {
			// Given
			KnowledgeBaseRetrievalResult result = createResult("Content", 0.85, null);

			// When
			Document doc = BedrockKnowledgeBaseVectorStoreTest.this.vectorStore.toDocument(result);

			// Then
			assertThat(doc.getScore()).isEqualTo(0.85);
			assertThat((double) doc.getMetadata().get(DocumentMetadata.DISTANCE.value())).isCloseTo(0.15,
					within(0.0001));
		}

		@Test
		void shouldMapS3SourceLocation() {
			// Given
			KnowledgeBaseRetrievalResult result = createResult("Content", 0.8, "s3://my-bucket/docs/policy.pdf");

			// When
			Document doc = BedrockKnowledgeBaseVectorStoreTest.this.vectorStore.toDocument(result);

			// Then
			assertThat(doc.getMetadata().get("source")).isEqualTo("s3://my-bucket/docs/policy.pdf");
			assertThat(doc.getMetadata().get("locationType")).isEqualTo("S3");
		}

		@Test
		void shouldHandleNullContent() {
			// Given
			KnowledgeBaseRetrievalResult result = KnowledgeBaseRetrievalResult.builder().score(0.8).build();

			// When
			Document doc = BedrockKnowledgeBaseVectorStoreTest.this.vectorStore.toDocument(result);

			// Then
			assertThat(doc.getText()).isEmpty();
		}

		@Test
		void shouldGenerateUniqueDocumentIds() {
			// Given
			KnowledgeBaseRetrievalResult result = createResult("Content", 0.8, null);

			// When
			Document doc1 = BedrockKnowledgeBaseVectorStoreTest.this.vectorStore.toDocument(result);
			Document doc2 = BedrockKnowledgeBaseVectorStoreTest.this.vectorStore.toDocument(result);

			// Then
			assertThat(doc1.getId()).isNotEqualTo(doc2.getId());
		}

	}

	@Nested
	@DisplayName("Unsupported Operations Tests")
	class UnsupportedOperationsTests {

		@Test
		void addShouldThrowUnsupportedOperationException() {
			assertThatThrownBy(() -> BedrockKnowledgeBaseVectorStoreTest.this.vectorStore.add(List.of()))
				.isInstanceOf(UnsupportedOperationException.class)
				.hasMessageContaining("data source sync");
		}

		@Test
		void deleteByIdsShouldThrowUnsupportedOperationException() {
			assertThatThrownBy(() -> BedrockKnowledgeBaseVectorStoreTest.this.vectorStore.delete(List.of("id1", "id2")))
				.isInstanceOf(UnsupportedOperationException.class)
				.hasMessageContaining("data source");
		}

		@Test
		void deleteByFilterShouldThrowUnsupportedOperationException() {
			Filter.Expression filter = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("key"),
					new Filter.Value("value"));
			assertThatThrownBy(() -> BedrockKnowledgeBaseVectorStoreTest.this.vectorStore.delete(filter))
				.isInstanceOf(UnsupportedOperationException.class)
				.hasMessageContaining("data source");
		}

	}

	@Nested
	@DisplayName("AWS Exception Handling Tests")
	class AwsExceptionHandlingTests {

		@Test
		void shouldPropagateResourceNotFoundException() {
			// Given
			when(BedrockKnowledgeBaseVectorStoreTest.this.mockClient.retrieve(any(RetrieveRequest.class)))
				.thenThrow(ResourceNotFoundException.builder().message("Knowledge base not found").build());

			// When/Then
			assertThatThrownBy(() -> BedrockKnowledgeBaseVectorStoreTest.this.vectorStore
				.similaritySearch(SearchRequest.builder().query("test").build()))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("Knowledge base not found");
		}

		@Test
		void shouldPropagateAccessDeniedException() {
			// Given
			when(BedrockKnowledgeBaseVectorStoreTest.this.mockClient.retrieve(any(RetrieveRequest.class)))
				.thenThrow(AccessDeniedException.builder().message("Access denied").build());

			// When/Then
			assertThatThrownBy(() -> BedrockKnowledgeBaseVectorStoreTest.this.vectorStore
				.similaritySearch(SearchRequest.builder().query("test").build()))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("Access denied");
		}

	}

	@Nested
	@DisplayName("Pagination Tests")
	class PaginationTests {

		@Test
		void shouldHandlePaginatedResults() {
			// Given - two pages of results
			RetrieveResponse page1 = RetrieveResponse.builder()
				.retrievalResults(List.of(createResult("Result 1", 0.9, null), createResult("Result 2", 0.85, null)))
				.nextToken("token-page-2")
				.build();

			RetrieveResponse page2 = RetrieveResponse.builder()
				.retrievalResults(List.of(createResult("Result 3", 0.8, null)))
				.nextToken(null)
				.build();

			when(BedrockKnowledgeBaseVectorStoreTest.this.mockClient.retrieve(any(RetrieveRequest.class)))
				.thenReturn(page1)
				.thenReturn(page2);

			// When - request more than first page has
			List<Document> results = BedrockKnowledgeBaseVectorStoreTest.this.vectorStore
				.similaritySearch(SearchRequest.builder().query("test").topK(5).similarityThreshold(0.0).build());

			// Then - should get results from both pages
			assertThat(results).hasSize(3);
			verify(BedrockKnowledgeBaseVectorStoreTest.this.mockClient, times(2)).retrieve(any(RetrieveRequest.class));
		}

		@Test
		void shouldStopPaginationWhenTopKReached() {
			// Given - first page has enough results
			RetrieveResponse page1 = RetrieveResponse.builder()
				.retrievalResults(List.of(createResult("Result 1", 0.9, null), createResult("Result 2", 0.85, null),
						createResult("Result 3", 0.8, null)))
				.nextToken("token-page-2")
				.build();

			when(BedrockKnowledgeBaseVectorStoreTest.this.mockClient.retrieve(any(RetrieveRequest.class)))
				.thenReturn(page1);

			// When - request only 2 results
			List<Document> results = BedrockKnowledgeBaseVectorStoreTest.this.vectorStore
				.similaritySearch(SearchRequest.builder().query("test").topK(2).similarityThreshold(0.0).build());

			// Then - should trim to topK
			assertThat(results).hasSize(2);
			verify(BedrockKnowledgeBaseVectorStoreTest.this.mockClient, times(1)).retrieve(any(RetrieveRequest.class));
		}

	}

	@Nested
	@DisplayName("Location Type Tests")
	class LocationTypeTests {

		@Test
		void shouldExtractConfluenceLocation() {
			// Given
			KnowledgeBaseRetrievalResult result = KnowledgeBaseRetrievalResult.builder()
				.content(RetrievalResultContent.builder().text("Confluence content").build())
				.score(0.8)
				.location(RetrievalResultLocation.builder()
					.type(RetrievalResultLocationType.CONFLUENCE)
					.confluenceLocation(RetrievalResultConfluenceLocation.builder()
						.url("https://company.atlassian.net/wiki/spaces/DOC/pages/123")
						.build())
					.build())
				.build();

			// When
			Document doc = BedrockKnowledgeBaseVectorStoreTest.this.vectorStore.toDocument(result);

			// Then
			assertThat(doc.getMetadata().get("source"))
				.isEqualTo("https://company.atlassian.net/wiki/spaces/DOC/pages/123");
			assertThat(doc.getMetadata().get("locationType")).isEqualTo("CONFLUENCE");
		}

		@Test
		void shouldExtractSharePointLocation() {
			// Given
			KnowledgeBaseRetrievalResult result = KnowledgeBaseRetrievalResult.builder()
				.content(RetrievalResultContent.builder().text("SharePoint content").build())
				.score(0.8)
				.location(RetrievalResultLocation.builder()
					.type(RetrievalResultLocationType.SHAREPOINT)
					.sharePointLocation(RetrievalResultSharePointLocation.builder()
						.url("https://company.sharepoint.com/sites/docs/policy.docx")
						.build())
					.build())
				.build();

			// When
			Document doc = BedrockKnowledgeBaseVectorStoreTest.this.vectorStore.toDocument(result);

			// Then
			assertThat(doc.getMetadata().get("source"))
				.isEqualTo("https://company.sharepoint.com/sites/docs/policy.docx");
			assertThat(doc.getMetadata().get("locationType")).isEqualTo("SHAREPOINT");
		}

		@Test
		void shouldExtractSalesforceLocation() {
			// Given
			KnowledgeBaseRetrievalResult result = KnowledgeBaseRetrievalResult.builder()
				.content(RetrievalResultContent.builder().text("Salesforce content").build())
				.score(0.8)
				.location(RetrievalResultLocation.builder()
					.type(RetrievalResultLocationType.SALESFORCE)
					.salesforceLocation(RetrievalResultSalesforceLocation.builder()
						.url("https://company.salesforce.com/article/123")
						.build())
					.build())
				.build();

			// When
			Document doc = BedrockKnowledgeBaseVectorStoreTest.this.vectorStore.toDocument(result);

			// Then
			assertThat(doc.getMetadata().get("source")).isEqualTo("https://company.salesforce.com/article/123");
			assertThat(doc.getMetadata().get("locationType")).isEqualTo("SALESFORCE");
		}

		@Test
		void shouldExtractWebLocation() {
			// Given
			KnowledgeBaseRetrievalResult result = KnowledgeBaseRetrievalResult.builder()
				.content(RetrievalResultContent.builder().text("Web content").build())
				.score(0.8)
				.location(RetrievalResultLocation.builder()
					.type(RetrievalResultLocationType.WEB)
					.webLocation(RetrievalResultWebLocation.builder().url("https://docs.example.com/guide").build())
					.build())
				.build();

			// When
			Document doc = BedrockKnowledgeBaseVectorStoreTest.this.vectorStore.toDocument(result);

			// Then
			assertThat(doc.getMetadata().get("source")).isEqualTo("https://docs.example.com/guide");
			assertThat(doc.getMetadata().get("locationType")).isEqualTo("WEB");
		}

	}

	@Nested
	@DisplayName("Search Type Tests")
	class SearchTypeTests {

		@Test
		void shouldPassHybridSearchTypeToApi() {
			// Given
			BedrockKnowledgeBaseVectorStore storeWithHybrid = BedrockKnowledgeBaseVectorStore
				.builder(BedrockKnowledgeBaseVectorStoreTest.this.mockClient, TEST_KB_ID)
				.searchType(SearchType.HYBRID)
				.build();

			when(BedrockKnowledgeBaseVectorStoreTest.this.mockClient.retrieve(any(RetrieveRequest.class)))
				.thenReturn(RetrieveResponse.builder().retrievalResults(List.of()).build());

			// When
			storeWithHybrid.similaritySearch(SearchRequest.builder().query("test").build());

			// Then
			ArgumentCaptor<RetrieveRequest> captor = ArgumentCaptor.forClass(RetrieveRequest.class);
			verify(BedrockKnowledgeBaseVectorStoreTest.this.mockClient).retrieve(captor.capture());

			assertThat(captor.getValue().retrievalConfiguration().vectorSearchConfiguration().overrideSearchType())
				.isEqualTo(SearchType.HYBRID);
		}

		@Test
		void shouldPassSemanticSearchTypeToApi() {
			// Given
			BedrockKnowledgeBaseVectorStore storeWithSemantic = BedrockKnowledgeBaseVectorStore
				.builder(BedrockKnowledgeBaseVectorStoreTest.this.mockClient, TEST_KB_ID)
				.searchType(SearchType.SEMANTIC)
				.build();

			when(BedrockKnowledgeBaseVectorStoreTest.this.mockClient.retrieve(any(RetrieveRequest.class)))
				.thenReturn(RetrieveResponse.builder().retrievalResults(List.of()).build());

			// When
			storeWithSemantic.similaritySearch(SearchRequest.builder().query("test").build());

			// Then
			ArgumentCaptor<RetrieveRequest> captor = ArgumentCaptor.forClass(RetrieveRequest.class);
			verify(BedrockKnowledgeBaseVectorStoreTest.this.mockClient).retrieve(captor.capture());

			assertThat(captor.getValue().retrievalConfiguration().vectorSearchConfiguration().overrideSearchType())
				.isEqualTo(SearchType.SEMANTIC);
		}

	}

}
